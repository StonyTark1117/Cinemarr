package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

/** Owns the single in-flight segment window permitted for each connected client. */
public final class TransferGrantRegistry {
    public enum RequestDecision { FETCH, REPLAY, REJECT }
    private final long timeoutMs;
    private final ConcurrentMap<UUID, Grant> grants = new ConcurrentHashMap<UUID, Grant>();

    public TransferGrantRegistry(long timeoutMs) {
        if (timeoutMs < 1) throw new IllegalArgumentException("timeoutMs must be positive");
        this.timeoutMs = timeoutMs;
    }

    /**
     * An identical retry may replay cached bytes, but must not enqueue another
     * download or extend the original grant's lifetime. Other windows still
     * require acknowledgement of the current window.
     */
    public RequestDecision request(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        if (tryAcquire(client, request, nowMs)) return RequestDecision.FETCH;
        return owns(client, request, nowMs) ? RequestDecision.REPLAY : RequestDecision.REJECT;
    }

    public boolean tryAcquire(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        if (client == null || request == null) throw new IllegalArgumentException("client and request are required");
        Grant replacement = new Grant(request, nowMs);
        while (true) {
            Grant existing = grants.putIfAbsent(client, replacement);
            if (existing == null) return true;
            if (existing.supersededBy(request)) {
                if (grants.replace(client, existing, replacement)) return true;
                continue;
            }
            if (!existing.expired(nowMs, timeoutMs)) return false;
            if (grants.replace(client, existing, replacement)) return true;
        }
    }

    public boolean owns(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        Grant grant = grants.get(client);
        if (grant == null) return false;
        if (grant.expired(nowMs, timeoutMs)) {
            grants.remove(client, grant);
            return false;
        }
        return grant.matches(request);
    }

    public void release(UUID client, VideoPackets.SegmentRequest request) {
        Grant grant = grants.get(client);
        if (grant != null && grant.matches(request)) grants.remove(client, grant);
    }

    public boolean acknowledge(UUID client, VideoPackets.SegmentAcknowledgement acknowledgement, long nowMs) {
        Grant grant = grants.get(client);
        if (grant == null || grant.expired(nowMs, timeoutMs) || !grant.matches(acknowledgement)) {
            if (grant != null && grant.expired(nowMs, timeoutMs)) grants.remove(client, grant);
            return false;
        }
        return grants.remove(client, grant);
    }

    public List<UUID> expire(long nowMs) {
        List<UUID> expired = new ArrayList<UUID>();
        for (java.util.Map.Entry<UUID, Grant> entry : grants.entrySet()) {
            if (entry.getValue().expired(nowMs, timeoutMs) && grants.remove(entry.getKey(), entry.getValue())) {
                expired.add(entry.getKey());
            }
        }
        return expired.isEmpty() ? Collections.<UUID>emptyList() : expired;
    }

    /** A validated manifest request abandons only this stream's old window. */
    public boolean restartManifest(UUID client, UUID session, long generation) {
        return restartManifest(client, new VideoStreamIdentity(session, generation, session, generation));
    }
    public boolean restartManifest(UUID client, VideoStreamIdentity identity) {
        if (client == null || identity == null) throw new IllegalArgumentException("client and stream identity are required");
        Grant grant = grants.get(client);
        return grant != null && grant.identity.equals(identity)
                && grants.remove(client, grant);
    }

    public void remove(UUID client) { grants.remove(client); }
    /** Preserve a window only while at least one of its screens stays visible. */
    public boolean releaseUntracked(UUID client, java.util.Map<UUID, UUID> screenSessions,
                                    java.util.Set<UUID> previousScreens) {
        if (screenSessions == null || previousScreens == null) throw new IllegalArgumentException("screen visibility is required");
        java.util.Set<UUID> continuousSessions = new java.util.HashSet<UUID>();
        for (java.util.Map.Entry<UUID, UUID> screen : screenSessions.entrySet()) {
            if (previousScreens.contains(screen.getKey())) continuousSessions.add(screen.getValue());
        }
        return releaseUntracked(client, continuousSessions);
    }
    /** Release an abandoned screen's window without cancelling a still-visible stream. */
    public boolean releaseUntracked(UUID client, java.util.Set<UUID> trackedSessions) {
        if (client == null || trackedSessions == null) throw new IllegalArgumentException("client and tracked sessions are required");
        Grant grant = grants.get(client);
        return grant != null && !trackedSessions.contains(grant.session) && grants.remove(client, grant);
    }
    public void clear() { grants.clear(); }
    public int size() { return grants.size(); }
    /** Diagnostic only: never expires or removes evidence of an orphaned grant. */
    public int countOutside(java.util.Set<UUID> connected) {
        int count = 0;
        for (UUID client : grants.keySet()) if (!connected.contains(client)) count++;
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
