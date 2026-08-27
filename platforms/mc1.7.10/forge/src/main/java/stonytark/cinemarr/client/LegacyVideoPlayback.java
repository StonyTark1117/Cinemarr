package stonytark.cinemarr.client;

import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.protocol.VideoPackets;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded Java-8 FFmpeg pipeline with generation-safe client-thread delivery. */
final class LegacyVideoPlayback implements AutoCloseable {
    private static final int MAX_DECODE_JOBS = 2;
    private static final int MAX_VIDEO_FRAMES = 180;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Cinemarr legacy FFmpeg decoder"); thread.setDaemon(true); return thread;
    });
    private final LegacyFfmpegVideoDecoder decoder = new LegacyFfmpegVideoDecoder();
    private final Queue<DecodedBatch> decoded = new ConcurrentLinkedQueue<DecodedBatch>();
    private final PriorityQueue<LegacyDecodedVideoFrame> video = new PriorityQueue<LegacyDecodedVideoFrame>(32,
            new Comparator<LegacyDecodedVideoFrame>() { @Override public int compare(LegacyDecodedVideoFrame left, LegacyDecodedVideoFrame right) {
                return left.presentationTimeUs() < right.presentationTimeUs() ? -1 : left.presentationTimeUs() == right.presentationTimeUs() ? 0 : 1;
            }});
    private final Queue<LegacyDecodedAudioFrame> audio = new ArrayDeque<LegacyDecodedAudioFrame>();
    private final AtomicInteger pending = new AtomicInteger();
    private final LegacyVideoTexture texture = new LegacyVideoTexture();
    private UUID sessionId;
    private long generation = -1;
    private int decoderRecoveries;
    private int videoDrops;
    private long lastPresentedUs;
    private long lastHealthMs;

    void tick(LegacyVideoClientState.StreamState stream) {
        VideoPackets.SessionState session = stream.session();
        if (session == null || session.status() == VideoPackets.SessionStatus.IDLE || session.status() == VideoPackets.SessionStatus.ERROR) { reset(); return; }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            resetQueues(); sessionId = session.sessionId(); generation = session.generation();
        }
        VideoSegmentAssembler.CompletedSegment segment;
        while (pending.get() < MAX_DECODE_JOBS && (segment = stream.pollSegment()) != null) submit(segment);
        DecodedBatch batch;
        while ((batch = decoded.poll()) != null) if (batch.sessionId.equals(sessionId) && batch.generation == generation) {
            for (LegacyDecodedVideoFrame frame : batch.video) { if (video.size() >= MAX_VIDEO_FRAMES) { video.poll(); videoDrops++; } video.add(frame); }
            audio.addAll(batch.audio);
        }
        long targetUs = authoritativePositionMs(session, LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis())) * 1_000L;
        LegacyDecodedVideoFrame current = null;
        while (!video.isEmpty() && video.peek().presentationTimeUs() <= targetUs + 40_000L) current = video.poll();
        if (current != null) { texture.upload(current); lastPresentedUs = current.presentationTimeUs(); }
    }

    private void submit(final VideoSegmentAssembler.CompletedSegment segment) {
        pending.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            try {
                LegacyDecodedMediaSegment result = decoder.decode(segment.data()); long first = earliestTimestamp(result);
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
            } finally { pending.decrementAndGet(); }
        }, executor);
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

    void sendHealth(LegacyVideoClientState.StreamState stream, int underruns) {
        VideoPackets.SessionState session = stream.session(); long now = System.currentTimeMillis();
        if (session == null || session.item() == null || now - lastHealthMs < 5_000L) return;
        long target = authoritativePositionMs(session, LegacyClientState.INSTANCE.serverEpoch(now)) * 1_000L;
        long last = lastPresentedUs; for (LegacyDecodedVideoFrame frame : video) last = Math.max(last, frame.presentationTimeUs());
        long buffered = Math.max(0, (last - target) / 1_000L); long drift = last == 0 ? 0 : Math.max(-30_000, Math.min(30_000, (last - target) / 1_000L));
        LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_CLIENT_HEALTH, new VideoPackets.ClientHealth(session.sessionId(),
                session.generation(), texture.ready() ? "PLAYING" : "BUFFERING", decoderRecoveries, videoDrops, underruns,
                Math.min(60_000, buffered), drift)); lastHealthMs = now;
    }
    void reset() { sessionId = null; generation = -1; lastPresentedUs = lastHealthMs = 0; resetQueues(); texture.close(); }
    private void resetQueues() { decoded.clear(); video.clear(); audio.clear(); }
    @Override public void close() { reset(); executor.shutdownNow(); }

    private static final class DecodedBatch {
        final UUID sessionId; final long generation; final List<LegacyDecodedVideoFrame> video; final List<LegacyDecodedAudioFrame> audio;
        DecodedBatch(UUID sessionId, long generation, List<LegacyDecodedVideoFrame> video, List<LegacyDecodedAudioFrame> audio) {
            this.sessionId = sessionId; this.generation = generation; this.video = Collections.unmodifiableList(video); this.audio = Collections.unmodifiableList(audio);
        }
    }
}
