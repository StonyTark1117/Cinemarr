package stonytark.cinemarr.client;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTSourceLatency;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.AudioUnderrunPolicy;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.mixin.client.ChannelAccessor;
import stonytark.cinemarr.mixin.client.SoundEngineAccessor;
import stonytark.cinemarr.mixin.client.SoundManagerAccessor;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;

/** Timeline-gated positional TV audio. Audio starts only after a future buffer exists. */
public final class CinemarrVideoAudio {
    private static final long START_BUFFER_US = 3_000_000;
    private static final long SCHEDULE_LEAD_US = 1_500_000;
    private static final long SCHEDULE_QUANTUM_US = 250_000;
    private static final long MIN_SOURCE_START_LEAD_US = 1_000_000;
    private static final int STREAM_BUFFER_MS = 250;
    private static final int INITIAL_STREAM_BUFFERS = 12;
    private static final int SOURCE_PREROLL_BUFFERS = 4;
    private static final long SOURCE_PREROLL_US = SOURCE_PREROLL_BUFFERS * STREAM_BUFFER_MS * 1_000L;
    private static final int MAX_PENDING_FRAMES = 256;
    // The acceptance contract permits 150 ms between two listeners. Keep each
    // endpoint to one third of that budget so opposite-signed clock error and
    // capture/output jitter cannot consume the complete pairwise allowance.
    private static final long REBUFFER_DRIFT_US = 50_000;
    private static final int REBUFFER_DRIFT_TICKS = 5;
    private static final int READY_STABLE_TICKS = 10;
    private final Queue<DecodedAudioFrame> pending =
            new PriorityQueue<>(Comparator.comparingLong(DecodedAudioFrame::presentationTimeUs));
    private UUID sessionId;
    private long generation = -1;
    private VideoPcmAudioStream stream;
    private ChannelAccess.ChannelHandle channel;
    private boolean channelPending;
    private volatile long channelAttempt;
    private int underruns;
    private int observedStarvations;
    private int caughtUpTicks;
    private int driftTicks;
    private int stableTicks;
    private volatile long audioTimelineUs;
    private volatile long audioTimelineNanos = Long.MIN_VALUE;
    private volatile long audioMediaStartUs;
    private volatile long physicalAudioTimelineUs;
    private volatile long physicalAudioTimelineNanos = Long.MIN_VALUE;
    private volatile boolean physicalTimelineProbeQueued;
    private long lastAcceptanceLogMs;
    private boolean acceptanceSetupStallInjected;

    public void tick(CinemarrVideoPlayback playback, VideoPackets.SessionState session) {
        if (session == null || session.item() == null || session.status() == VideoPackets.SessionStatus.IDLE) { reset(); return; }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            reset(); sessionId = session.sessionId(); generation = session.generation();
        }
        long targetUs = CinemarrVideoPlayback.authoritativePositionMsLocal(session) * 1_000L;
        boolean acceptMore = pending.size() < MAX_PENDING_FRAMES;
        if (stream != null) {
            while (!pending.isEmpty()) {
                if (endUs(pending.peek()) < targetUs - 100_000L) { pending.poll(); continue; }
                if (!stream.offer(pending.peek())) { acceptMore = false; break; }
                pending.poll();
            }
        }
        if (acceptMore) for (DecodedAudioFrame frame; (frame = playback.pollAudio()) != null;) {
            if (endUs(frame) < targetUs - 100_000L) continue;
            if (stream == null) {
                pending.add(frame);
                if (pending.size() >= MAX_PENDING_FRAMES) break;
            }
            else if (!stream.offer(frame)) { pending.add(frame); break; }
        }
        if (playback.caughtUp()) caughtUpTicks++; else caughtUpTicks = 0;
        if (stream == null && !channelPending && CinemarrClientState.INSTANCE.mediaClockReady()
                && targetUs >= 2_000_000L && caughtUpTicks >= 10) {
            prepareAndStart(session, targetUs);
        }
        if (channel != null) {
            long nowNanos = System.nanoTime();
            if (audioTimelineNanos != Long.MIN_VALUE && nowNanos >= audioTimelineNanos) {
                probePhysicalTimeline();
                if (physicalAudioTimelineNanos == Long.MIN_VALUE) {
                    audioTimelineUs = advanceTimelineUs(
                            audioTimelineUs, audioTimelineNanos, nowNanos, session.paused());
                } else {
                    audioTimelineUs = advancePhysicalTimelineUs(audioTimelineUs,
                            physicalAudioTimelineUs, physicalAudioTimelineNanos, nowNanos, session.paused());
                }
                audioTimelineNanos = nowNanos;
                long driftUs = audioTimelineUs - targetUs;
                if (!session.paused() && driftRequiresRebuffer(driftUs)) {
                    stableTicks = 0;
                    if (++driftTicks >= REBUFFER_DRIFT_TICKS) {
                        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                                "Acceptance video audio rebuffer: driftMs={} targetMs={} audioMs={}",
                                driftUs / 1_000L, targetUs / 1_000L, audioTimelineUs / 1_000L);
                        resetChannel();
                        caughtUpTicks = 0;
                    }
                } else {
                    driftTicks = 0;
                    stableTicks++;
                }
            }
        }
        if (channel != null) {
            int starvations = stream == null ? 0 : stream.starvations();
            boolean stopped = channel.isStopped() && !session.paused();
            boolean terminal = playback.audioInputExhausted() && pending.isEmpty();
            int additional = AudioUnderrunPolicy.additionalUnderruns(observedStarvations, starvations,
                    stopped, session.paused(), terminal);
            underruns += additional;
            if (additional > 0 && ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance video audio active underrun: underruns={}", underruns);
            observedStarvations = Math.max(observedStarvations, starvations);
            Vec3 origin = nearestScreenPoint(session, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            float volume = CinemarrSettings.enabled() ? (float) (CinemarrSettings.volume()
                    * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS)) : 0;
            channel.execute(value -> {
                value.setSelfPosition(origin); value.setVolume(volume); value.setRelative(false); value.linearAttenuation(64);
                if (session.paused()) value.pause(); else value.unpause();
            });
            if (stopped) {
                if (terminal && ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                        "Acceptance video audio terminal drain: targetMs={} audioMs={}",
                        targetUs / 1_000L, audioTimelineUs / 1_000L);
                resetChannel();
            }
        }
        if (ProtocolLimits.videoProbeEnabled() && System.currentTimeMillis() - lastAcceptanceLogMs >= 1_000) {
            lastAcceptanceLogMs = System.currentTimeMillis();
            long acceptanceAudioUs = audioTimelineNanos == Long.MIN_VALUE ? 0L : audioTimelineUs;
            Cinemarr.LOGGER.info("Acceptance video audio timeline: wallEpochMs={} targetMs={} videoMs={} audioMs={} physicalAudioMs={} driftMs={} javaBufferMs={} timelineGapMs={} timelineTrimmedMs={} pendingFrames={} pendingFirstMs={} decodedFrames={} starvations={} underruns={}",
                    lastAcceptanceLogMs,
                    targetUs / 1_000L, playback.lastPresentedUs() / 1_000L,
                    acceptanceAudioUs / 1_000L,
                    physicalAudioTimelineNanos == Long.MIN_VALUE ? 0L : physicalAudioTimelineUs / 1_000L,
                    (acceptanceAudioUs - targetUs) / 1_000L,
                    stream == null ? 0 : stream.bufferedMs(),
                    stream == null ? 0 : stream.timelineGapMs(),
                    stream == null ? 0 : stream.timelineTrimmedMs(), pending.size(),
                    pending.isEmpty() ? -1 : pending.peek().presentationTimeUs() / 1_000L,
                    playback.queuedAudioFrames(), stream == null ? 0 : stream.starvations(), underruns);
        }
    }

    private void prepareAndStart(VideoPackets.SessionState session, long targetUs) {
        while (!pending.isEmpty() && endUs(pending.peek()) < targetUs - 50_000L) pending.poll();
        if (pending.isEmpty()) return;
        long scheduledStartUs = scheduledStartUs(targetUs);
        StartWindow window = startWindow(scheduledStartUs);
        if (window == null || !hasStartRunway(scheduledStartUs, window.first.presentationTimeUs(),
                endUs(window.first), endUs(window.last))) return;
        UUID expectedSession = sessionId; long expectedGeneration = generation; long expectedAttempt = ++channelAttempt; channelPending = true;
        ChannelAccess access = ((SoundEngineAccessor) ((SoundManagerAccessor) (Object) Minecraft.getInstance().getSoundManager()).cinemarr$soundEngine()).cinemarr$channelAccess();
        access.createHandle(Library.Pool.STREAMING).whenComplete((handle, error) -> Minecraft.getInstance().execute(
                () -> finishStart(session, expectedSession, expectedGeneration, expectedAttempt, scheduledStartUs, handle, error)));
    }

    private void finishStart(VideoPackets.SessionState session, UUID expectedSession, long expectedGeneration,
                             long expectedAttempt, long scheduledStartUs,
                             ChannelAccess.ChannelHandle handle, Throwable error) {
        if (expectedAttempt != channelAttempt) { if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::stop); return; }
        channelPending = false;
        if (error != null || handle == null || !expectedSession.equals(sessionId) || expectedGeneration != generation) {
            if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::stop); return;
        }
        long targetUs = CinemarrVideoPlayback.authoritativePositionMsLocal(session) * 1_000L;
        StartWindow window = startWindow(scheduledStartUs);
        if (scheduledStartUs - targetUs < MIN_SOURCE_START_LEAD_US || window == null
                || !hasStartRunway(scheduledStartUs, window.first.presentationTimeUs(),
                endUs(window.first), endUs(window.last))) {
            handle.execute(com.mojang.blaze3d.audio.Channel::stop); return;
        }
        while (!pending.isEmpty() && endUs(pending.peek()) <= scheduledStartUs) pending.poll();
        DecodedAudioFrame first = pending.peek();
        VideoPcmAudioStream startingStream = new VideoPcmAudioStream(first.sampleRate(), first.channels());
        boolean firstFrame = true;
        while (!pending.isEmpty() && startingStream.offer(pending.peek(), firstFrame ? scheduledStartUs : pending.peek().presentationTimeUs())) {
            pending.poll();
            firstFrame = false;
        }
        stream = startingStream; channel = handle; observedStarvations = startingStream.starvations(); underruns += observedStarvations;
        audioTimelineUs = 0;
        audioTimelineNanos = Long.MIN_VALUE;
        audioMediaStartUs = scheduledStartUs;
        physicalAudioTimelineUs = scheduledStartUs;
        physicalAudioTimelineNanos = Long.MIN_VALUE;
        physicalTimelineProbeQueued = false;
        driftTicks = 0;
        stableTicks = 0;
        if (ProtocolLimits.videoProbeEnabled()) {
            Cinemarr.LOGGER.info("Acceptance video audio scheduled: targetMs={} mediaStartMs={} silenceMs={} bufferedMs={} timelineGapMs={} timelineTrimmedMs={}",
                    targetUs / 1_000L, scheduledStartUs / 1_000L, (scheduledStartUs - targetUs) / 1_000L,
                    startingStream.bufferedMs(), startingStream.timelineGapMs(), startingStream.timelineTrimmedMs());
        }
        Vec3 origin = nearestScreenPoint(session, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        handle.execute(value -> {
            if (expectedAttempt != channelAttempt) { value.stop(); return; }
            value.setRelative(false); value.setSelfPosition(origin); value.linearAttenuation(64); value.setVolume(0);
            ChannelAccessor accessor = (ChannelAccessor) value;
            accessor.cinemarr$stream(startingStream);
            accessor.cinemarr$streamingBufferSize(streamBufferBytes(startingStream));
            long untilMediaStartUs;
            long localBoundaryEpochMs;
            if (session.paused()) {
                long currentTargetUs = CinemarrVideoPlayback.authoritativePositionMsLocal(session) * 1_000L;
                untilMediaStartUs = Math.max(0L, scheduledStartUs - currentTargetUs);
                localBoundaryEpochMs = System.currentTimeMillis() + untilMediaStartUs / 1_000L;
            } else {
                long serverBoundaryEpochMs = session.serverEpochMs() + scheduledStartUs / 1_000L - session.positionMs();
                localBoundaryEpochMs = CinemarrClientState.INSTANCE.serverToLocalEpoch(serverBoundaryEpochMs);
                untilMediaStartUs = Math.max(0L, localBoundaryEpochMs - System.currentTimeMillis()) * 1_000L;
            }
            long mediaBoundaryNanos = System.nanoTime() + untilMediaStartUs * 1_000L;
            if (!startingStream.prependSilenceFor(SOURCE_PREROLL_US)) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            accessor.cinemarr$pumpBuffers(SOURCE_PREROLL_BUFFERS);
            value.play();
            if (!injectAcceptanceSetupStall() || !sourcePlaying(accessor.cinemarr$source())) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            SourceTiming timing = sourceTimingUs(accessor.cinemarr$source(), SOURCE_PREROLL_US);
            long remainingPrerollUs = Math.max(0L, SOURCE_PREROLL_US - timing.offsetUs());
            long remainingUntilBoundaryUs = Math.max(0L, mediaBoundaryNanos - System.nanoTime()) / 1_000L;
            if (!sourcePlaying(accessor.cinemarr$source())
                    || remainingPrerollUs + timing.outputLatencyUs() > remainingUntilBoundaryUs + 50_000L) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            long silenceUs = additionalSilenceUs(remainingUntilBoundaryUs, remainingPrerollUs, timing.outputLatencyUs());
            if (!startingStream.prependSilenceFor(silenceUs)) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            int additionalBuffers = startingStream.initialBufferCount(
                    STREAM_BUFFER_MS, INITIAL_STREAM_BUFFERS - SOURCE_PREROLL_BUFFERS);
            if (additionalBuffers < 1) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            accessor.cinemarr$pumpBuffers(additionalBuffers);
            if (!sourcePlaying(accessor.cinemarr$source())) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            audioTimelineUs = scheduledStartUs;
            audioTimelineNanos = mediaBoundaryNanos;
            if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance video audio source started: boundaryEpochMs={} sourceOffsetMs={} outputLatencyMs={} prerollRemainingMs={} boundaryLeadMs={} silenceMs={} runwayBuffers={} latencyMeasured={}",
                    localBoundaryEpochMs,
                    timing.offsetUs() / 1_000L, timing.outputLatencyUs() / 1_000L,
                    remainingPrerollUs / 1_000L, remainingUntilBoundaryUs / 1_000L,
                    silenceUs / 1_000L, SOURCE_PREROLL_BUFFERS + additionalBuffers,
                    AL.getCapabilities().AL_SOFT_source_latency);
        });
    }

    private static long endUs(DecodedAudioFrame frame) {
        long samples = frame.byteLength() / (2L * frame.channels());
        return frame.presentationTimeUs() + samples * 1_000_000L / frame.sampleRate();
    }

    private static long secondsToMicros(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0 || seconds > Long.MAX_VALUE / 1_000_000.0) return -1L;
        return Math.round(seconds * 1_000_000.0);
    }

    private static SourceTiming sourceTimingUs(int source, long maximumOffsetUs) {
        long offsetUs;
        long latencyUs = 0L;
        if (AL.getCapabilities().AL_SOFT_source_latency) {
            double[] timing = new double[2];
            SOFTSourceLatency.alGetSourcedvSOFT(source, SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT, timing);
            offsetUs = secondsToMicros(timing[0]);
            latencyUs = secondsToMicros(timing[1]);
        } else {
            // OpenAL 1.1 still supplies the streaming cursor when device
            // output latency cannot be measured through the optional extension.
            offsetUs = secondsToMicros(AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET));
        }
        if (offsetUs < 0L || offsetUs > maximumOffsetUs) offsetUs = 0L;
        if (latencyUs < 0L || latencyUs > 5_000_000L) latencyUs = 0L;
        return new SourceTiming(offsetUs, latencyUs);
    }

    private static boolean sourcePlaying(int source) {
        return source != 0 && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    private void probePhysicalTimeline() {
        if (physicalTimelineProbeQueued || channel == null || stream == null) return;
        ChannelAccess.ChannelHandle expectedChannel = channel;
        VideoPcmAudioStream expectedStream = stream;
        long expectedAttempt = channelAttempt;
        physicalTimelineProbeQueued = true;
        expectedChannel.execute(value -> {
            try {
                if (expectedAttempt != channelAttempt || expectedChannel != channel || expectedStream != stream) return;
                int source = ((ChannelAccessor) value).cinemarr$source();
                int queuedBuffers = Math.max(0, AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED));
                long bufferUs = STREAM_BUFFER_MS * 1_000L;
                SourceTiming timing = sourceTimingUs(source, Math.max(bufferUs, queuedBuffers * bufferUs));
                long estimate = physicalTimelineUs(audioMediaStartUs, expectedStream.totalReadUs(),
                        expectedStream.prependedSilenceUs(), queuedBuffers, bufferUs,
                        timing.offsetUs(), timing.outputLatencyUs());
                physicalAudioTimelineUs = Math.max(physicalAudioTimelineUs, estimate);
                physicalAudioTimelineNanos = System.nanoTime();
            } finally {
                physicalTimelineProbeQueued = false;
            }
        });
    }

    static long additionalSilenceUs(long untilMediaStartUs, long remainingPrerollUs, long outputLatencyUs) {
        return VideoPcmAudioStream.compensatingSilenceUs(
                untilMediaStartUs, remainingPrerollUs, outputLatencyUs);
    }

    static boolean driftRequiresRebuffer(long driftUs) {
        return Math.abs(driftUs) > REBUFFER_DRIFT_US;
    }

    static long advanceTimelineUs(long timelineUs, long previousNanos, long nowNanos, boolean paused) {
        if (paused || previousNanos == Long.MIN_VALUE || nowNanos <= previousNanos) return timelineUs;
        // Bridge the scheduled boundary to the first backend observation.
        // Subsequent observations account for unqueued buffers and constrain
        // wall-time interpolation through advancePhysicalTimelineUs.
        return timelineUs + (nowNanos - previousNanos) / 1_000L;
    }

    static long physicalTimelineUs(long mediaStartUs, long totalReadUs, long leadingSilenceUs,
                                   int queuedBuffers, long bufferUs, long sourceOffsetUs,
                                   long outputLatencyUs) {
        long queuedUs = Math.max(0L, queuedBuffers) * Math.max(0L, bufferUs);
        long unqueuedUs = Math.max(0L, totalReadUs - queuedUs);
        long offsetUs = Math.max(0L, Math.min(sourceOffsetUs, queuedUs));
        long heardSourceUs = Math.max(0L, unqueuedUs + offsetUs - Math.max(0L, outputLatencyUs));
        return mediaStartUs + Math.max(0L, heardSourceUs - Math.max(0L, leadingSilenceUs));
    }

    static long advancePhysicalTimelineUs(long reportedUs, long physicalUs, long physicalNanos,
                                          long nowNanos, boolean paused) {
        if (paused) return reportedUs;
        if (physicalNanos == Long.MIN_VALUE || nowNanos <= physicalNanos) {
            return Math.max(reportedUs, physicalUs);
        }
        return Math.max(reportedUs, physicalUs + (nowNanos - physicalNanos) / 1_000L);
    }

    private boolean injectAcceptanceSetupStall() {
        if (!ProtocolLimits.videoProbeEnabled() || acceptanceSetupStallInjected) return true;
        int delayMs = Integer.getInteger("cinemarr.acceptance.modernAudioSetupStallMs", 0);
        if (delayMs <= 0) return true;
        acceptanceSetupStallInjected = true;
        Cinemarr.LOGGER.info("Acceptance video audio setup stall injected: delayMs={}", delayMs);
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long roundUp(long value, long quantum) {
        return Math.floorDiv(value + quantum - 1, quantum) * quantum;
    }

    static long scheduledStartUs(long targetUs) {
        return roundUp(targetUs + SCHEDULE_LEAD_US, SCHEDULE_QUANTUM_US);
    }

    static boolean hasStartRunway(long scheduledStartUs, long firstStartUs, long firstEndUs, long lastEndUs) {
        return firstEndUs > scheduledStartUs
                && firstStartUs <= scheduledStartUs + 100_000L
                && lastEndUs - Math.max(scheduledStartUs, firstStartUs) >= START_BUFFER_US;
    }

    private StartWindow startWindow(long scheduledStartUs) {
        DecodedAudioFrame first = null, last = null;
        for (DecodedAudioFrame value : pending) {
            if (endUs(value) > scheduledStartUs && (first == null
                    || value.presentationTimeUs() < first.presentationTimeUs())) first = value;
            if (last == null || endUs(value) > endUs(last)) last = value;
        }
        return first == null ? null : new StartWindow(first, last);
    }

    private static int streamBufferBytes(VideoPcmAudioStream value) {
        int frameSize = value.getFormat().getFrameSize();
        int bytes = (int) value.getFormat().getSampleRate() * frameSize * STREAM_BUFFER_MS / 1_000;
        return Math.max(frameSize, bytes - bytes % frameSize);
    }

    static Vec3 nearestScreenPoint(VideoPackets.SessionState state, Vec3 listener) {
        double u = state.screenFacing() == ScreenFacing.EAST || state.screenFacing() == ScreenFacing.WEST ? listener.z : listener.x;
        double v = state.screenFacing() == ScreenFacing.UP || state.screenFacing() == ScreenFacing.DOWN ? listener.z : listener.y;
        u = Math.max(state.minimumU(), Math.min(state.minimumU() + state.screenWidth(), u));
        v = Math.max(state.minimumV(), Math.min(state.minimumV() + state.screenHeight(), v));
        return switch (state.screenFacing()) {
            case NORTH -> new Vec3(u,v,state.screenPlane()); case SOUTH -> new Vec3(u,v,state.screenPlane()+1);
            case WEST -> new Vec3(state.screenPlane(),v,u); case EAST -> new Vec3(state.screenPlane()+1,v,u);
            case DOWN -> new Vec3(u,state.screenPlane(),v); case UP -> new Vec3(u,state.screenPlane()+1,v);
        };
    }

    public int underruns() { return underruns; }
    public boolean ready() { return stream != null && channel != null && !channel.isStopped() && stableTicks >= READY_STABLE_TICKS; }
    public void audioEngineReloaded() { resetChannel(); }
    public void reset() { resetChannel(); pending.clear(); sessionId=null; generation=-1; underruns=0;caughtUpTicks=0;lastAcceptanceLogMs=0; }
    private void resetChannel() {
        channelAttempt++;
        if (channel != null) { channel.execute(com.mojang.blaze3d.audio.Channel::stop); channel=null; }
        if (stream != null) { stream.close(); stream=null; }
        channelPending=false;observedStarvations=0;driftTicks=0;stableTicks=0;audioTimelineUs=0;audioTimelineNanos=Long.MIN_VALUE;
        audioMediaStartUs=0;physicalAudioTimelineUs=0;physicalAudioTimelineNanos=Long.MIN_VALUE;physicalTimelineProbeQueued=false;
    }

    private record StartWindow(DecodedAudioFrame first, DecodedAudioFrame last) {}
    private record SourceTiming(long offsetUs, long outputLatencyUs) {}
}
