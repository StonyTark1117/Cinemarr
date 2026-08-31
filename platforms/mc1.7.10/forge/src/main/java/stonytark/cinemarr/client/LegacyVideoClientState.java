package stonytark.cinemarr.client;

import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.client.TransferWindowFlow;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/** Visible legacy televisions and one compressed stream per watch-party generation. */
final class LegacyVideoClientState {
    static final LegacyVideoClientState INSTANCE = new LegacyVideoClientState();
    private final Map<Long, VideoPackets.SessionState> televisions = new LinkedHashMap<Long, VideoPackets.SessionState>();
    private final Map<StreamKey, StreamState> streams = new LinkedHashMap<StreamKey, StreamState>();
    private final Map<UUID, List<QueuedVideo>> queues = new LinkedHashMap<UUID, List<QueuedVideo>>();
    private VideoPackets.LibraryList libraries = new VideoPackets.LibraryList(Collections.<VideoPackets.LibrarySummary>emptyList());
    private VideoPackets.BrowseResults browse = new VideoPackets.BrowseResults("", "", "", 0, false,
            Collections.<stonytark.cinemarr.core.library.VideoMediaItem>emptyList());

    boolean accept(LegacyPacketTypes.Type<?> type, Object payload) {
        if (type == LegacyPacketTypes.OPEN_VIDEO_SCREEN) {
            net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new LegacyVideoScreen(
                    ((LegacyPacketTypes.OpenVideoScreen) payload).controllerPos(), this));
            requestLibraries(); return true;
        }
        if (type == LegacyPacketTypes.VIDEO_LIBRARY_LIST) { libraries = (VideoPackets.LibraryList) payload; screenChanged(); return true; }
        if (type == LegacyPacketTypes.VIDEO_BROWSE_RESULTS) { browse = (VideoPackets.BrowseResults) payload; screenChanged(); return true; }
        if (type == LegacyPacketTypes.VIDEO_SESSION_STATE) { acceptSession((VideoPackets.SessionState) payload); screenChanged(); return true; }
        if (type == LegacyPacketTypes.VIDEO_TELEVISION_REMOVED) { removeTelevision(((VideoPackets.TelevisionRemoved) payload).controllerPos()); return true; }
        if (type == LegacyPacketTypes.VIDEO_SESSION_QUEUE) {
            VideoPackets.SessionQueue value = (VideoPackets.SessionQueue) payload; queues.put(value.sessionId(), value.entries()); screenChanged(); return true;
        }
        if (type == LegacyPacketTypes.VIDEO_MANIFEST) {
            VideoPackets.SegmentManifest value = (VideoPackets.SegmentManifest) payload;
            StreamState stream = streams.get(new StreamKey(value.sessionId(), value.generation())); if (stream != null) stream.manifest(value); return true;
        }
        if (type == LegacyPacketTypes.VIDEO_SEGMENT_CHUNK) {
            VideoPackets.SegmentChunk value = (VideoPackets.SegmentChunk) payload;
            StreamState stream = streams.get(new StreamKey(value.sessionId(), value.generation())); if (stream != null) stream.chunk(value); return true;
        }
        return false;
    }

    private void acceptSession(VideoPackets.SessionState value) {
        televisions.put(value.controllerPos(), value);
        if (value.item() != null && !value.sessionId().equals(new UUID(0, 0))) {
            StreamKey key = new StreamKey(value.sessionId(), value.generation()); StreamState stream = streams.get(key);
            if (stream == null) { stream = new StreamState(key); streams.put(key, stream); } stream.session(value);
        }
        pruneStreams();
    }
    private void removeTelevision(long controller) { televisions.remove(controller); pruneStreams(); }
    private void pruneStreams() {
        Set<StreamKey> referenced = new HashSet<StreamKey>(); Set<UUID> visibleSessions = new HashSet<UUID>();
        for (VideoPackets.SessionState state : televisions.values()) if (state.item() != null && !state.sessionId().equals(new UUID(0, 0))) {
            referenced.add(new StreamKey(state.sessionId(), state.generation())); visibleSessions.add(state.sessionId());
        }
        java.util.Iterator<Map.Entry<StreamKey, StreamState>> iterator = streams.entrySet().iterator();
        while (iterator.hasNext()) { Map.Entry<StreamKey, StreamState> entry = iterator.next(); if (!referenced.contains(entry.getKey())) { entry.getValue().reset(); iterator.remove(); } }
        queues.keySet().retainAll(visibleSessions);
    }

    void reset() {
        libraries = new VideoPackets.LibraryList(Collections.<VideoPackets.LibrarySummary>emptyList());
        browse = new VideoPackets.BrowseResults("", "", "", 0, false, Collections.<stonytark.cinemarr.core.library.VideoMediaItem>emptyList());
        televisions.clear(); queues.clear(); for (StreamState stream : streams.values()) stream.reset(); streams.clear();
    }
    void requestLibraries() { LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_LIBRARY_LIST_REQUEST, LegacyPacketTypes.EmptyRequest.INSTANCE); }
    void browse(String library, String parent, String query, int page) { LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_BROWSE_REQUEST, new VideoPackets.BrowseRequest(library, parent, query, page)); }
    void command(VideoPackets.SessionCommand command) { LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SESSION_COMMAND, command); }
    VideoPackets.LibraryList libraries() { return libraries; }
    VideoPackets.BrowseResults browse() { return browse; }
    VideoPackets.SessionState session(long controller) { return televisions.get(controller); }
    Collection<VideoPackets.SessionState> televisions() { return new ArrayList<VideoPackets.SessionState>(televisions.values()); }
    List<QueuedVideo> queue(long controller) {
        VideoPackets.SessionState session = televisions.get(controller); List<QueuedVideo> values = session == null ? null : queues.get(session.sessionId());
        return values == null ? Collections.<QueuedVideo>emptyList() : values;
    }
    Collection<StreamState> streamStates() { return new ArrayList<StreamState>(streams.values()); }
    void tick(long now) { for (StreamState stream : streams.values()) stream.tick(now); }
    StreamState stream(StreamKey key) { return streams.get(key); }
    List<VideoPackets.SessionState> televisionsForStream(StreamKey key) {
        List<VideoPackets.SessionState> values = new ArrayList<VideoPackets.SessionState>();
        for (VideoPackets.SessionState state : televisions.values()) if (state.sessionId().equals(key.sessionId) && state.generation() == key.generation) values.add(state);
        return values;
    }
    private void screenChanged() {
        if (net.minecraft.client.Minecraft.getMinecraft().currentScreen instanceof LegacyVideoScreen) {
            ((LegacyVideoScreen) net.minecraft.client.Minecraft.getMinecraft().currentScreen).stateChanged();
        }
    }

    static final class StreamKey {
        final UUID sessionId; final long generation;
        StreamKey(UUID sessionId, long generation) { this.sessionId = sessionId; this.generation = generation; }
        @Override public boolean equals(Object value) { if (this == value) return true; if (!(value instanceof StreamKey)) return false;
            StreamKey other = (StreamKey) value; return generation == other.generation && sessionId.equals(other.sessionId); }
        @Override public int hashCode() { return 31 * sessionId.hashCode() + (int) (generation ^ generation >>> 32); }
    }

    static final class StreamState {
        private static final int MAX_READY_SEGMENTS = 4;
        private final StreamKey key;
        private final VideoSegmentAssembler assembler = new VideoSegmentAssembler();
        private final Queue<VideoSegmentAssembler.CompletedSegment> ready = new ArrayDeque<VideoSegmentAssembler.CompletedSegment>();
        private VideoPackets.SessionState session;
        private VideoPackets.SegmentManifest manifest;
        private long requestId;
        private int requestedSegment = -1, lastCompletedSegment = -1, currentWindowStart, totalChunks, requestRetries;
        private long requestSentAt;
        private int deferredSegment = -1;
        private boolean finalSegmentReceived;
        StreamState(StreamKey key) { this.key = key; }
        void session(VideoPackets.SessionState value) { session = value; }
        VideoPackets.SessionState session() { return session; }
        StreamKey key() { return key; }
        void manifest(VideoPackets.SegmentManifest value) {
            if (!key.sessionId.equals(value.sessionId()) || key.generation != value.generation() || session == null) return;
            if (sameWindow(manifest, value) && requestedSegment >= 0) { manifest = value; return; }
            boolean continuation = manifest != null && !value.segments().isEmpty() && value.segments().get(0).index() == lastCompletedSegment + 1;
            manifest = value;
            if (continuation) {
                int first = value.segments().get(0).index();
                if (ready.size() < MAX_READY_SEGMENTS) request(first, 0); else deferredSegment = first;
            } else {
                ready.clear(); deferredSegment = -1;
                int first = seekSegment(value, session.positionMs()); if (first >= 0) request(first, 0);
            }
        }
        void chunk(VideoPackets.SegmentChunk value) {
            if (manifest == null || value.requestId() != requestId
                    || value.segmentIndex() != requestedSegment || value.totalChunks() < 1) return;
            requestSentAt = System.currentTimeMillis(); requestRetries = 0;
            if (totalChunks == 0) { totalChunks = value.totalChunks(); assembler.begin(value.sessionId(), value.generation(), value.requestId(),
                    value.segmentIndex(), value.totalChunks(), value.segmentSha256(), value.presentationTimeMs(), value.keyframe()); }
            Optional<VideoSegmentAssembler.CompletedSegment> completed = assembler.accept(value.sessionId(), value.generation(), value.requestId(),
                    value.segmentIndex(), value.chunkIndex(), value.totalChunks(), value.segmentSha256(), value.presentationTimeMs(), value.keyframe(), value.data());
            TransferWindowFlow.Decision flow = TransferWindowFlow.afterChunk(
                    value.chunkIndex(), currentWindowStart, 8, totalChunks, completed.isPresent());
            if (completed.isPresent()) {
                ready.add(completed.get()); lastCompletedSegment = value.segmentIndex(); requestedSegment = -1;
                requestSentAt = 0L; requestRetries = 0;
                LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SEGMENT_ACKNOWLEDGEMENT,
                        new VideoPackets.SegmentAcknowledgement(value.sessionId(), value.generation(), value.requestId(),
                                value.segmentIndex(), flow.receivedThroughChunk(), bufferedMs()));
                int local = descriptorIndex(value.segmentIndex());
                if (local >= 0 && local + 1 < manifest.segments().size()) {
                    int next = manifest.segments().get(local + 1).index();
                    if (ready.size() < MAX_READY_SEGMENTS) request(next, 0); else deferredSegment = next;
                }
                else if (manifest.hasMore()) LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_MANIFEST_REQUEST,
                        new VideoPackets.SegmentManifestRequest(key.sessionId, key.generation, value.segmentIndex() + 1));
                else finalSegmentReceived = true;
            } else if (flow.continuesSegment()) {
                LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SEGMENT_ACKNOWLEDGEMENT,
                        new VideoPackets.SegmentAcknowledgement(value.sessionId(), value.generation(), value.requestId(),
                                value.segmentIndex(), flow.receivedThroughChunk(), bufferedMs()));
                request(value.segmentIndex(), flow.nextWindowStart());
            }
        }
        private void request(int segment, int firstChunk) {
            int descriptor = descriptorIndex(segment); if (manifest == null || descriptor < 0) return;
            long targetMs = LegacyVideoPlayback.authoritativePositionMs(session,
                    LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis()));
            if (firstChunk == 0 && !withinPrefetchLead(
                    manifest.segments().get(descriptor).presentationTimeMs(), targetMs)) {
                deferredSegment = segment; return;
            }
            deferredSegment = -1; requestedSegment = segment;
            currentWindowStart = firstChunk;
            if (firstChunk == 0) { totalChunks = 0; requestId++; }
            requestSentAt = System.currentTimeMillis();
            LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SEGMENT_REQUEST,
                    new VideoPackets.SegmentRequest(key.sessionId, key.generation, requestId, segment, firstChunk, 8));
        }
        void tick(long now) {
            if (requestedSegment < 0 || requestSentAt == 0L || now - requestSentAt < 1_500L) return;
            if (requestRetries++ < 3) {
                requestSentAt = now;
                LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_SEGMENT_REQUEST,
                        new VideoPackets.SegmentRequest(key.sessionId, key.generation, requestId,
                                requestedSegment, currentWindowStart, 8));
                return;
            }
            assembler.reset(); requestedSegment = -1; requestSentAt = 0L; requestRetries = 0;
            if (manifest != null && !manifest.segments().isEmpty()) {
                LegacyNetwork.sendToServer(LegacyPacketTypes.VIDEO_MANIFEST_REQUEST,
                        new VideoPackets.SegmentManifestRequest(key.sessionId, key.generation,
                                manifest.segments().get(0).index()));
            }
        }
        static boolean withinPrefetchLead(long segmentPresentationTimeMs, long playbackPositionMs) {
            return segmentPresentationTimeMs <= playbackPositionMs + ProtocolLimits.CLIENT_VIDEO_PREFETCH_LEAD_MS;
        }
        private int descriptorIndex(int segment) { if (manifest == null) return -1; for (int index = 0; index < manifest.segments().size(); index++) if (manifest.segments().get(index).index() == segment) return index; return -1; }
        private static boolean sameWindow(VideoPackets.SegmentManifest left, VideoPackets.SegmentManifest right) {
            if (left == null || right == null || left.segments().size() != right.segments().size()) return false;
            if (left.segments().isEmpty()) return true;
            return left.segments().get(0).index() == right.segments().get(0).index()
                    && left.segments().get(left.segments().size() - 1).index()
                    == right.segments().get(right.segments().size() - 1).index();
        }
        private static int seekSegment(VideoPackets.SegmentManifest manifest, long position) {
            int result = manifest.segments().isEmpty() ? -1 : manifest.segments().get(0).index();
            for (VideoPackets.SegmentDescriptor descriptor : manifest.segments()) { if (descriptor.presentationTimeMs() > position) break; if (descriptor.keyframe()) result = descriptor.index(); }
            return result;
        }
        private long bufferedMs() { long total = 0; for (VideoSegmentAssembler.CompletedSegment segment : ready) { int index = descriptorIndex(segment.segmentIndex()); if (index >= 0) total += manifest.segments().get(index).durationMs(); } return total; }
        boolean inputExhausted() { return finalSegmentReceived && ready.isEmpty() && requestedSegment < 0 && deferredSegment < 0; }
        void reset() { assembler.reset(); ready.clear(); manifest = null; requestedSegment = -1; lastCompletedSegment = -1;
            currentWindowStart = totalChunks = 0; deferredSegment = -1;
            requestSentAt = 0L; requestRetries = 0; finalSegmentReceived = false; }
        VideoSegmentAssembler.CompletedSegment pollSegment() {
            if (deferredSegment >= 0 && ready.size() < MAX_READY_SEGMENTS) request(deferredSegment, 0);
            VideoSegmentAssembler.CompletedSegment value = ready.poll();
            if (deferredSegment >= 0 && ready.size() < MAX_READY_SEGMENTS) request(deferredSegment, 0);
            return value;
        }
    }
}
