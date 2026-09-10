package stonytark.cinemarr.client;

import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.client.DecodeCompletionGuard;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.ProtocolCapabilities;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;
import stonytark.cinemarr.core.server.BoundedWorkExecutor;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded off-thread decode queue with generation-safe render-thread delivery. */
public final class CinemarrVideoPlayback implements AutoCloseable {
    private static final int MAX_AUDIO_DECODE_JOBS = 2;
    private static final int MAX_VIDEO_DECODE_JOBS = 1;
    private static final int MAX_DECODED_VIDEO_BATCHES = 8;
    private static final int MAX_COMPRESSED_VIDEO_SEGMENTS = 8;
    private static final long MAX_COMPRESSED_VIDEO_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_QUEUED_VIDEO_BYTES = 192L * 1024L * 1024L;
    private static final int MAX_QUEUED_AUDIO_FRAMES = 256;
    private final BoundedWorkExecutor audioDecoderExecutor =
            new BoundedWorkExecutor(1, MAX_AUDIO_DECODE_JOBS, "Cinemarr FFmpeg audio decoder ");
    private final BoundedWorkExecutor videoDecoderExecutor =
            new BoundedWorkExecutor(1, MAX_VIDEO_DECODE_JOBS, "Cinemarr FFmpeg video decoder ");
    private final FfmpegVideoDecoder decoder = new FfmpegVideoDecoder(
            CinemarrSettings.videoDecoderBackend(), CinemarrSettings.videoDecoderDevice());
    private final Queue<AudioBatch> decodedAudio = new ConcurrentLinkedQueue<>();
    private final Queue<VideoBatch> decodedVideo = new ConcurrentLinkedQueue<>();
    private final Queue<PendingVideoSegment> compressedVideo = new ArrayDeque<>();
    private final Queue<List<DecodedVideoFrame>> videoBatches = new ArrayDeque<>();
    private final PriorityQueue<DecodedVideoFrame> video = new PriorityQueue<>(Comparator.comparingLong(DecodedVideoFrame::presentationTimeUs));
    private final Queue<DecodedAudioFrame> audio =
            new PriorityQueue<>(Comparator.comparingLong(DecodedAudioFrame::presentationTimeUs));
    private final AtomicInteger pendingAudio = new AtomicInteger();
    private final AtomicInteger pendingVideo = new AtomicInteger();
    private final DecodeCompletionGuard completions = new DecodeCompletionGuard();
    private final AtomicBoolean decoderSelectionLogged = new AtomicBoolean();
    private final AtomicBoolean decoderFallbackLogged = new AtomicBoolean();
    private CinemarrVideoTexture texture = new CinemarrVideoTexture();
    private VideoStreamIdentity identity;
    private UUID televisionId;
    private UUID sessionId;
    private String itemKey = "";
    private long generation = -1;
    private final AtomicInteger decoderRecoveries = new AtomicInteger();
    private int videoDrops;
    private long lastPresentedUs;
    private String lastFrameSha256 = "";
    private long lastHealthMs;
    private boolean caughtUp;
    private boolean audioInputExhausted;
    private long queuedVideoBytes;
    private long queuedCompressedVideoBytes;

    public void tick(CinemarrVideoClientState.StreamState state) {
        VideoPackets.SessionState session = state.session();
        if (session == null || session.status() == VideoPackets.SessionStatus.IDLE || session.status() == VideoPackets.SessionStatus.ERROR) {
            reset();
            return;
        }
        itemKey = session.item() == null ? "" : session.item().key();
        televisionId = session.televisionId();
        if (!session.identity().equals(identity)) {
            resetQueues();
            identity = session.identity(); sessionId = session.sessionId();
            generation = session.generation();
        }
        for (AudioBatch batch; (batch = decodedAudio.poll()) != null; ) {
            if (!batch.identity.equals(identity)) continue;
            audio.addAll(batch.audio);
            compressedVideo.add(new PendingVideoSegment(batch.identity, batch.segmentIndex,
                    batch.presentationTimeUs, batch.sourceFirstTimestampUs, batch.mpegTs));
            queuedCompressedVideoBytes += batch.mpegTs.length;
        }
        for (VideoBatch batch; canBufferAnotherVideoBatch() && (batch = decodedVideo.poll()) != null; ) {
            if (!batch.identity.equals(identity)) continue;
            if (!batch.video.isEmpty()) {
                videoBatches.add(batch.video);
                queuedVideoBytes += videoBytes(batch.video);
            }
            videoDrops += batch.droppedFrames;
        }
        VideoSegmentAssembler.CompletedSegment segment;
        while (canBufferAnotherCompressedSegment() && audio.size() < MAX_QUEUED_AUDIO_FRAMES
                && (segment = state.pollSegment()) != null) submitAudio(segment);
        if (pendingVideo.get() < MAX_VIDEO_DECODE_JOBS && canBufferAnotherVideoBatch() && !compressedVideo.isEmpty()) {
            PendingVideoSegment next = compressedVideo.poll();
            queuedCompressedVideoBytes -= next.mpegTs.length;
            submitVideo(next);
        }
        if (video.isEmpty() && !videoBatches.isEmpty()) video.addAll(videoBatches.poll());
        long targetUs = authoritativePositionMsLocal(session) * 1_000L;
        DecodedVideoFrame current = null;
        while (!video.isEmpty() && video.peek().presentationTimeUs() <= targetUs + 40_000L) {
            current = video.poll();
            queuedVideoBytes -= current.rgbaView().length;
        }
        if (current != null) {
            texture.upload(current);
            lastPresentedUs = current.presentationTimeUs();
            if (ProtocolLimits.videoProbeEnabled()) lastFrameSha256 = Hashing.sha256(current.rgbaView());
            if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance video frame: session={} generation={} ptsUs={} sha256={} dimensions={}x{}",
                    sessionId, generation, lastPresentedUs, lastFrameSha256, current.width(), current.height());
        }
        caughtUp = lastPresentedUs > 0 && Math.abs(lastPresentedUs - targetUs) <= 250_000L;
        audioInputExhausted = state.inputExhausted() && pendingAudio.get() == 0
                && decodedAudio.isEmpty() && audio.isEmpty();
    }

    boolean retainPausedFrameFrom(CinemarrVideoPlayback previous, VideoPackets.SessionState next) {
        if (texture.ready() || !previous.texture.ready()
                || !stonytark.cinemarr.core.client.PausedFrameRetention.permits(
                        previous.identity, previous.itemKey, next)) return false;
        // Move only the GPU texture. The old pipeline still owns its jobs and
        // audio queues and is closed normally when its last stream disappears.
        texture.close();
        texture = previous.texture;
        previous.texture = new CinemarrVideoTexture();
        lastPresentedUs = previous.lastPresentedUs;
        lastFrameSha256 = previous.lastFrameSha256;
        if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                "Acceptance paused frame retained: generation={} frameSha256={} ptsUs={}",
                next.generation(), lastFrameSha256, lastPresentedUs);
        return true;
    }

    boolean retainReplacementFrameFrom(CinemarrVideoPlayback previous, VideoPackets.SessionState next) {
        if (texture.ready() || !previous.texture.ready() || next == null || previous.televisionId == null
                || !previous.televisionId.equals(next.televisionId()) || previous.itemKey == null
                || next.item() == null || !previous.itemKey.equals(next.item().key()) || previous.identity == null
                || !previous.identity.timelineId().equals(next.timelineId())
                || previous.identity.timelineGeneration() != next.timelineGeneration()) return false;
        texture.close(); texture = previous.texture; previous.texture = new CinemarrVideoTexture();
        lastPresentedUs = previous.lastPresentedUs; lastFrameSha256 = previous.lastFrameSha256;
        return true;
    }

    private void submitAudio(VideoSegmentAssembler.CompletedSegment segment) {
        if (!segment.identity().equals(identity)) return;
        long epoch = completions.epoch();
        pendingAudio.incrementAndGet();
        audioDecoderExecutor.run(() -> {
            try {
                byte[] mpegTs = segment.data();
                List<DecodedAudioFrame> decoded = decoder.decodeAudio(mpegTs);
                long firstUs = decoded.isEmpty() ? -1L : decoded.get(0).presentationTimeUs();
                long offsetUs = segment.presentationTimeMs() * 1_000L - Math.max(0, firstUs);
                List<DecodedAudioFrame> shiftedAudio = decoded.stream().map(value -> new DecodedAudioFrame(
                        Math.max(0, value.presentationTimeUs() + offsetUs), value.sampleRate(), value.channels(), value.pcmView())).toList();
                completions.publish(epoch, () -> decodedAudio.add(new AudioBatch(segment.identity(), segment.segmentIndex(),
                        segment.presentationTimeMs() * 1_000L, firstUs, mpegTs, shiftedAudio)));
            } catch (Throwable error) {
                completions.publish(epoch, () -> {
                    decoderRecoveries.incrementAndGet();
                    Cinemarr.LOGGER.warn("Cinemarr rejected video segment {} audio: {}", segment.segmentIndex(), error.toString());
                });
            }
        }).whenComplete((unused, failure) -> pendingAudio.decrementAndGet());
    }

    private void submitVideo(PendingVideoSegment segment) {
        long epoch = completions.epoch();
        pendingVideo.incrementAndGet();
        videoDecoderExecutor.run(() -> {
            try {
                FfmpegVideoDecoder.VideoDecodeResult result = decoder.decodeVideoBounded(segment.mpegTs);
                long videoFirstUs = result.video().isEmpty() ? 0L : result.video().get(0).presentationTimeUs();
                long sourceFirstUs = segment.sourceFirstTimestampUs >= 0 ? segment.sourceFirstTimestampUs : videoFirstUs;
                long offsetUs = segment.presentationTimeUs - sourceFirstUs;
                List<DecodedVideoFrame> shifted = result.video().stream().map(value -> new DecodedVideoFrame(
                        Math.max(0, value.presentationTimeUs() + offsetUs), value.width(), value.height(), value.rgbaView())).toList();
                completions.publish(epoch, () -> {
                    logDecoderSelection();
                    decodedVideo.add(new VideoBatch(segment.identity, shifted, result.droppedFrames()));
                });
            } catch (Throwable error) {
                completions.publish(epoch, () -> {
                    decoderRecoveries.incrementAndGet();
                    Cinemarr.LOGGER.warn("Cinemarr rejected video segment {} video: {}", segment.segmentIndex, error.toString());
                });
            }
        }).whenComplete((unused, failure) -> pendingVideo.decrementAndGet());
    }

    private void logDecoderSelection() {
        FfmpegVideoDecoder.DecoderDiagnostics metrics = decoder.diagnostics();
        if (metrics.fallbackCount() > 0) {
            if (decoderFallbackLogged.compareAndSet(false, true)) Cinemarr.LOGGER.warn(
                    "Cinemarr video decoder requested={} deviceType={} fell back permanently to software: {}",
                    metrics.requestedBackend().configValue(), metrics.deviceType(), metrics.fallbackReason());
            return;
        }
        if (decoderSelectionLogged.compareAndSet(false, true)) Cinemarr.LOGGER.info(
                "Cinemarr video decoder requested={} effective={} deviceType={}",
                metrics.requestedBackend().configValue(), metrics.effectiveBackend().configValue(), metrics.deviceType());
    }

    static long authoritativePositionMs(VideoPackets.SessionState session, long now) {
        if (session.paused()) return session.positionMs();
        return Math.min(session.durationMs(), Math.max(0, session.positionMs() + Math.max(0, now - session.serverEpochMs())));
    }

    static long authoritativePositionMsLocal(VideoPackets.SessionState session) {
        return authoritativePositionMsLocal(session, System.currentTimeMillis());
    }

    static long authoritativePositionMsLocal(VideoPackets.SessionState session, long localNow) {
        long localEpoch = CinemarrClientState.INSTANCE.serverToLocalEpoch(session.serverEpochMs());
        long estimatedServerNow = session.serverEpochMs() + Math.max(0, localNow - localEpoch);
        return authoritativePositionMs(session, estimatedServerNow);
    }

    public CinemarrVideoTexture texture() { return texture; }
    public DecodedAudioFrame pollAudio() { return audio.poll(); }
    public int decoderRecoveries() { return decoderRecoveries.get(); }
    public int videoDrops() { return videoDrops; }
    public long lastPresentedUs() { return lastPresentedUs; }
    public String lastFrameSha256() { return lastFrameSha256; }
    public boolean caughtUp() { return caughtUp; }
    public boolean audioInputExhausted() { return audioInputExhausted; }
    int queuedAudioFrames() { return audio.size(); }
    long queuedVideoBytes() { return queuedVideoBytes; }
    public FfmpegVideoDecoder.DecoderDiagnostics decoderDiagnostics() { return decoder.diagnostics(); }

    private boolean canBufferAnotherVideoBatch() {
        return decodedVideo.size() + videoBatches.size() < MAX_DECODED_VIDEO_BATCHES
                && queuedVideoBytes < MAX_QUEUED_VIDEO_BYTES;
    }

    private boolean canBufferAnotherCompressedSegment() {
        return pendingAudio.get() < MAX_AUDIO_DECODE_JOBS
                && decodedAudio.size() + compressedVideo.size() + pendingVideo.get() < MAX_COMPRESSED_VIDEO_SEGMENTS
                && queuedCompressedVideoBytes < MAX_COMPRESSED_VIDEO_BYTES;
    }

    private static long videoBytes(List<DecodedVideoFrame> frames) {
        long bytes = 0;
        for (DecodedVideoFrame frame : frames) bytes += frame.rgbaView().length;
        return bytes;
    }

    public void sendHealth(CinemarrVideoClientState.StreamState streamState, int audioUnderruns) {
        VideoPackets.SessionState session = streamState.session(); long now = System.currentTimeMillis();
        if (session == null || session.item() == null || now - lastHealthMs < ProtocolCapabilities.HEALTH_INTERVAL_MS) return;
        long targetUs = authoritativePositionMsLocal(session) * 1_000L;
        long lastQueuedUs = video.isEmpty() ? lastPresentedUs : Math.max(lastPresentedUs,
                video.stream().mapToLong(DecodedVideoFrame::presentationTimeUs).max().orElse(lastPresentedUs));
        long bufferedMs = Math.max(0, (lastQueuedUs - targetUs) / 1_000L);
        long driftMs = lastPresentedUs == 0 ? 0 : Math.max(-30_000, Math.min(30_000, (lastPresentedUs - targetUs) / 1_000L));
        CinemarrNetwork.sendToServer(new VideoPayloads.ClientHealth(new VideoPackets.ClientHealth(session.identity(), texture.ready() ? "PLAYING" : "BUFFERING", decoderRecoveries.get(), videoDrops,
                audioUnderruns, Math.min(60_000, bufferedMs), driftMs)));
        if (ProtocolLimits.videoProbeEnabled()) {
            FfmpegVideoDecoder.DecoderDiagnostics metrics = decoder.diagnostics();
            Cinemarr.LOGGER.info("Acceptance decoder metrics: requested={} effective={} deviceType={} segments={} frames={} "
                            + "wallNanos={} cpuNanos={} transferNanos={} conversionNanos={} peakRetainedBytes={} "
                            + "fallbackCount={} recoveries={} videoDrops={} audioUnderruns={} driftMs={}",
                    metrics.requestedBackend().configValue(), metrics.effectiveBackend().configValue(), metrics.deviceType(),
                    metrics.decodedSegments(), metrics.decodedFrames(), metrics.wallNanos(), metrics.cpuNanos(),
                    metrics.transferNanos(), metrics.conversionNanos(), metrics.peakRetainedBytes(), metrics.fallbackCount(),
                    decoderRecoveries.get(), videoDrops, audioUnderruns, driftMs);
        }
        lastHealthMs = now;
    }

    public void reset() {
        identity = null; televisionId = null; sessionId = null; itemKey = "";
        generation = -1;
        lastPresentedUs = 0;
        lastFrameSha256 = "";
        caughtUp = false;
        audioInputExhausted = false;
        lastHealthMs = 0;
        resetQueues();
        texture.close();
    }

    private void resetQueues() {
        completions.reset(this::clearQueues);
    }

    private void clearQueues() {
        decodedAudio.clear();
        decodedVideo.clear();
        compressedVideo.clear();
        videoBatches.clear();
        video.clear();
        audio.clear();
        queuedVideoBytes = 0;
        queuedCompressedVideoBytes = 0;
    }

    @Override public void close() {
        // Retire publication before interrupting workers. An obsolete decode's
        // cancellation is not a failure of the replacement stream, and a late
        // successful native return must not repopulate these retired queues.
        completions.close(this::clearQueues);
        reset();
        audioDecoderExecutor.close();
        videoDecoderExecutor.close();
    }

    private record AudioBatch(VideoStreamIdentity identity, int segmentIndex, long presentationTimeUs,
                              long sourceFirstTimestampUs, byte[] mpegTs, List<DecodedAudioFrame> audio) {}
    private record PendingVideoSegment(VideoStreamIdentity identity, int segmentIndex, long presentationTimeUs,
                                       long sourceFirstTimestampUs, byte[] mpegTs) {}
    private record VideoBatch(VideoStreamIdentity identity, List<DecodedVideoFrame> video, int droppedFrames) {}
}
