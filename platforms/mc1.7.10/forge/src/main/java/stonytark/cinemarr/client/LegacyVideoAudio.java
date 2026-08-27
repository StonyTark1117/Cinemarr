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
    private static final long SCHEDULE_QUANTUM_US = 1_000_000L;
    private static final long TARGET_QUEUE_US = 1_000_000L;
    private static final int PCM_FEED_BYTES = 32 * 1024;
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
    private int underruns;
    private boolean prepared;
    private boolean started;
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
        while (pending.size() < MAX_PENDING_FRAMES && (frame = playback.pollAudio()) != null) {
            if (endUs(frame) >= targetUs - 100_000L) pending.add(frame);
        }
        SoundSystem current = LegacySoundAccess.soundSystem(Minecraft.getMinecraft());
        if (prepared && current != soundSystem) stopSource();
        if (!prepared && LegacyClientState.INSTANCE.mediaClockReady()) prepare(current, targetUs);
        if (!prepared || soundSystem == null) return;
        float[] origin = nearest(televisions);
        soundSystem.setPosition(source, origin[0], origin[1], origin[2]);
        soundSystem.setVolume(source, CinemarrSettings.enabled() ? (float) (CinemarrSettings.volume()
                * Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.RECORDS)) : 0.0F);
        if (session.paused()) { if (started) soundSystem.pause(source); }
        else if (!started) {
            long latenessUs = targetUs - scheduledStartUs;
            if (latenessUs > 100_000L) { stopSource(); return; }
            long nowUs = System.currentTimeMillis() * 1_000L;
            queuedUntilLocalUs = nowUs + preparedDurationUs;
            started = true; stableTicks = latenessUs >= 0L ? 1 : 0; soundSystem.play(source);
            Cinemarr.LOGGER.info("Acceptance legacy video audio scheduled: framePtsMs={} targetMs={} leadMs={} queuedMs={}",
                    scheduledStartUs / 1_000L, targetUs / 1_000L, Math.max(0L, -latenessUs) / 1_000L,
                    preparedDurationUs / 1_000L);
        } else {
            long nowUs = System.currentTimeMillis() * 1_000L;
            feed(nowUs); if (targetUs >= scheduledStartUs) stableTicks++;
            if (pending.isEmpty() && nowUs > queuedUntilLocalUs + 250_000L && !soundSystem.playing(source)) { underruns++; stopSource(); }
        }
        if (ProtocolLimits.videoProbeEnabled() && System.currentTimeMillis() - lastAcceptanceLogMs >= 1_000L) {
            lastAcceptanceLogMs = System.currentTimeMillis();
            Cinemarr.LOGGER.info("Acceptance legacy video audio timeline: targetMs={} scheduledMs={} started={} stableTicks={} pendingFrames={} underruns={}",
                    targetUs / 1_000L, scheduledStartUs / 1_000L, started, stableTicks, pending.size(), underruns);
        }
    }

    private void prepare(SoundSystem system, long targetUs) {
        long boundaryUs = roundUp(targetUs + SCHEDULE_LEAD_US, SCHEDULE_QUANTUM_US);
        while (!pending.isEmpty() && endUs(pending.peek()) <= boundaryUs) pending.poll();
        if (system == null || pending.isEmpty()) return;
        LegacyDecodedAudioFrame first = pending.peek(), last = first; for (LegacyDecodedAudioFrame value : pending) last = value;
        if (endUs(last) - Math.max(boundaryUs, first.presentationTimeUs()) < START_BUFFER_US
                || first.presentationTimeUs() > boundaryUs + 100_000L) return;
        format = new AudioFormat(first.sampleRate(), 16, first.channels(), true, false); soundSystem = system;
        float[] origin = new float[] { 0, 0, 0 };
        soundSystem.rawDataStream(format, true, source, origin[0], origin[1], origin[2], SoundSystemConfig.ATTENUATION_LINEAR, 64.0F);
        soundSystem.setLooping(source, false); scheduledStartUs = first.presentationTimeUs();
        queuedProgramUntilUs = scheduledStartUs; preparedDurationUs = queueSilence(scheduledStartUs - targetUs);
        long preparedContentUs = 0L;
        while (preparedContentUs < TARGET_QUEUE_US && !pending.isEmpty()) {
            long queuedUs = queueBatch();
            if (queuedUs < 0L) { stopSource(); return; }
            if (queuedUs == 0L) break;
            preparedDurationUs += queuedUs; preparedContentUs += queuedUs;
        }
        prepared = true;
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance legacy video audio prepared: framePtsMs={} durationMs={} sampleRate={} channels={} pendingFrames={}",
                scheduledStartUs / 1_000L, preparedDurationUs / 1_000L, (int) format.getSampleRate(),
                format.getChannels(), pending.size());
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
        stableTicks = 0; lastAcceptanceLogMs = 0L; }
    private void stopSource() {
        SoundSystem previous = soundSystem; soundSystem = null; format = null; prepared = false; started = false;
        queuedUntilLocalUs = preparedDurationUs = queuedProgramUntilUs = scheduledStartUs = 0L; stableTicks = 0;
        if (previous != null) {
            try { previous.stop(source); previous.flush(source); previous.removeSource(source); }
            catch (RuntimeException ignored) { }
        }
    }
}
