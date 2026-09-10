package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IndependentTransferWindowsTest {
    private final UUID client=UUID.randomUUID(), party=UUID.randomUUID();
    private VideoPackets.SegmentRequest request() {
        return new VideoPackets.SegmentRequest(new VideoStreamIdentity(party, 9, UUID.randomUUID(), 2), 1, 0, 0, 8);
    }
    private VideoPackets.SegmentAcknowledgement ack(VideoPackets.SegmentRequest request) {
        return new VideoPackets.SegmentAcknowledgement(request.identity(), request.requestId(), 0, 7, 0);
    }

    @Test void sameClientCanTransferTwoTvsWithoutOpeningTwoWindowsForEitherTv() {
        TransferGrantRegistry registry=new TransferGrantRegistry(1000, 2);
        VideoPackets.SegmentRequest first=request(), second=request(), excess=request();
        assertTrue(registry.tryAcquire(client, first, 0));
        assertTrue(registry.tryAcquire(client, second, 0));
        assertEquals(TransferGrantRegistry.RequestDecision.REPLAY, registry.request(client, first, 1));
        assertEquals(TransferGrantRegistry.RequestDecision.REJECT, registry.request(client,
                new VideoPackets.SegmentRequest(first.identity(), 2, 1, 0, 8), 1));
        assertFalse(registry.tryAcquire(client, excess, 1));
        assertEquals(2, registry.size());
        assertTrue(registry.acknowledge(client, ack(first), 2));
        assertTrue(registry.owns(client, second, 2));
        assertTrue(registry.tryAcquire(client, excess, 2));
        assertEquals(2, registry.size());
    }

    @Test void expiryAndManifestRecoveryIdentifyOnlyTheAffectedWindow() {
        TransferGrantRegistry registry=new TransferGrantRegistry(1000, 2);
        VideoPackets.SegmentRequest first=request(), second=request();
        registry.tryAcquire(client, first, 0); registry.tryAcquire(client, second, 500);
        List<TransferGrantRegistry.Window> expired=registry.expireWindows(1000);
        assertEquals(1, expired.size()); assertEquals(client, expired.get(0).client());
        assertEquals(first.identity(), expired.get(0).identity());
        assertTrue(registry.owns(client, second, 1000));
        registry.tryAcquire(client, first, 1001);
        assertTrue(registry.restartManifest(client, first.identity()));
        assertTrue(registry.owns(client, second, 1002));
        assertEquals(1, registry.size());
    }

    @Test void concurrentTvAdmissionCannotExceedTheClientBound() throws Exception {
        TransferGrantRegistry registry=new TransferGrantRegistry(1000, 2);
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(4);
        java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(4), start=new java.util.concurrent.CountDownLatch(1);
        java.util.ArrayList<java.util.concurrent.Future<Boolean>> attempts=new java.util.ArrayList<>();
        try {
            for (int i=0; i<4; i++) {
                VideoPackets.SegmentRequest request=request();
                attempts.add(workers.submit(() -> { ready.countDown(); assertTrue(start.await(5, java.util.concurrent.TimeUnit.SECONDS)); return registry.tryAcquire(client, request, 0); }));
            }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)); start.countDown();
            int accepted=0;
            for (java.util.concurrent.Future<Boolean> attempt : attempts) if (attempt.get(5, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertEquals(2, accepted); assertEquals(2, registry.size()); assertEquals(2, registry.windows().size());
        } finally { start.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)); }
    }

    @Test void generationReplacementAndVisibilityCleanupPreserveSiblingAndOtherClient() {
        TransferGrantRegistry registry=new TransferGrantRegistry(1000, 2); UUID other=UUID.randomUUID();
        VideoPackets.SegmentRequest first=request(), second=request();
        registry.tryAcquire(client, first, 0); registry.tryAcquire(client, second, 0); registry.tryAcquire(other, first, 0);
        VideoPackets.SegmentRequest replacement=new VideoPackets.SegmentRequest(new VideoStreamIdentity(party, 9,
                first.sessionId(), 3), 1, 0, 0, 8);
        assertTrue(registry.tryAcquire(client, replacement, 1));
        registry.release(client, first); assertFalse(registry.acknowledge(client, ack(first), 2));
        assertTrue(registry.owns(client, replacement, 2)); assertTrue(registry.owns(client, second, 2));
        List<TransferGrantRegistry.Window> removed=registry.releaseExcept(client, Collections.singleton(second.identity()));
        assertEquals(1, removed.size()); assertEquals(replacement.identity(), removed.get(0).identity());
        assertTrue(registry.owns(client, second, 3)); assertTrue(registry.owns(other, first, 3));
        registry.remove(client); assertEquals(1, registry.size());
        assertEquals(1, registry.countOutside(Collections.singleton(client)));
        registry.remove(other); assertEquals(0, registry.size());
    }

    @Test void anExpiredWindowCannotSelectANewerRequestOrAdjacentWindowForDeletion() {
        TransferGrantRegistry registry=new TransferGrantRegistry(1000, 2);
        VideoPackets.SegmentRequest request=request(); registry.tryAcquire(client, request, 0);
        TransferGrantRegistry.Window expired=registry.expireWindows(1000).get(0);
        assertTrue(expired.matches(new VideoPackets.SegmentChunk(request.identity(), 1, 0, 7, 16, 0, true, "", new byte[] {1})));
        assertFalse(expired.matches(new VideoPackets.SegmentChunk(request.identity(), 2, 0, 7, 16, 0, true, "", new byte[] {1})));
        assertFalse(expired.matches(new VideoPackets.SegmentChunk(request.identity(), 1, 0, 8, 16, 0, true, "", new byte[] {1})));
        assertFalse(expired.matches(new VideoPackets.SegmentChunk(request.identity(), 1, 1, 7, 16, 0, true, "", new byte[] {1})));
        assertFalse(expired.matches(new VideoPackets.SegmentChunk(request().identity(), 1, 0, 7, 16, 0, true, "", new byte[] {1})));
    }

    @Test void scopedEgressRemovalPreservesSiblingOrderAndReleasesAllBudgetCounters() {
        FairEgressScheduler<String, String, String> scheduler=new FairEgressScheduler<>(4, 40, 4, 40, 8, 80);
        assertTrue(scheduler.enqueueBatch("viewer", "first", "player", Arrays.asList(
                new FairEgressScheduler.Item<>("a1", 10), new FairEgressScheduler.Item<>("a2", 10))));
        assertTrue(scheduler.enqueueBatch("viewer", "second", "player", Arrays.asList(
                new FairEgressScheduler.Item<>("b1", 10), new FairEgressScheduler.Item<>("b2", 10))));
        assertEquals(2, scheduler.removeMatching("viewer", value -> value.startsWith("a")));
        assertEquals(2, scheduler.backlogItems()); assertEquals(20, scheduler.backlogBytes());
        assertTrue(scheduler.enqueueBatch("viewer", "first", "player", Arrays.asList(
                new FairEgressScheduler.Item<>("a3", 10), new FairEgressScheduler.Item<>("a4", 10))));
        java.util.ArrayList<String> sent=new java.util.ArrayList<>();
        assertEquals(4, scheduler.drain(8, 80, (player, message) -> sent.add(message)));
        assertEquals(Arrays.asList("b1", "b2", "a3", "a4"), sent);
        assertEquals(0, scheduler.backlogItems()); assertEquals(0, scheduler.backlogBytes());
    }

    @Test void scopedRemovalDuringSendLeavesTheRoundRobinRingUsable() {
        FairEgressScheduler<String, String, String> scheduler=new FairEgressScheduler<>(4, 8, 80);
        scheduler.enqueueBatch("viewer", "player", Arrays.asList(new FairEgressScheduler.Item<>("first", 10),
                new FairEgressScheduler.Item<>("retired", 10), new FairEgressScheduler.Item<>("sibling", 10)));
        java.util.ArrayList<String> sent=new java.util.ArrayList<>();
        assertEquals(2, scheduler.drain(8, 80, (player, message) -> {
            sent.add(message); scheduler.removeMatching("viewer", value -> value.equals("retired"));
        }));
        assertEquals(Arrays.asList("first", "sibling"), sent);
        assertEquals(0, scheduler.backlogItems()); assertEquals(0, scheduler.backlogBytes());
    }
}
