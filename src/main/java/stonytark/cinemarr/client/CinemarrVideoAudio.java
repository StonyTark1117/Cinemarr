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
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.mixin.client.ChannelAccessor;
import stonytark.cinemarr.mixin.client.SoundEngineAccessor;
import stonytark.cinemarr.mixin.client.SoundManagerAccessor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/** Timeline-gated positional TV audio. Audio starts only after a future buffer exists. */
public final class CinemarrVideoAudio {
    private static final long START_BUFFER_US = 3_000_000;
    private static final long SCHEDULE_LEAD_US = 1_500_000;
    private static final long SCHEDULE_QUANTUM_US = 250_000;
    private static final long SOURCE_PREROLL_US = 1_000_000;
    private static final int SOURCE_PREROLL_BUFFERS = 2;
    private static final int STREAM_BUFFER_MS = 250;
    private static final int INITIAL_STREAM_BUFFERS = 12;
    private static final int MAX_PENDING_FRAMES = 256;
    private static final long REBUFFER_DRIFT_US = 150_000;
    private static final int REBUFFER_DRIFT_TICKS = 5;
    private static final int READY_STABLE_TICKS = 10;
    private static final long SOURCE_START_TIMEOUT_NANOS = 5_000_000_000L;
    private final Queue<DecodedAudioFrame> pending = new ArrayDeque<>();
    private UUID sessionId;
    private long generation = -1;
    private VideoPcmAudioStream stream;
    private ChannelAccess.ChannelHandle channel;
    private boolean channelPending;
    private long channelAttempt;
    private int underruns;
    private int observedStarvations;
    private int caughtUpTicks;
    private int driftTicks;
    private int stableTicks;
    private volatile long audioTimelineUs;
    private volatile long audioTimelineNanos = Long.MIN_VALUE;
    private volatile boolean sourceStartPending;
    private volatile boolean sourceStartProbeQueued;
    private volatile long sourceStartScheduledUs;
    private volatile long sourceStartServerBoundaryEpochMs;
    private volatile long sourceStartSilenceUs;
    private volatile int sourceStartSampleRate;
    private volatile long sourceStartRequestedNanos;
    private long lastAcceptanceLogMs;

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
            if (sourceStartPending) {
                if (nowNanos - sourceStartRequestedNanos >= SOURCE_START_TIMEOUT_NANOS) {
                    if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                            "Acceptance video audio rebuffer: reason=source-start-timeout");
                    resetChannel();
                    caughtUpTicks = 0;
                } else {
                    probeSourceStart(session);
                }
            }
            if (audioTimelineNanos != Long.MIN_VALUE && nowNanos >= audioTimelineNanos) {
                if (!session.paused()) audioTimelineUs += (nowNanos - audioTimelineNanos) / 1_000L;
                audioTimelineNanos = nowNanos;
                long driftUs = audioTimelineUs - targetUs;
                if (!session.paused() && Math.abs(driftUs) > REBUFFER_DRIFT_US) {
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
            if (starvations > observedStarvations) { underruns += starvations - observedStarvations; observedStarvations = starvations; }
            Vec3 origin = nearestScreenPoint(session, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            float volume = CinemarrSettings.enabled() ? (float) (CinemarrSettings.volume()
                    * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS)) : 0;
            channel.execute(value -> {
                value.setSelfPosition(origin); value.setVolume(volume); value.setRelative(false); value.linearAttenuation(64);
                if (session.paused()) value.pause(); else value.unpause();
            });
            if (channel.isStopped() && !session.paused()) { underruns++; resetChannel(); }
        }
        if (ProtocolLimits.videoProbeEnabled() && System.currentTimeMillis() - lastAcceptanceLogMs >= 1_000) {
            lastAcceptanceLogMs = System.currentTimeMillis();
            long acceptanceAudioUs = audioTimelineNanos == Long.MIN_VALUE ? 0L : audioTimelineUs;
            Cinemarr.LOGGER.info("Acceptance video audio timeline: targetMs={} videoMs={} audioMs={} driftMs={} javaBufferMs={} pendingFrames={} pendingFirstMs={} decodedFrames={} starvations={} underruns={}",
                    targetUs / 1_000L, playback.lastPresentedUs() / 1_000L,
                    acceptanceAudioUs / 1_000L, (acceptanceAudioUs - targetUs) / 1_000L,
                    stream == null ? 0 : stream.bufferedMs(), pending.size(),
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
        if (scheduledStartUs - targetUs < SOURCE_PREROLL_US || window == null
                || !hasStartRunway(scheduledStartUs, window.first.presentationTimeUs(),
                endUs(window.first), endUs(window.last))) {
            handle.execute(com.mojang.blaze3d.audio.Channel::stop); return;
        }
        while (!pending.isEmpty() && endUs(pending.peek()) <= scheduledStartUs) pending.poll();
        DecodedAudioFrame first = pending.peek();
        VideoPcmAudioStream startingStream = new VideoPcmAudioStream(first.sampleRate(), first.channels());
        startingStream.scheduleSilenceFor(SOURCE_PREROLL_US);
        boolean firstFrame = true;
        while (!pending.isEmpty() && startingStream.offer(pending.peek(), firstFrame ? scheduledStartUs : pending.peek().presentationTimeUs())) {
            pending.poll();
            firstFrame = false;
        }
        stream = startingStream; channel = handle; observedStarvations = startingStream.starvations(); underruns += observedStarvations;
        audioTimelineUs = 0;
        audioTimelineNanos = Long.MIN_VALUE;
        driftTicks = 0;
        stableTicks = 0;
        if (ProtocolLimits.videoProbeEnabled()) {
            Cinemarr.LOGGER.info("Acceptance video audio scheduled: targetMs={} mediaStartMs={} silenceMs={} bufferedMs={}",
                    targetUs / 1_000L, scheduledStartUs / 1_000L, (scheduledStartUs - targetUs) / 1_000L,
                    startingStream.bufferedMs());
        }
        Vec3 origin = nearestScreenPoint(session, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        handle.execute(value -> {
            if (expectedAttempt != channelAttempt) { value.stop(); return; }
            value.setRelative(false); value.setSelfPosition(origin); value.linearAttenuation(64); value.setVolume(0);
            ChannelAccessor accessor = (ChannelAccessor) value;
            accessor.cinemarr$stream(startingStream);
            accessor.cinemarr$streamingBufferSize(streamBufferBytes(startingStream));
            int initialBuffers = startingStream.initialBufferCount(STREAM_BUFFER_MS, SOURCE_PREROLL_BUFFERS);
            if (initialBuffers < SOURCE_PREROLL_BUFFERS) {
                value.stop();
                Minecraft.getInstance().execute(() -> { if (expectedAttempt == channelAttempt) { resetChannel(); caughtUpTicks = 0; } });
                return;
            }
            accessor.cinemarr$pumpBuffers(SOURCE_PREROLL_BUFFERS);
            value.play();
            // Begin with a bounded silent preroll. Once the source cursor and
            // physical output latency are measurable, prepend the remaining
            // compensated silence before pumping the program-audio runway.
            sourceStartScheduledUs = scheduledStartUs;
            sourceStartServerBoundaryEpochMs = session.paused() ? 0L
                    : session.serverEpochMs() + scheduledStartUs / 1_000L - session.positionMs();
            sourceStartSilenceUs = startingStream.scheduledSilenceUs();
            sourceStartSampleRate = (int) startingStream.getFormat().getSampleRate();
            sourceStartRequestedNanos = System.nanoTime();
            sourceStartPending = true;
        });
    }

    private void probeSourceStart(VideoPackets.SessionState session) {
        if (!sourceStartPending || sourceStartProbeQueued || channel == null) return;
        ChannelAccess.ChannelHandle expectedChannel = channel;
        VideoPcmAudioStream expectedStream = stream;
        long expectedAttempt = channelAttempt;
        sourceStartProbeQueued = true;
        expectedChannel.execute(value -> {
            if (expectedAttempt != channelAttempt || expectedChannel != channel || expectedStream != stream
                    || !sourceStartPending) {
                sourceStartProbeQueued = false;
                return;
            }
            int source = ((ChannelAccessor) value).cinemarr$source();
            long playedUs;
            long outputLatencyUs = 0L;
            boolean measuredOutputLatency = AL.getCapabilities().AL_SOFT_source_latency;
            if (measuredOutputLatency) {
                double[] timing = new double[2];
                SOFTSourceLatency.alGetSourcedvSOFT(source, SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT, timing);
                playedUs = secondsToMicros(timing[0]);
                outputLatencyUs = secondsToMicros(timing[1]);
                if (playedUs < 0L || outputLatencyUs < 0L || outputLatencyUs > SOURCE_START_TIMEOUT_NANOS / 1_000L) {
                    measuredOutputLatency = false;
                }
            } else {
                playedUs = 0L;
            }
            if (!measuredOutputLatency) {
                int sampleOffset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
                playedUs = sampleOffset * 1_000_000L / Math.max(1, sourceStartSampleRate);
                outputLatencyUs = 0L;
            }
            if (playedUs > 0) {
                long remainingPrerollUs = Math.max(0L, sourceStartSilenceUs - playedUs);
                // Map the server-owned media boundary directly to local wall
                // time here so executor delay and join time cannot skew it.
                long untilMediaStartUs;
                if (sourceStartServerBoundaryEpochMs > 0L) {
                    long localBoundaryEpochMs = CinemarrClientState.INSTANCE.serverToLocalEpoch(
                            sourceStartServerBoundaryEpochMs);
                    untilMediaStartUs = Math.max(0L, localBoundaryEpochMs - System.currentTimeMillis()) * 1_000L;
                } else {
                    long targetUs = CinemarrVideoPlayback.authoritativePositionMsLocal(session) * 1_000L;
                    untilMediaStartUs = Math.max(0L, sourceStartScheduledUs - targetUs);
                }
                if (remainingPrerollUs + outputLatencyUs > untilMediaStartUs + 50_000L) {
                    sourceStartPending = false;
                    sourceStartProbeQueued = false;
                    if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                            "Acceptance video audio rebuffer: reason=source-start-missed-boundary remainingPrerollMs={} outputLatencyMs={} untilStartMs={}",
                            remainingPrerollUs / 1_000L, outputLatencyUs / 1_000L, untilMediaStartUs / 1_000L);
                    Minecraft.getInstance().execute(() -> {
                        if (expectedAttempt == channelAttempt && expectedChannel == channel) {
                            resetChannel();
                            caughtUpTicks = 0;
                        }
                    });
                    return;
                }
                long additionalSilenceUs = VideoPcmAudioStream.compensatingSilenceUs(
                        untilMediaStartUs, remainingPrerollUs, outputLatencyUs);
                if (!expectedStream.prependSilenceFor(additionalSilenceUs)) {
                    sourceStartPending = false;
                    sourceStartProbeQueued = false;
                    Minecraft.getInstance().execute(() -> {
                        if (expectedAttempt == channelAttempt && expectedChannel == channel) {
                            resetChannel();
                            caughtUpTicks = 0;
                        }
                    });
                    return;
                }
                int runwayBuffers = expectedStream.initialBufferCount(STREAM_BUFFER_MS,
                        INITIAL_STREAM_BUFFERS - SOURCE_PREROLL_BUFFERS);
                if (runwayBuffers > 0) ((ChannelAccessor) value).cinemarr$pumpBuffers(runwayBuffers);
                audioTimelineUs = sourceStartScheduledUs;
                audioTimelineNanos = System.nanoTime()
                        + VideoPcmAudioStream.physicalBoundaryDelayUs(
                                sourceStartSilenceUs + additionalSilenceUs, playedUs, outputLatencyUs) * 1_000L;
                sourceStartPending = false;
                if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                        "Acceptance video audio source started: backendPlayedMs={} outputLatencyMs={} prerollMs={} additionalSilenceMs={} runwayBuffers={} latencyMeasured={}",
                        playedUs / 1_000L, outputLatencyUs / 1_000L, sourceStartSilenceUs / 1_000L,
                        additionalSilenceUs / 1_000L, runwayBuffers, measuredOutputLatency);
            }
            sourceStartProbeQueued = false;
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
            if (first == null && endUs(value) > scheduledStartUs) first = value;
            last = value;
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
        sourceStartPending=false;sourceStartProbeQueued=false;sourceStartScheduledUs=0;sourceStartServerBoundaryEpochMs=0;sourceStartSilenceUs=0;
        sourceStartSampleRate=0;sourceStartRequestedNanos=0;
    }

    private record StartWindow(DecodedAudioFrame first, DecodedAudioFrame last) {}
}
