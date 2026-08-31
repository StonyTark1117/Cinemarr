package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/** Timeline-gated positional PCM fed through Minecraft's existing OpenAL context. */
final class LegacyVideoAudio {
    private static final long START_BUFFER_US = 700_000L;
    private static final long SCHEDULE_LEAD_US = 2_000_000L;
    private static final long SCHEDULE_QUANTUM_US = 50_000L;
    private static final long SOURCE_PREROLL_US = 2_000_000L;
    private static final long SOURCE_START_CONFIRM_US = 250_000L;
    private static final long SOURCE_START_TIMEOUT_US = 5_000_000L;
    private static final long REBUFFER_DRIFT_US = 150_000L;
    private static final int REBUFFER_DRIFT_TICKS = 5;
    // Paulscode's raw-stream adapter can auto-restart an OpenAL source when a
    // late feed arrives after the last queued buffer was consumed. Keep a
    // materially larger runway than the modern streaming-source adapter so
    // that old command-thread jitter cannot create an unreported phase jump.
    private static final long TARGET_QUEUE_US = 4_000_000L;
    // Paulscode serializes every raw buffer through its shared command thread.
    // One-second buffers avoid flooding that legacy queue with several commands
    // per render tick while the four-second runway still bounds recovery time.
    private static final int PCM_FEED_BYTES = 192 * 1024;
    private static final int MAX_PENDING_FRAMES = 256;
    private static long ids;
    private final String source = "cinemarr:video:" + (++ids);
    private final Queue<LegacyDecodedAudioFrame> pending = new ArrayDeque<LegacyDecodedAudioFrame>();
    private UUID sessionId;
    private long generation = -1;
    private SoundSystem soundSystem;
    private AudioFormat format;
    private long queuedUntilLocalUs;
    private long preparedDurationUs;
    private long queuedProgramUntilUs;
    private long scheduledStartUs;
    private long programOffsetUs;
    private long mediaBoundaryLocalUs;
    private long sourceRequestedAtUs;
    private long sourcePausedAtUs;
    private int underruns;
    private int driftTicks;
    private boolean prepared;
    private boolean started;
    private boolean sourcePaused;
    private int stableTicks;
    private long lastAcceptanceLogMs;

    void tick(LegacyVideoPlayback playback, VideoPackets.SessionState session, List<VideoPackets.SessionState> televisions) {
        if (session == null || session.item() == null || session.status() == VideoPackets.SessionStatus.IDLE || televisions.isEmpty()) { reset(); return; }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            reset(); sessionId = session.sessionId(); generation = session.generation();
        }
        long targetUs = LegacyVideoPlayback.authoritativePositionMs(session,
                LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis())) * 1_000L;
        LegacyDecodedAudioFrame frame;
        // A follower can finish decoding its first few segments before its
        // server clock is ready. Do not let those now-stale frames permanently
        // fill the bounded queue and prevent later, schedulable audio from
        // flowing through once clock synchronization completes.
        while (!pending.isEmpty() && endUs(pending.peek()) < targetUs - 100_000L) pending.poll();
        while (pending.size() < MAX_PENDING_FRAMES && (frame = playback.pollAudio()) != null) {
            if (endUs(frame) >= targetUs - 100_000L) pending.add(frame);
        }
        SoundSystem current = LegacySoundAccess.soundSystem(Minecraft.getMinecraft());
        if (prepared && current != soundSystem) stopSource();
        if (!prepared && !session.paused() && LegacyClientState.INSTANCE.mediaClockReady()) prepare(current, targetUs);
        if (!prepared || soundSystem == null) return;
        float[] origin = nearest(televisions);
        soundSystem.setPosition(source, origin[0], origin[1], origin[2]);
        soundSystem.setVolume(source, CinemarrSettings.enabled() ? (float) (CinemarrSettings.volume()
                * Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.RECORDS)) : 0.0F);
        if (session.paused()) {
            if (!started) { stopSource(); return; }
            if (!sourcePaused) {
                soundSystem.pause(source); sourcePaused = true;
                sourcePausedAtUs = System.currentTimeMillis() * 1_000L;
            }
        }
        else if (!started) {
            long nowUs = System.currentTimeMillis() * 1_000L;
            if (!soundSystem.playing(source)) {
                if (nowUs - sourceRequestedAtUs > SOURCE_START_TIMEOUT_US) stopSource();
                return;
            }
            // OpenAL can report PLAYING before the selected physical backend
            // begins consuming samples. Confirm cursor movement inside the
            // silent preroll so that device startup latency is not baked into
            // this client's permanent media offset.
            if (backendPlayedUs() < SOURCE_START_CONFIRM_US) return;
            scheduleAfterSourceStart(targetUs, nowUs);
        } else {
            long nowUs = System.currentTimeMillis() * 1_000L;
            if (sourcePaused) {
                soundSystem.play(source); sourcePaused = false;
                long pausedUs = Math.max(0L, nowUs - sourcePausedAtUs);
                queuedUntilLocalUs += pausedUs;
                mediaBoundaryLocalUs += pausedUs;
                sourcePausedAtUs = 0L;
            }
            if (!soundSystem.playing(source)) {
                rebuffer(targetUs, scheduledStartUs, "source-stopped");
                return;
            }
            feed(nowUs);
            long audioMediaUs = wallClockAudioMediaUs(scheduledStartUs, mediaBoundaryLocalUs, nowUs);
            long driftUs = audioMediaUs - targetUs;
            if (targetUs >= scheduledStartUs) {
                if (Math.abs(driftUs) > REBUFFER_DRIFT_US) {
                    stableTicks = 0;
                    if (++driftTicks >= REBUFFER_DRIFT_TICKS) {
                        rebuffer(targetUs, audioMediaUs, "timeline-drift");
                        return;
                    }
                } else {
                    driftTicks = 0;
                    stableTicks++;
                }
            }
            if (pending.isEmpty() && nowUs > queuedUntilLocalUs + 250_000L && !soundSystem.playing(source)) { underruns++; stopSource(); }
        }
        if (ProtocolLimits.videoProbeEnabled() && System.currentTimeMillis() - lastAcceptanceLogMs >= 1_000L) {
            lastAcceptanceLogMs = System.currentTimeMillis();
            long nowUs = System.currentTimeMillis() * 1_000L;
            long backendPlayedUs = backendPlayedUs();
            long backendMediaUs = started ? backendAudioMediaUs(scheduledStartUs, programOffsetUs, backendPlayedUs) : 0L;
            long audioMediaUs = started ? wallClockAudioMediaUs(scheduledStartUs, mediaBoundaryLocalUs,
                    sourcePaused ? sourcePausedAtUs : nowUs) : 0L;
            Cinemarr.LOGGER.info("Acceptance legacy video audio timeline: targetMs={} videoMs={} scheduledMs={} backendPlayedMs={} backendMediaMs={} audioMediaMs={} driftMs={} started={} stableTicks={} pendingFrames={} underruns={}",
                    targetUs / 1_000L, playback.lastPresentedUs() / 1_000L, scheduledStartUs / 1_000L,
                    backendPlayedUs / 1_000L, backendMediaUs / 1_000L, audioMediaUs / 1_000L,
                    (audioMediaUs - targetUs) / 1_000L, started, stableTicks, pending.size(), underruns);
        }
    }

    private void prepare(SoundSystem system, long targetUs) {
        if (system == null || pending.isEmpty()) return;
        LegacyDecodedAudioFrame first = pending.peek(), last = first; for (LegacyDecodedAudioFrame value : pending) last = value;
        if (endUs(last) - (targetUs + SCHEDULE_LEAD_US) < START_BUFFER_US
                || first.presentationTimeUs() > targetUs + SCHEDULE_LEAD_US + 100_000L) return;
        format = new AudioFormat(first.sampleRate(), 16, first.channels(), true, false); soundSystem = system;
        float[] origin = new float[] { 0, 0, 0 };
        soundSystem.rawDataStream(format, true, source, origin[0], origin[1], origin[2], SoundSystemConfig.ATTENUATION_LINEAR, 64.0F);
        soundSystem.setLooping(source, false);
        // Start the physical source with silence only. Paulscode submits raw
        // buffers through a separate command thread, and OpenAL may report the
        // source as PLAYING before the selected backend consumes its first
        // sample. Deferring all program data until cursor movement is observed
        // lets the later scheduling pass compensate for that real startup time
        // without needing to remove an already-queued program buffer.
        preparedDurationUs = queueSilence(SOURCE_PREROLL_US);
        sourceRequestedAtUs = System.currentTimeMillis() * 1_000L;
        prepared = true; soundSystem.play(source);
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance legacy video audio preroll: targetMs={} queuedMs={} sampleRate={} channels={} pendingFrames={}",
                targetUs / 1_000L, preparedDurationUs / 1_000L, (int) format.getSampleRate(),
                format.getChannels(), pending.size());
    }

    private void scheduleAfterSourceStart(long targetUs, long nowUs) {
        long playedUs = Math.max(0L, (long) (soundSystem.millisecondsPlayed(source) * 1_000.0F));
        long boundaryUs = scheduledStartUs(targetUs);
        while (!pending.isEmpty() && endUs(pending.peek()) <= boundaryUs) pending.poll();
        if (pending.isEmpty()) { stopSource(); return; }
        LegacyDecodedAudioFrame first = pending.peek(), last = first;
        for (LegacyDecodedAudioFrame value : pending) last = value;
        scheduledStartUs = Math.max(boundaryUs, first.presentationTimeUs());
        if (endUs(last) - scheduledStartUs < START_BUFFER_US) { stopSource(); return; }
        queuedProgramUntilUs = scheduledStartUs;
        long additionalSilenceUs = additionalSilenceUs(targetUs, scheduledStartUs, preparedDurationUs, playedUs);
        preparedDurationUs += queueSilence(additionalSilenceUs);
        programOffsetUs = preparedDurationUs;
        // Queue the program only after the physical cursor is confirmed. This
        // preserves the corrected silence in front of every decoded frame.
        long preparedContentUs = 0L;
        while (preparedContentUs < TARGET_QUEUE_US && !pending.isEmpty()) {
            long queuedUs = queueBatch();
            if (queuedUs < 0L) { stopSource(); return; }
            if (queuedUs == 0L) break;
            preparedDurationUs += queuedUs; preparedContentUs += queuedUs;
        }
        mediaBoundaryLocalUs = nowUs + Math.max(0L, programOffsetUs - playedUs);
        queuedUntilLocalUs = nowUs + Math.max(0L, preparedDurationUs - playedUs);
        started = true; driftTicks = 0; stableTicks = targetUs >= scheduledStartUs ? 1 : 0;
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance legacy video audio scheduled: framePtsMs={} targetMs={} leadMs={} backendPlayedMs={} extraSilenceMs={} queuedMs={}",
                scheduledStartUs / 1_000L, targetUs / 1_000L, Math.max(0L, scheduledStartUs - targetUs) / 1_000L,
                playedUs / 1_000L, additionalSilenceUs / 1_000L, preparedDurationUs / 1_000L);
    }

    private long backendPlayedUs() {
        return soundSystem == null ? 0L
                : Math.max(0L, (long) (soundSystem.millisecondsPlayed(source) * 1_000.0F));
    }

    private void rebuffer(long targetUs, long audioMediaUs, String reason) {
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance legacy video audio rebuffer: reason={} driftMs={} targetMs={} audioMs={}",
                reason, (audioMediaUs - targetUs) / 1_000L, targetUs / 1_000L, audioMediaUs / 1_000L);
        stopSource();
    }

    private void feed(long nowUs) {
        if (soundSystem == null || format == null) return;
        queuedUntilLocalUs = Math.max(nowUs, queuedUntilLocalUs);
        while (queuedUntilLocalUs - nowUs < TARGET_QUEUE_US && !pending.isEmpty()) {
            long queuedUs = queueBatch();
            if (queuedUs < 0L) { underruns++; stopSource(); return; }
            if (queuedUs == 0L) break;
            queuedUntilLocalUs += queuedUs;
        }
    }

    /** Coalesce tiny AAC decoder frames and preserve their absolute PTS across HLS boundaries. */
    private long queueBatch() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(PCM_FEED_BYTES);
        long durationUs = 0L;
        while (bytes.size() < PCM_FEED_BYTES && !pending.isEmpty()) {
            LegacyDecodedAudioFrame frame = pending.poll();
            if (frame.sampleRate() != (int) format.getSampleRate() || frame.channels() != format.getChannels()) return -1L;
            byte[] pcm = frame.pcmView();
            long frameDurationUs = durationUs(pcm.length, frame.sampleRate(), frame.channels());
            long frameEndUs = frame.presentationTimeUs() + frameDurationUs;
            if (frameEndUs <= queuedProgramUntilUs) continue;

            long gapUs = frame.presentationTimeUs() - queuedProgramUntilUs;
            if (gapUs > 0L) {
                int silenceBytes = alignedBytes(gapUs, frame.sampleRate(), frame.channels());
                if (silenceBytes > 0) {
                    bytes.write(new byte[silenceBytes], 0, silenceBytes);
                    durationUs += durationUs(silenceBytes, frame.sampleRate(), frame.channels());
                }
            }

            int offset = 0;
            if (gapUs < 0L) offset = Math.min(pcm.length,
                    alignedBytes(-gapUs, frame.sampleRate(), frame.channels()));
            if (offset < pcm.length) {
                bytes.write(pcm, offset, pcm.length - offset);
                durationUs += durationUs(pcm.length - offset, frame.sampleRate(), frame.channels());
            }
            queuedProgramUntilUs = Math.max(queuedProgramUntilUs, frameEndUs);
        }
        if (bytes.size() == 0) return 0L;
        soundSystem.feedRawAudioData(source, bytes.toByteArray());
        return durationUs;
    }

    private long queueSilence(long durationUs) {
        if (durationUs <= 0L) return 0L;
        int remaining = alignedBytes(durationUs, (int) format.getSampleRate(), format.getChannels());
        long queuedUs = 0L;
        while (remaining > 0) {
            int length = Math.min(PCM_FEED_BYTES, remaining);
            int alignment = format.getChannels() * 2;
            length -= length % alignment;
            if (length == 0) break;
            soundSystem.feedRawAudioData(source, new byte[length]);
            queuedUs += durationUs(length, (int) format.getSampleRate(), format.getChannels());
            remaining -= length;
        }
        return queuedUs;
    }

    private static int alignedBytes(long durationUs, int sampleRate, int channels) {
        long sampleFrames = (durationUs * sampleRate + 999_999L) / 1_000_000L;
        return (int) Math.min(Integer.MAX_VALUE, sampleFrames * channels * 2L);
    }
    private static long durationUs(int bytes, int sampleRate, int channels) {
        return bytes * 1_000_000L / (2L * channels * sampleRate);
    }
    static long additionalSilenceUs(long targetUs, long scheduledStartUs, long queuedPrerollUs, long playedUs) {
        long remainingPrerollUs = Math.max(0L, queuedPrerollUs - Math.max(0L, playedUs));
        return Math.max(0L, scheduledStartUs - targetUs - remainingPrerollUs);
    }
    static long backendAudioMediaUs(long scheduledStartUs, long programOffsetUs, long backendPlayedUs) {
        return scheduledStartUs + Math.max(0L, backendPlayedUs - programOffsetUs);
    }
    static long wallClockAudioMediaUs(long scheduledStartUs, long mediaBoundaryLocalUs, long nowUs) {
        return scheduledStartUs + Math.max(0L, nowUs - mediaBoundaryLocalUs);
    }
    static long scheduledStartUs(long targetUs) {
        return roundUp(targetUs + SCHEDULE_LEAD_US, SCHEDULE_QUANTUM_US);
    }
    private static long roundUp(long value, long quantum) { return ((value + quantum - 1L) / quantum) * quantum; }
    private static long endUs(LegacyDecodedAudioFrame frame) {
        return frame.presentationTimeUs() + durationUs(frame.pcmView().length, frame.sampleRate(), frame.channels());
    }
    private static float[] nearest(List<VideoPackets.SessionState> televisions) {
        net.minecraft.entity.Entity listener = Minecraft.getMinecraft().renderViewEntity;
        double x = listener == null ? 0 : listener.posX, y = listener == null ? 0 : listener.posY, z = listener == null ? 0 : listener.posZ;
        float[] best = null; double bestDistance = Double.MAX_VALUE;
        for (VideoPackets.SessionState television : televisions) {
            float[] point = nearestScreenPoint(television, x, y, z); double dx = point[0] - x, dy = point[1] - y, dz = point[2] - z;
            double distance = dx * dx + dy * dy + dz * dz; if (distance < bestDistance) { bestDistance = distance; best = point; }
        }
        return best == null ? new float[] { 0, 0, 0 } : best;
    }
    static float[] nearestScreenPoint(VideoPackets.SessionState state, double listenerX, double listenerY, double listenerZ) {
        double u = state.screenFacing() == ScreenFacing.EAST || state.screenFacing() == ScreenFacing.WEST ? listenerZ : listenerX;
        double v = state.screenFacing() == ScreenFacing.UP || state.screenFacing() == ScreenFacing.DOWN ? listenerZ : listenerY;
        u = Math.max(state.minimumU(), Math.min(state.minimumU() + state.screenWidth(), u));
        v = Math.max(state.minimumV(), Math.min(state.minimumV() + state.screenHeight(), v));
        switch (state.screenFacing()) {
            case NORTH: return new float[] { (float) u, (float) v, state.screenPlane() };
            case SOUTH: return new float[] { (float) u, (float) v, state.screenPlane() + 1 };
            case WEST: return new float[] { state.screenPlane(), (float) v, (float) u };
            case EAST: return new float[] { state.screenPlane() + 1, (float) v, (float) u };
            case DOWN: return new float[] { (float) u, state.screenPlane(), (float) v };
            default: return new float[] { (float) u, state.screenPlane() + 1, (float) v };
        }
    }
    int underruns() { return underruns; }
    boolean ready() { return started && soundSystem != null && stableTicks >= 10; }
    void audioEngineReloaded() { stopSource(); }
    void reset() { stopSource(); pending.clear(); sessionId = null; generation = -1; underruns = 0;
        driftTicks = stableTicks = 0; lastAcceptanceLogMs = 0L; }
    private void stopSource() {
        SoundSystem previous = soundSystem; soundSystem = null; format = null; prepared = false; started = false;
        sourcePaused = false; queuedUntilLocalUs = preparedDurationUs = queuedProgramUntilUs = scheduledStartUs = programOffsetUs = mediaBoundaryLocalUs = sourceRequestedAtUs = sourcePausedAtUs = 0L;
        driftTicks = stableTicks = 0;
        if (previous != null) {
            try { previous.stop(source); previous.flush(source); previous.removeSource(source); }
            catch (RuntimeException ignored) { }
        }
    }
}
