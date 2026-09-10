package stonytark.cinemarr.client;

import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.DecodeCompletionGuard;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.ProtocolCapabilities;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.BoundedWorkExecutor;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded Java-8 FFmpeg pipeline with generation-safe client-thread delivery. */
final class LegacyVideoPlayback implements AutoCloseable {
    private static final int MAX_DECODE_JOBS = 2;
    // One-second HLS segments need enough completed decode batches to ride out
    // the same ten-second decoder jitter covered by the transport's prefetch
    // window. The byte ceiling below remains the controlling bound for larger
    // resolutions and longer real-Plex segments.
    private static final int MAX_DECODED_VIDEO_BATCHES = 16;
    private static final long MAX_QUEUED_VIDEO_BYTES = 192L * 1024L * 1024L;
    private static final int MAX_AUDIO_FRAMES = 128;
    private final BoundedWorkExecutor executor =
            new BoundedWorkExecutor(1, MAX_DECODE_JOBS, "Cinemarr legacy FFmpeg decoder ");
    private final LegacyFfmpegVideoDecoder decoder = new LegacyFfmpegVideoDecoder(
            CinemarrSettings.videoDecoderBackend(), CinemarrSettings.videoDecoderDevice());
    private final LegacyMediaSegmentDecoder segmentDecoder;
    private final Queue<DecodedBatch> decoded = new ConcurrentLinkedQueue<DecodedBatch>();
    private final Queue<List<LegacyDecodedVideoFrame>> videoBatches = new ArrayDeque<List<LegacyDecodedVideoFrame>>();
    private final PriorityQueue<LegacyDecodedVideoFrame> video = new PriorityQueue<LegacyDecodedVideoFrame>(32,
            new Comparator<LegacyDecodedVideoFrame>() { @Override public int compare(LegacyDecodedVideoFrame left, LegacyDecodedVideoFrame right) {
                return left.presentationTimeUs() < right.presentationTimeUs() ? -1 : left.presentationTimeUs() == right.presentationTimeUs() ? 0 : 1;
            }});
    private final Queue<LegacyDecodedAudioFrame> audio = new ArrayDeque<LegacyDecodedAudioFrame>();
    private final AtomicInteger pending = new AtomicInteger();
    private final DecodeCompletionGuard completions = new DecodeCompletionGuard();
    private final AtomicBoolean decoderSelectionLogged = new AtomicBoolean();
    private final AtomicBoolean decoderFallbackLogged = new AtomicBoolean();
    private final AtomicBoolean acceptanceDecodeStallInjected = new AtomicBoolean();
    private LegacyVideoTexture texture = new LegacyVideoTexture();
    private VideoStreamIdentity identity;
    private UUID televisionId;
    private UUID sessionId;
    private String itemKey = "";
    private long generation = -1;
    private int decoderRecoveries;
    private int videoDrops;
    private long lastPresentedUs;
    private String lastFrameSha256 = "";
    private long lastHealthMs;
    private boolean caughtUp;
    private boolean audioInputExhausted;
    private long queuedVideoBytes;

    LegacyVideoPlayback() { this(null); }

    LegacyVideoPlayback(LegacyMediaSegmentDecoder segmentDecoder) {
        this.segmentDecoder = segmentDecoder == null ? decoder : segmentDecoder;
    }

    void tick(LegacyVideoClientState.StreamState stream) {
        VideoPackets.SessionState session = stream.session();
        if (session == null || session.status() == VideoPackets.SessionStatus.IDLE || session.status() == VideoPackets.SessionStatus.ERROR) { reset(); return; }
        itemKey = session.item() == null ? "" : session.item().key();
        televisionId = session.televisionId();
        if (!session.identity().equals(identity)) {
            resetQueues(); identity = session.identity(); sessionId = session.sessionId(); generation = session.generation();
        }
        VideoSegmentAssembler.CompletedSegment segment;
        while (pending.get() < MAX_DECODE_JOBS && decoded.size() < MAX_DECODE_JOBS && canBufferAnotherVideoBatch()
                && audio.size() < MAX_AUDIO_FRAMES && (segment = stream.pollSegment()) != null) submit(segment);
        DecodedBatch batch;
        while (audio.size() < MAX_AUDIO_FRAMES && canBufferAnotherVideoBatch() && (batch = decoded.poll()) != null) if (batch.identity.equals(identity)) {
            if (!batch.video.isEmpty()) {
                videoBatches.add(batch.video);
                queuedVideoBytes += videoBytes(batch.video);
            }
            audio.addAll(batch.audio);
        }
        // Preserve complete HLS segments as bounded batches. Flattening a long
        // Plex segment into a small frame-count queue evicted its early frames
        // before the render clock reached them, producing multi-second freezes
        // that short fake-Plex segments did not expose.
        if (video.isEmpty() && !videoBatches.isEmpty()) video.addAll(videoBatches.poll());
        long targetUs = authoritativePositionMs(session, LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis())) * 1_000L;
        LegacyDecodedVideoFrame current = null;
        while (!video.isEmpty() && video.peek().presentationTimeUs() <= targetUs + 40_000L) {
            current = video.poll(); queuedVideoBytes -= current.rgbaView().length;
        }
        if (current != null) {
            texture.upload(current); lastPresentedUs = current.presentationTimeUs();
            if (ProtocolLimits.videoProbeEnabled()) lastFrameSha256 = Hashing.sha256(current.rgbaView());
            if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance video frame: session={} generation={} ptsUs={} sha256={} dimensions={}x{}",
                    sessionId, generation, lastPresentedUs, lastFrameSha256, current.width(), current.height());
        }
        caughtUp = lastPresentedUs > 0L && Math.abs(lastPresentedUs - targetUs) <= 250_000L;
        audioInputExhausted = stream.inputExhausted() && pending.get() == 0 && decoded.isEmpty() && audio.isEmpty();
    }

    boolean retainPausedFrameFrom(LegacyVideoPlayback previous, VideoPackets.SessionState next) {
        if (texture.ready() || !previous.texture.ready()
                || !stonytark.cinemarr.core.client.PausedFrameRetention.permits(
                        previous.identity, previous.itemKey, next)) return false;
        // Move only the GPU texture. The old pipeline still owns its jobs and
        // audio queues and is closed normally when its last stream disappears.
        texture.close();
        texture = previous.texture;
        previous.texture = new LegacyVideoTexture();
        lastPresentedUs = previous.lastPresentedUs;
        lastFrameSha256 = previous.lastFrameSha256;
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance paused frame retained: generation={} frameSha256={} ptsUs={}",
                next.generation(), lastFrameSha256, lastPresentedUs);
        return true;
    }

    boolean retainReplacementFrameFrom(LegacyVideoPlayback previous, VideoPackets.SessionState next) {
        if (texture.ready() || !previous.texture.ready() || next == null || previous.televisionId == null
                || !previous.televisionId.equals(next.televisionId()) || previous.itemKey == null
                || next.item() == null || !previous.itemKey.equals(next.item().key()) || previous.identity == null
                || !previous.identity.timelineId().equals(next.timelineId())
                || previous.identity.timelineGeneration() != next.timelineGeneration()) return false;
        texture.close(); texture = previous.texture; previous.texture = new LegacyVideoTexture();
        lastPresentedUs = previous.lastPresentedUs; lastFrameSha256 = previous.lastFrameSha256;
        return true;
    }

    private void submit(final VideoSegmentAssembler.CompletedSegment segment) {
        if (!segment.identity().equals(identity)) return;
        final long epoch = completions.epoch();
        pending.incrementAndGet();
        executor.run(() -> {
            try {
                injectAcceptanceDecodeStall();
                LegacyDecodedMediaSegment result = segmentDecoder.decode(segment.data()); long first = earliestTimestamp(result);
                long offset = segment.presentationTimeMs() * 1_000L - first;
                List<LegacyDecodedVideoFrame> shiftedVideo = new ArrayList<LegacyDecodedVideoFrame>();
                for (LegacyDecodedVideoFrame frame : result.video()) shiftedVideo.add(new LegacyDecodedVideoFrame(
                        Math.max(0, frame.presentationTimeUs() + offset), frame.width(), frame.height(), frame.rgbaView()));
                List<LegacyDecodedAudioFrame> shiftedAudio = new ArrayList<LegacyDecodedAudioFrame>();
                for (LegacyDecodedAudioFrame frame : result.audio()) shiftedAudio.add(new LegacyDecodedAudioFrame(
                        Math.max(0, frame.presentationTimeUs() + offset), frame.sampleRate(), frame.channels(), frame.pcmView()));
                completions.publish(epoch, () -> {
                    logDecoderSelection();
                    decoded.add(new DecodedBatch(segment.identity(), shiftedVideo, shiftedAudio));
                });
            } catch (Throwable failure) {
                completions.publish(epoch, () -> {
                    decoderRecoveries++;
                    Cinemarr.LOGGER.warn("Cinemarr rejected legacy video segment {}: {}", segment.segmentIndex(), failure.toString());
                });
            }
        }).whenComplete((unused, failure) -> pending.decrementAndGet());
    }

    private void injectAcceptanceDecodeStall() throws InterruptedException {
        // Wait until the transport has had enough time to fill the expanded
        // lead window. Injecting during initial startup tests the small PCM
        // runway instead of whether completed compressed segments cover a
        // later decoder stall.
        if (!ProtocolLimits.videoProbeEnabled() || decoder.decodedSegments() < 30L
                || acceptanceDecodeStallInjected.get()) return;
        int requestedMs = Integer.getInteger("cinemarr.acceptance.legacyDecodeStallMs", 0);
        int delayMs = Math.max(0, Math.min(15_000, requestedMs));
        if (delayMs == 0 || !acceptanceDecodeStallInjected.compareAndSet(false, true)) return;
        Cinemarr.LOGGER.info("Acceptance legacy decoder stall injected: afterSegments={} delayMs={}",
                decoder.decodedSegments(), delayMs);
        Thread.sleep(delayMs);
    }

    private void logDecoderSelection() {
        if (decoder.fallbackCount() > 0L) {
            if (decoderFallbackLogged.compareAndSet(false, true)) Cinemarr.LOGGER.warn(
                    "Cinemarr legacy video decoder requested={} deviceType={} fell back permanently to software: {}",
                    decoder.requestedBackend().configValue(), decoder.deviceType(), decoder.fallbackReason());
            return;
        }
        if (decoderSelectionLogged.compareAndSet(false, true)) Cinemarr.LOGGER.info(
                "Cinemarr legacy video decoder requested={} effective={} deviceType={}",
                decoder.requestedBackend().configValue(), decoder.effectiveBackend().configValue(), decoder.deviceType());
    }

    private static long earliestTimestamp(LegacyDecodedMediaSegment result) {
        long first = Long.MAX_VALUE;
        if (!result.video().isEmpty()) first = Math.min(first, result.video().get(0).presentationTimeUs());
        if (!result.audio().isEmpty()) first = Math.min(first, result.audio().get(0).presentationTimeUs());
        return first == Long.MAX_VALUE ? 0 : first;
    }
    static long authoritativePositionMs(VideoPackets.SessionState session, long serverNow) {
        if (session.paused()) return session.positionMs();
        return Math.min(session.durationMs(), Math.max(0, session.positionMs() + Math.max(0, serverNow - session.serverEpochMs())));
    }
    LegacyVideoTexture texture() { return texture; }
    LegacyDecodedAudioFrame pollAudio() { return audio.poll(); }
    int decoderRecoveries() { return decoderRecoveries; }
    int videoDrops() { return videoDrops; }
    long lastPresentedUs() { return lastPresentedUs; }
    String lastFrameSha256() { return lastFrameSha256; }
    boolean caughtUp() { return caughtUp; }
    boolean audioInputExhausted() { return audioInputExhausted; }

    private boolean canBufferAnotherVideoBatch() {
        return allowsDecodedVideoBatch(decoded.size() + videoBatches.size(), queuedVideoBytes);
    }

    static boolean allowsDecodedVideoBatch(int retainedBatches, long retainedBytes) {
        return retainedBatches >= 0 && retainedBytes >= 0L
                && retainedBatches < MAX_DECODED_VIDEO_BATCHES
                && retainedBytes < MAX_QUEUED_VIDEO_BYTES;
    }

    private static long videoBytes(List<LegacyDecodedVideoFrame> frames) {
        long bytes = 0L;
        for (LegacyDecodedVideoFrame frame : frames) bytes += frame.rgbaView().length;
        return bytes;
    }

    void sendHealth(LegacyVideoClientState.StreamState stream, int underruns) {
        VideoPackets.SessionState session = stream.session(); long now = System.currentTimeMillis();
        if (session == null || session.item() == null || now - lastHealthMs < ProtocolCapabilities.HEALTH_INTERVAL_MS) return;
        long target = authoritativePositionMs(session, LegacyClientState.INSTANCE.serverEpoch(now)) * 1_000L;
        long newestQueued = lastPresentedUs;
        for (LegacyDecodedVideoFrame frame : video) newestQueued = Math.max(newestQueued, frame.presentationTimeUs());
        long buffered = bufferedMs(target, newestQueued);
        long drift = presentedDriftMs(target, lastPresentedUs);
        LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_CLIENT_HEALTH, new VideoPackets.ClientHealth(session.identity(), texture.ready() ? "PLAYING" : "BUFFERING", decoderRecoveries, videoDrops, underruns,
                Math.min(60_000, buffered), drift));
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance decoder metrics: requested={} effective={} deviceType={} segments={} frames={} wallNanos={} "
                        + "cpuNanos={} transferNanos={} conversionNanos={} peakRetainedBytes={} fallbackCount={} "
                        + "recoveries={} videoDrops={} audioUnderruns={} bufferedMs={} driftMs={}",
                decoder.requestedBackend().configValue(), decoder.effectiveBackend().configValue(), decoder.deviceType(),
                decoder.decodedSegments(), decoder.decodedFrames(), decoder.wallNanos(), decoder.cpuNanos(),
                decoder.transferNanos(), decoder.conversionNanos(), decoder.peakRetainedBytes(), decoder.fallbackCount(),
                decoderRecoveries, videoDrops, underruns, buffered, drift);
        lastHealthMs = now;
    }
    static long bufferedMs(long targetUs, long newestQueuedUs) {
        return Math.max(0L, (newestQueuedUs - targetUs) / 1_000L);
    }
    static long presentedDriftMs(long targetUs, long lastPresentedUs) {
        if (lastPresentedUs == 0L) return 0L;
        return Math.max(-30_000L, Math.min(30_000L, (lastPresentedUs - targetUs) / 1_000L));
    }
    void reset() { identity = null; televisionId = null; sessionId = null; itemKey = ""; generation = -1; lastPresentedUs = lastHealthMs = 0; lastFrameSha256 = ""; caughtUp = false; audioInputExhausted = false; resetQueues(); texture.close(); }
    private void resetQueues() { completions.reset(this::clearQueues); }
    private void clearQueues() { decoded.clear(); videoBatches.clear(); video.clear(); audio.clear(); queuedVideoBytes = 0L; }
    @Override public void close() {
        // Retire publication before interruption: a native return or cancelled
        // obsolete job must neither refill cleared queues nor report recovery.
        completions.close(this::clearQueues);
        reset();
        executor.close();
    }

    private static final class DecodedBatch {
        final VideoStreamIdentity identity; final List<LegacyDecodedVideoFrame> video; final List<LegacyDecodedAudioFrame> audio;
        DecodedBatch(VideoStreamIdentity identity, List<LegacyDecodedVideoFrame> video, List<LegacyDecodedAudioFrame> audio) {
            this.identity = identity; this.video = Collections.unmodifiableList(video); this.audio = Collections.unmodifiableList(audio);
        }
    }
}
