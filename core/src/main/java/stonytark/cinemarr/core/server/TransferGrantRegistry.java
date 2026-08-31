package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import stonytark.cinemarr.core.protocol.VideoPackets;

/** Owns the single in-flight segment window permitted for each connected client. */
public final class TransferGrantRegistry {
    private final long timeoutMs;
    private final ConcurrentMap<UUID, Grant> grants = new ConcurrentHashMap<UUID, Grant>();

    public TransferGrantRegistry(long timeoutMs) {
        if (timeoutMs < 1) throw new IllegalArgumentException("timeoutMs must be positive");
        this.timeoutMs = timeoutMs;
    }

    public boolean tryAcquire(UUID client, VideoPackets.SegmentRequest request, long nowMs) {
        if (client == null || request == null) throw new IllegalArgumentException("client and request are required");
        Grant replacement = new Grant(request, nowMs);
        while (true) {
            Grant existing = grants.putIfAbsent(client, replacement);
            if (existing == null) return true;
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

    public void remove(UUID client) { grants.remove(client); }
    public void clear() { grants.clear(); }
    public int size() { return grants.size(); }

    private static final class Grant {
        private final UUID session;
        private final long generation;
        private final long requestId;
        private final int segment;
        private final int firstChunk;
        private final int lastRequestedChunk;
        private final long createdAtMs;

        private Grant(VideoPackets.SegmentRequest value, long createdAtMs) {
            session = value.sessionId(); generation = value.generation(); requestId = value.requestId();
            segment = value.segmentIndex(); firstChunk = value.firstChunk();
            lastRequestedChunk = firstChunk + value.chunkCount() - 1; this.createdAtMs = createdAtMs;
        }

        private boolean expired(long nowMs, long timeoutMs) { return nowMs - createdAtMs >= timeoutMs; }
        private boolean matches(VideoPackets.SegmentRequest value) {
            return session.equals(value.sessionId()) && generation == value.generation()
                    && requestId == value.requestId() && segment == value.segmentIndex()
                    && firstChunk == value.firstChunk() && lastRequestedChunk == firstChunk + value.chunkCount() - 1;
        }
        private boolean matches(VideoPackets.SegmentAcknowledgement value) {
            return session.equals(value.sessionId()) && generation == value.generation()
                    && requestId == value.requestId() && segment == value.segmentIndex()
                    && value.receivedThroughChunk() >= firstChunk
                    && value.receivedThroughChunk() <= lastRequestedChunk;
        }
    }
}
