package stonytark.cinemarr.client;

import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded off-thread decode queue with generation-safe render-thread delivery. */
public final class CinemarrVideoPlayback implements AutoCloseable {
    private static final int MAX_DECODE_JOBS = 2;
    private static final int MAX_QUEUED_VIDEO_FRAMES = 180;
    private static final int MAX_QUEUED_AUDIO_FRAMES = 128;
    private final ExecutorService decoderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Cinemarr FFmpeg decoder");
        thread.setDaemon(true);
        return thread;
    });
    private final FfmpegVideoDecoder decoder = new FfmpegVideoDecoder();
    private final Queue<DecodedBatch> decoded = new ConcurrentLinkedQueue<>();
    private final PriorityQueue<DecodedVideoFrame> video = new PriorityQueue<>(Comparator.comparingLong(DecodedVideoFrame::presentationTimeUs));
    private final Queue<DecodedAudioFrame> audio = new ArrayDeque<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final CinemarrVideoTexture texture = new CinemarrVideoTexture();
    private UUID sessionId;
    private long generation = -1;
    private int decoderRecoveries;
    private int videoDrops;
    private long lastPresentedUs;
    private String lastFrameSha256 = "";
    private long lastHealthMs;
    private boolean caughtUp;

    public void tick(CinemarrVideoClientState.StreamState state) {
        VideoPackets.SessionState session = state.session();
        if (session == null || session.status() == VideoPackets.SessionStatus.IDLE || session.status() == VideoPackets.SessionStatus.ERROR) {
            reset();
            return;
        }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            resetQueues();
            sessionId = session.sessionId();
            generation = session.generation();
        }
        VideoSegmentAssembler.CompletedSegment segment;
        while (pending.get() < MAX_DECODE_JOBS && decoded.size() < MAX_DECODE_JOBS
                && audio.size() < MAX_QUEUED_AUDIO_FRAMES && (segment = state.pollSegment()) != null) submit(segment);
        for (DecodedBatch batch; audio.size() < MAX_QUEUED_AUDIO_FRAMES && (batch = decoded.poll()) != null; ) {
            if (!batch.sessionId.equals(sessionId) || batch.generation != generation) continue;
            for (DecodedVideoFrame frame : batch.video) {
                if (video.size() >= MAX_QUEUED_VIDEO_FRAMES) { video.poll(); videoDrops++; }
                video.add(frame);
            }
            audio.addAll(batch.audio);
        }
        long targetUs = authoritativePositionMsLocal(session) * 1_000L;
        DecodedVideoFrame current = null;
        while (!video.isEmpty() && video.peek().presentationTimeUs() <= targetUs + 40_000L) current = video.poll();
        if (current != null) {
            texture.upload(current);
            lastPresentedUs = current.presentationTimeUs();
            lastFrameSha256 = Hashing.sha256(current.rgbaView());
            if (ProtocolLimits.videoProbeEnabled()) Cinemarr.LOGGER.info(
                    "Acceptance video frame: session={} generation={} ptsUs={} sha256={} dimensions={}x{}",
                    sessionId, generation, lastPresentedUs, lastFrameSha256, current.width(), current.height());
        }
        caughtUp = lastPresentedUs > 0 && Math.abs(lastPresentedUs - targetUs) <= 250_000L;
    }

    private void submit(VideoSegmentAssembler.CompletedSegment segment) {
        pending.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            try {
                DecodedMediaSegment result = decoder.decode(segment.data());
                long firstUs = earliestTimestamp(result);
                long offsetUs = segment.presentationTimeMs() * 1_000L - firstUs;
                List<DecodedVideoFrame> shiftedVideo = result.video().stream().map(value -> new DecodedVideoFrame(
                        Math.max(0, value.presentationTimeUs() + offsetUs), value.width(), value.height(), value.rgbaView())).toList();
                List<DecodedAudioFrame> shiftedAudio = result.audio().stream().map(value -> new DecodedAudioFrame(
                        Math.max(0, value.presentationTimeUs() + offsetUs), value.sampleRate(), value.channels(), value.pcmView())).toList();
                decoded.add(new DecodedBatch(segment.sessionId(), segment.generation(), shiftedVideo, shiftedAudio));
            } catch (Throwable error) {
                decoderRecoveries++;
                Cinemarr.LOGGER.warn("Cinemarr rejected video segment {}: {}", segment.segmentIndex(), error.toString());
            } finally {
                pending.decrementAndGet();
            }
        }, decoderExecutor);
    }

    private static long earliestTimestamp(DecodedMediaSegment result) {
        long first = Long.MAX_VALUE;
        if (!result.video().isEmpty()) first = Math.min(first, result.video().get(0).presentationTimeUs());
        if (!result.audio().isEmpty()) first = Math.min(first, result.audio().get(0).presentationTimeUs());
        return first == Long.MAX_VALUE ? 0 : first;
    }

    static long authoritativePositionMs(VideoPackets.SessionState session, long now) {
        if (session.paused()) return session.positionMs();
        return Math.min(session.durationMs(), Math.max(0, session.positionMs() + Math.max(0, now - session.serverEpochMs())));
    }

    static long authoritativePositionMsLocal(VideoPackets.SessionState session) {
        long localEpoch = CinemarrClientState.INSTANCE.serverToLocalEpoch(session.serverEpochMs());
        long estimatedServerNow = session.serverEpochMs() + Math.max(0, System.currentTimeMillis() - localEpoch);
        return authoritativePositionMs(session, estimatedServerNow);
    }

    public CinemarrVideoTexture texture() { return texture; }
    public DecodedAudioFrame pollAudio() { return audio.poll(); }
    public int decoderRecoveries() { return decoderRecoveries; }
    public int videoDrops() { return videoDrops; }
    public long lastPresentedUs() { return lastPresentedUs; }
    public String lastFrameSha256() { return lastFrameSha256; }
    public boolean caughtUp() { return caughtUp; }
    int queuedAudioFrames() { return audio.size(); }

    public void sendHealth(CinemarrVideoClientState.StreamState streamState, int audioUnderruns) {
        VideoPackets.SessionState session = streamState.session(); long now = System.currentTimeMillis();
        if (session == null || session.item() == null || now - lastHealthMs < 5_000) return;
        long targetUs = authoritativePositionMsLocal(session) * 1_000L;
        long lastQueuedUs = video.isEmpty() ? lastPresentedUs : Math.max(lastPresentedUs,
                video.stream().mapToLong(DecodedVideoFrame::presentationTimeUs).max().orElse(lastPresentedUs));
        long bufferedMs = Math.max(0, (lastQueuedUs - targetUs) / 1_000L);
        long driftMs = lastPresentedUs == 0 ? 0 : Math.max(-30_000, Math.min(30_000, (lastPresentedUs - targetUs) / 1_000L));
        CinemarrNetwork.sendToServer(new VideoPayloads.ClientHealth(new VideoPackets.ClientHealth(session.sessionId(),
                session.generation(), texture.ready() ? "PLAYING" : "BUFFERING", decoderRecoveries, videoDrops,
                audioUnderruns, Math.min(60_000, bufferedMs), driftMs)));
        lastHealthMs = now;
    }

    public void reset() {
        sessionId = null;
        generation = -1;
        lastPresentedUs = 0;
        lastFrameSha256 = "";
        caughtUp = false;
        lastHealthMs = 0;
        resetQueues();
        texture.close();
    }

    private void resetQueues() {
        decoded.clear();
        video.clear();
        audio.clear();
    }

    @Override public void close() {
        reset();
        decoderExecutor.shutdownNow();
    }

    private record DecodedBatch(UUID sessionId, long generation, List<DecodedVideoFrame> video, List<DecodedAudioFrame> audio) {}
}
