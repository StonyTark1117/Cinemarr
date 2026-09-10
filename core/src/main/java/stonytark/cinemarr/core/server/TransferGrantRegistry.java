package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

/** One in-flight window per client/TV stream, under an explicit per-client bound. */
public final class TransferGrantRegistry {
    public enum RequestDecision { FETCH, REPLAY, REJECT }
    public static final class Window {
        private final UUID client;
        private final VideoStreamIdentity identity;
        private final long requestId;
        private final int segment, firstChunk, lastChunk;
        private Window(UUID client, Grant grant) {
            this.client=client; this.identity=grant.identity; this.requestId=grant.requestId;
            this.segment=grant.segment; this.firstChunk=grant.firstChunk; this.lastChunk=grant.lastRequestedChunk;
        }
        public boolean matches(VideoPackets.SegmentChunk chunk) {
            return identity.equals(chunk.identity()) && requestId==chunk.requestId() && segment==chunk.segmentIndex()
                    && chunk.chunkIndex()>=firstChunk && chunk.chunkIndex()<=lastChunk;
        }
        public UUID client() { return client; }
        public VideoStreamIdentity identity() { return identity; }
    }
    private final long timeoutMs;
    private final int maximumWindowsPerClient;
    private final Map<UUID, Map<UUID, Grant>> grants = new LinkedHashMap<UUID, Map<UUID, Grant>>();

    public TransferGrantRegistry(long timeoutMs) { this(timeoutMs, 1); }
    public TransferGrantRegistry(long timeoutMs, int maximumWindowsPerClient) {
        if (timeoutMs < 1 || maximumWindowsPerClient < 1) throw new IllegalArgumentException("Positive transfer limits are required");
        this.timeoutMs=timeoutMs; this.maximumWindowsPerClient=maximumWindowsPerClient;
    }

    /** Exact retries replay bytes without another download or an extended lifetime. */
    public synchronized RequestDecision request(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        if (tryAcquire(client, request, nowMs)) return RequestDecision.FETCH;
        return owns(client, request, nowMs) ? RequestDecision.REPLAY : RequestDecision.REJECT;
    }
    /** The caller validates current timeline/TV visibility before admission. */
    public synchronized boolean tryAcquire(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        if (client == null || request == null) throw new IllegalArgumentException("client and request are required");
        Map<UUID, Grant> windows = grants.get(client);
        Grant existing = windows == null ? null : windows.get(request.sessionId());
        if (existing != null && !existing.supersededBy(request) && !existing.expired(nowMs, timeoutMs)) return false;
        if (existing == null && windows != null && windows.size() >= maximumWindowsPerClient) return false;
        if (windows == null) { windows = new LinkedHashMap<UUID, Grant>(); grants.put(client, windows); }
        windows.put(request.sessionId(), new Grant(request, nowMs));
        return true;
    }
    private Grant get(UUID client, UUID stream) {
        Map<UUID, Grant> windows = grants.get(client); return windows == null ? null : windows.get(stream);
    }
    private void remove(UUID client, Grant grant) {
        Map<UUID, Grant> windows = grants.get(client);
        if (windows == null || windows.get(grant.session) != grant) return;
        windows.remove(grant.session); if (windows.isEmpty()) grants.remove(client);
    }
    public synchronized boolean owns(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        Grant grant = get(client, request.sessionId());
        if (grant == null) return false;
        if (grant.expired(nowMs, timeoutMs)) { remove(client, grant); return false; }
        return grant.matches(request);
    }
    public synchronized void release(UUID client, VideoPackets.SegmentRequest request) {
        Grant grant = get(client, request.sessionId());
        if (grant != null && grant.matches(request)) remove(client, grant);
    }
    public synchronized boolean acknowledge(UUID client, VideoPackets.SegmentAcknowledgement value, long nowMs) {
        Grant grant = get(client, value.sessionId());
        if (grant == null) return false;
        if (grant.expired(nowMs, timeoutMs)) { remove(client, grant); return false; }
        if (!grant.matches(value)) return false;
        remove(client, grant); return true;
    }
    public synchronized List<Window> expireWindows(long nowMs) {
        List<Window> expired = new ArrayList<Window>();
        for (UUID client : new ArrayList<UUID>(grants.keySet())) {
            for (Grant grant : new ArrayList<Grant>(grants.get(client).values())) {
                if (grant.expired(nowMs, timeoutMs)) { expired.add(new Window(client, grant)); remove(client, grant); }
            }
        }
        return expired;
    }
    public synchronized List<UUID> expire(long nowMs) {
        Set<UUID> clients = new LinkedHashSet<UUID>();
        for (Window window : expireWindows(nowMs)) clients.add(window.client());
        return new ArrayList<UUID>(clients);
    }
    public boolean restartManifest(UUID client, UUID session, long generation) {
        return restartManifest(client, new VideoStreamIdentity(session, generation, session, generation));
    }
    public synchronized boolean restartManifest(UUID client, VideoStreamIdentity identity) {
        if (client == null || identity == null) throw new IllegalArgumentException("client and stream identity are required");
        Grant grant = get(client, identity.streamId());
        if (grant == null || !grant.identity.equals(identity)) return false;
        remove(client, grant); return true;
    }
    /** Release only windows whose full media identity is no longer visible/current. */
    public synchronized List<Window> releaseExcept(UUID client, Set<VideoStreamIdentity> tracked) {
        if (client == null || tracked == null) throw new IllegalArgumentException("client and tracked identities are required");
        Map<UUID, Grant> windows = grants.get(client);
        if (windows == null) return Collections.emptyList();
        List<Window> removed = new ArrayList<Window>();
        for (Grant grant : new ArrayList<Grant>(windows.values())) if (!tracked.contains(grant.identity)) {
            removed.add(new Window(client, grant)); remove(client, grant);
        }
        return removed;
    }
    public synchronized void remove(UUID client) { grants.remove(client); }
    public synchronized List<Window> windows() {
        List<Window> result=new ArrayList<Window>();
        for (Map.Entry<UUID, Map<UUID, Grant>> entry : grants.entrySet())
            for (Grant grant : entry.getValue().values()) result.add(new Window(entry.getKey(), grant));
        return result;
    }
    public synchronized boolean releaseUntracked(UUID client, Map<UUID, UUID> screenSessions, Set<UUID> previousScreens) {
        if (screenSessions == null || previousScreens == null) throw new IllegalArgumentException("screen visibility is required");
        Set<UUID> continuous = new LinkedHashSet<UUID>();
        for (Map.Entry<UUID, UUID> screen : screenSessions.entrySet())
            if (previousScreens.contains(screen.getKey())) continuous.add(screen.getValue());
        return releaseUntracked(client, continuous);
    }
    public synchronized boolean releaseUntracked(UUID client, Set<UUID> trackedStreams) {
        if (client == null || trackedStreams == null) throw new IllegalArgumentException("client and tracked streams are required");
        Map<UUID, Grant> windows = grants.get(client); if (windows == null) return false;
        boolean removed = false;
        for (Grant grant : new ArrayList<Grant>(windows.values())) if (!trackedStreams.contains(grant.session)) {
            remove(client, grant); removed = true;
        }
        return removed;
    }
    public synchronized void clear() { grants.clear(); }
    public synchronized int size() {
        int count=0; for (Map<UUID, Grant> windows : grants.values()) count+=windows.size(); return count;
    }
    /** Diagnostic only: never mutates evidence of orphaned windows. */
    public synchronized int countOutside(Set<UUID> connected) {
        int count=0; for (Map.Entry<UUID, Map<UUID, Grant>> entry : grants.entrySet())
            if (!connected.contains(entry.getKey())) count+=entry.getValue().size();
        return count;
    }

    private static final class Grant {
        private final VideoStreamIdentity identity;
        private final UUID session;
        private final long generation;
        private final long requestId;
        private final int segment;
        private final int firstChunk;
        private final int lastRequestedChunk;
        private final long createdAtMs;

        private Grant(VideoPackets.SegmentRequest value, long createdAtMs) {
            identity = value.identity(); session = value.sessionId(); generation = value.generation(); requestId = value.requestId();
            segment = value.segmentIndex(); firstChunk = value.firstChunk();
            lastRequestedChunk = firstChunk + value.chunkCount() - 1; this.createdAtMs = createdAtMs;
        }

        private boolean expired(long nowMs, long timeoutMs) { return nowMs - createdAtMs >= timeoutMs; }
        private boolean supersededBy(VideoPackets.SegmentRequest value) {
            return session.equals(value.sessionId()) && (value.generation() > generation
                    || identity.timelineId().equals(value.timelineId()) && value.timelineGeneration() > identity.timelineGeneration());
        }
        private boolean matches(VideoPackets.SegmentRequest value) {
            return identity.equals(value.identity())
                    && requestId == value.requestId() && segment == value.segmentIndex()
                    && firstChunk == value.firstChunk() && lastRequestedChunk == firstChunk + value.chunkCount() - 1;
        }
        private boolean matches(VideoPackets.SegmentAcknowledgement value) {
            return identity.equals(value.identity())
                    && requestId == value.requestId() && segment == value.segmentIndex()
                    && value.receivedThroughChunk() >= firstChunk
                    && value.receivedThroughChunk() <= lastRequestedChunk;
        }
    }
}
