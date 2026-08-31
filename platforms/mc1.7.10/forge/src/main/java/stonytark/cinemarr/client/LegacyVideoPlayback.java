package stonytark.cinemarr.client;

import stonytark.cinemarr.Cinemarr;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded Java-8 FFmpeg pipeline with generation-safe client-thread delivery. */
final class LegacyVideoPlayback implements AutoCloseable {
    private static final int MAX_DECODE_JOBS = 2;
    private static final int MAX_DECODED_VIDEO_BATCHES = 8;
    private static final long MAX_QUEUED_VIDEO_BYTES = 192L * 1024L * 1024L;
    private static final int MAX_AUDIO_FRAMES = 128;
    private final BoundedWorkExecutor executor =
            new BoundedWorkExecutor(1, MAX_DECODE_JOBS, "Cinemarr legacy FFmpeg decoder ");
    private final LegacyFfmpegVideoDecoder decoder = new LegacyFfmpegVideoDecoder(
            CinemarrSettings.videoDecoderBackend(), CinemarrSettings.videoDecoderDevice());
    private final Queue<DecodedBatch> decoded = new ConcurrentLinkedQueue<DecodedBatch>();
    private final Queue<List<LegacyDecodedVideoFrame>> videoBatches = new ArrayDeque<List<LegacyDecodedVideoFrame>>();
    private final PriorityQueue<LegacyDecodedVideoFrame> video = new PriorityQueue<LegacyDecodedVideoFrame>(32,
            new Comparator<LegacyDecodedVideoFrame>() { @Override public int compare(LegacyDecodedVideoFrame left, LegacyDecodedVideoFrame right) {
                return left.presentationTimeUs() < right.presentationTimeUs() ? -1 : left.presentationTimeUs() == right.presentationTimeUs() ? 0 : 1;
            }});
    private final Queue<LegacyDecodedAudioFrame> audio = new ArrayDeque<LegacyDecodedAudioFrame>();
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicBoolean decoderSelectionLogged = new AtomicBoolean();
    private final AtomicBoolean decoderFallbackLogged = new AtomicBoolean();
    private final LegacyVideoTexture texture = new LegacyVideoTexture();
    private UUID sessionId;
    private long generation = -1;
    private int decoderRecoveries;
    private int videoDrops;
    private long lastPresentedUs;
    private String lastFrameSha256 = "";
    private long lastHealthMs;
    private boolean caughtUp;
    private boolean audioInputExhausted;
    private long queuedVideoBytes;

    void tick(LegacyVideoClientState.StreamState stream) {
        VideoPackets.SessionState session = stream.session();
        if (session == null || session.status() == VideoPackets.SessionStatus.IDLE || session.status() == VideoPackets.SessionStatus.ERROR) { reset(); return; }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            resetQueues(); sessionId = session.sessionId(); generation = session.generation();
        }
        VideoSegmentAssembler.CompletedSegment segment;
        while (pending.get() < MAX_DECODE_JOBS && decoded.size() < MAX_DECODE_JOBS && canBufferAnotherVideoBatch()
                && audio.size() < MAX_AUDIO_FRAMES && (segment = stream.pollSegment()) != null) submit(segment);
        DecodedBatch batch;
        while (audio.size() < MAX_AUDIO_FRAMES && canBufferAnotherVideoBatch() && (batch = decoded.poll()) != null) if (batch.sessionId.equals(sessionId) && batch.generation == generation) {
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

    private void submit(final VideoSegmentAssembler.CompletedSegment segment) {
        pending.incrementAndGet();
        executor.run(() -> {
            try {
                LegacyDecodedMediaSegment result = decoder.decode(segment.data()); long first = earliestTimestamp(result);
                logDecoderSelection();
                long offset = segment.presentationTimeMs() * 1_000L - first;
                List<LegacyDecodedVideoFrame> shiftedVideo = new ArrayList<LegacyDecodedVideoFrame>();
                for (LegacyDecodedVideoFrame frame : result.video()) shiftedVideo.add(new LegacyDecodedVideoFrame(
                        Math.max(0, frame.presentationTimeUs() + offset), frame.width(), frame.height(), frame.rgbaView()));
                List<LegacyDecodedAudioFrame> shiftedAudio = new ArrayList<LegacyDecodedAudioFrame>();
                for (LegacyDecodedAudioFrame frame : result.audio()) shiftedAudio.add(new LegacyDecodedAudioFrame(
                        Math.max(0, frame.presentationTimeUs() + offset), frame.sampleRate(), frame.channels(), frame.pcmView()));
                decoded.add(new DecodedBatch(segment.sessionId(), segment.generation(), shiftedVideo, shiftedAudio));
            } catch (Throwable failure) {
                decoderRecoveries++; Cinemarr.LOGGER.warn("Cinemarr rejected legacy video segment {}: {}", segment.segmentIndex(), failure.toString());
            }
        }).whenComplete((unused, failure) -> pending.decrementAndGet());
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
        return decoded.size() + videoBatches.size() < MAX_DECODED_VIDEO_BATCHES
                && queuedVideoBytes < MAX_QUEUED_VIDEO_BYTES;
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
        LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_CLIENT_HEALTH, new VideoPackets.ClientHealth(session.sessionId(),
                session.generation(), texture.ready() ? "PLAYING" : "BUFFERING", decoderRecoveries, videoDrops, underruns,
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
    void reset() { sessionId = null; generation = -1; lastPresentedUs = lastHealthMs = 0; lastFrameSha256 = ""; caughtUp = false; audioInputExhausted = false; resetQueues(); texture.close(); }
    private void resetQueues() { decoded.clear(); videoBatches.clear(); video.clear(); audio.clear(); queuedVideoBytes = 0L; }
    @Override public void close() { reset(); executor.close(); }

    private static final class DecodedBatch {
        final UUID sessionId; final long generation; final List<LegacyDecodedVideoFrame> video; final List<LegacyDecodedAudioFrame> audio;
        DecodedBatch(UUID sessionId, long generation, List<LegacyDecodedVideoFrame> video, List<LegacyDecodedAudioFrame> audio) {
            this.sessionId = sessionId; this.generation = generation; this.video = Collections.unmodifiableList(video); this.audio = Collections.unmodifiableList(audio);
        }
    }
}
