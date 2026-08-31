package stonytark.cinemarr.core.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;

final class TransferGrantRegistryTest {
    @Test void oneWindowPerClientAndInvalidAcknowledgementCannotStealIt() {
        TransferGrantRegistry registry = new TransferGrantRegistry(1_000);
        UUID client = UUID.randomUUID(), session = UUID.randomUUID();
        VideoPackets.SegmentRequest first = request(session, 4, 10, 2);
        VideoPackets.SegmentRequest second = request(session, 4, 11, 3);
        assertTrue(registry.tryAcquire(client, first, 100));
        assertFalse(registry.tryAcquire(client, second, 101));
        assertFalse(registry.acknowledge(client, acknowledgement(second), 102));
        assertTrue(registry.owns(client, first, 103));
        assertTrue(registry.acknowledge(client, acknowledgement(first), 104));
        assertEquals(0, registry.size());
    }

    @Test void expiryAndLifecycleRemovalReleaseOwnership() {
        TransferGrantRegistry registry = new TransferGrantRegistry(1_000);
        UUID client = UUID.randomUUID(), session = UUID.randomUUID();
        VideoPackets.SegmentRequest first = request(session, 1, 1, 0);
        VideoPackets.SegmentRequest second = request(session, 1, 2, 1);
        assertTrue(registry.tryAcquire(client, first, 100));
        assertFalse(registry.owns(client, first, 1_100));
        assertTrue(registry.tryAcquire(client, second, 1_100));
        assertEquals(java.util.Collections.singletonList(client), registry.expire(2_100));
        assertTrue(registry.tryAcquire(client, first, 2_101));
        registry.remove(client);
        assertEquals(0, registry.size());
    }

    private static VideoPackets.SegmentRequest request(UUID session, long generation, long request, int segment) {
        return new VideoPackets.SegmentRequest(session, generation, request, segment, 0, 4);
    }
    private static VideoPackets.SegmentAcknowledgement acknowledgement(VideoPackets.SegmentRequest request) {
        return new VideoPackets.SegmentAcknowledgement(request.sessionId(), request.generation(), request.requestId(),
                request.segmentIndex(), request.firstChunk() + request.chunkCount() - 1, 2_000);
    }
}
