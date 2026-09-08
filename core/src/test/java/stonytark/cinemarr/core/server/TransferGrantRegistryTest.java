package stonytark.cinemarr.core.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;

final class TransferGrantRegistryTest {
    @Test void replacingEveryScreenOfSamePartyAbandonsWindowButOneRetainedScreenPreservesIt() {
        TransferGrantRegistry registry = new TransferGrantRegistry(30_000);
        UUID client = UUID.randomUUID(), session = UUID.randomUUID();
        UUID oldScreen = UUID.randomUUID(), newScreen = UUID.randomUUID(), otherScreen = UUID.randomUUID();
        java.util.Set<UUID> previous = java.util.Collections.singleton(oldScreen);
        java.util.Map<UUID, UUID> visible = new java.util.HashMap<UUID, UUID>();
        VideoPackets.SegmentRequest window = request(session, 3, 71, 60);
        assertTrue(registry.tryAcquire(client, window, 100));
        visible.put(oldScreen, session);
        visible.put(newScreen, session);
        assertFalse(registry.releaseUntracked(client, visible, previous));
        assertTrue(registry.owns(client, window, 101));
        visible.remove(oldScreen);
        visible.put(otherScreen, UUID.randomUUID());
        assertTrue(registry.releaseUntracked(client, visible, previous),
                "all old TVs are removed before replacements arrive, resetting the client assembler even in the same party");
        assertFalse(registry.owns(client, window, 102));
        assertTrue(registry.tryAcquire(client, request(session, 3, 1, 61), 103));
        assertFalse(registry.releaseUntracked(client, visible, visible.keySet()));
        assertTrue(registry.releaseUntracked(client, java.util.Collections.<UUID, UUID>emptyMap(), visible.keySet()));
    }

    @Test void leavingScreenReleasesWindowBeforeTimeoutWithoutDisturbingOtherViewers() {
        TransferGrantRegistry registry = new TransferGrantRegistry(30_000);
        UUID client = UUID.randomUUID(), other = UUID.randomUUID(), session = UUID.randomUUID();
        VideoPackets.SegmentRequest abandoned = request(session, 3, 71, 60);
        VideoPackets.SegmentRequest returned = request(session, 3, 1, 74);
        assertTrue(registry.tryAcquire(client, abandoned, 100));
        assertTrue(registry.tryAcquire(other, abandoned, 100));
        assertFalse(registry.releaseUntracked(client, java.util.Collections.singleton(session)),
                "still-visible playback must retain flow control");
        assertFalse(registry.tryAcquire(client, returned, 101));
        assertTrue(registry.releaseUntracked(client, java.util.Collections.<UUID>emptySet()));
        assertTrue(registry.owns(other, abandoned, 102));
        assertFalse(registry.owns(client, abandoned, 102), "late completion must not enqueue departed work");
        assertTrue(registry.tryAcquire(client, returned, 103), "same-generation world return cannot wait for expiry");
        registry.release(client, abandoned);
        assertFalse(registry.acknowledge(client, acknowledgement(abandoned), 104));
        assertTrue(registry.owns(client, returned, 105));
        assertTrue(registry.releaseUntracked(client, java.util.Collections.singleton(UUID.randomUUID())),
                "switching to another screen also abandons the old window");
        assertFalse(registry.releaseUntracked(client, java.util.Collections.<UUID>emptySet()));
    }

    @Test void disconnectedOwnershipIsVisibleEvenWhileOtherClientsKeepStreaming() {
        TransferGrantRegistry registry = new TransferGrantRegistry(1_000);
        UUID active = UUID.randomUUID(), departed = UUID.randomUUID();
        VideoPackets.SegmentRequest request = request(UUID.randomUUID(), 1, 1, 0);
        registry.tryAcquire(active, request, 0);
        registry.tryAcquire(departed, request, 0);
        assertEquals(1, registry.countOutside(java.util.Collections.singleton(active)));
        registry.remove(departed);
        assertEquals(0, registry.countOutside(java.util.Collections.singleton(active)));
        assertEquals(1, registry.size(), "connected client's grant need not be idle");
        registry.tryAcquire(departed, request, 1);
        assertEquals(1, registry.countOutside(java.util.Collections.singleton(active)), "late resurrection is observable");
        registry.clear();
        assertEquals(0, registry.countOutside(java.util.Collections.<UUID>emptySet()));
    }

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

    @Test void eachLargeSegmentWindowNeedsItsOwnBoundedAcknowledgement() {
        TransferGrantRegistry registry = new TransferGrantRegistry(1_000);
        UUID client = UUID.randomUUID(), session = UUID.randomUUID();
        VideoPackets.SegmentRequest first = new VideoPackets.SegmentRequest(session, 3, 9, 0, 0, 8);
        VideoPackets.SegmentRequest second = new VideoPackets.SegmentRequest(session, 3, 9, 0, 8, 8);
        assertTrue(registry.tryAcquire(client, first, 100));
        assertFalse(registry.acknowledge(client,
                new VideoPackets.SegmentAcknowledgement(session, 3, 9, 0, 15, 0), 101));
        assertTrue(registry.acknowledge(client,
                new VideoPackets.SegmentAcknowledgement(session, 3, 9, 0, 7, 0), 102));
        assertTrue(registry.tryAcquire(client, second, 103));
        assertFalse(registry.owns(client, first, 104));
        assertTrue(registry.acknowledge(client,
                new VideoPackets.SegmentAcknowledgement(session, 3, 9, 0, 15, 0), 105));
    }

    @Test void replacementGenerationSupersedesAnUnacknowledgedWindow() {
        TransferGrantRegistry registry = new TransferGrantRegistry(1_000);
        UUID client = UUID.randomUUID(), session = UUID.randomUUID();
        VideoPackets.SegmentRequest pausedGeneration = request(session, 2, 7, 4);
        VideoPackets.SegmentRequest resumedGeneration = request(session, 3, 1, 5);
        assertTrue(registry.tryAcquire(client, pausedGeneration, 100));
        assertTrue(registry.tryAcquire(client, resumedGeneration, 101));
        assertFalse(registry.acknowledge(client, acknowledgement(pausedGeneration), 102));
        assertTrue(registry.owns(client, resumedGeneration, 103));
        assertFalse(registry.tryAcquire(client, request(UUID.randomUUID(), 99, 2, 0), 104));
        assertTrue(registry.acknowledge(client, acknowledgement(resumedGeneration), 105));
    }

    private static VideoPackets.SegmentRequest request(UUID session, long generation, long request, int segment) {
        return new VideoPackets.SegmentRequest(session, generation, request, segment, 0, 4);
    }
    private static VideoPackets.SegmentAcknowledgement acknowledgement(VideoPackets.SegmentRequest request) {
        return new VideoPackets.SegmentAcknowledgement(request.sessionId(), request.generation(), request.requestId(),
                request.segmentIndex(), request.firstChunk() + request.chunkCount() - 1, 2_000);
    }
}
