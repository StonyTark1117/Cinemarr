package stonytark.cinemarr.core.server;

import java.util.Queue;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.video.TvDisplaySettings;
import stonytark.cinemarr.core.video.PresentationMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

import static org.junit.jupiter.api.Assertions.*;
import static stonytark.cinemarr.core.server.VideoHealthRegistry.Result.*;

class VideoHealthRegistryTest {
    private final UUID client = UUID.randomUUID(), other = UUID.randomUUID(), party = UUID.randomUUID();
    private final VideoStreamIdentity first = identity(), second = identity();
    private final Set<VideoStreamIdentity> current = new HashSet<>(Arrays.asList(first, second));
    private final BiPredicate<VideoStreamIdentity, UUID> authority = (identity, viewer) ->
            (client.equals(viewer) || other.equals(viewer)) && current.contains(identity);

    private VideoStreamIdentity identity() { return new VideoStreamIdentity(party, 3, UUID.randomUUID(), 1); }
    private static VideoPackets.ClientHealth health(VideoStreamIdentity identity, int drops) {
        return new VideoPackets.ClientHealth(identity, "PLAYING", 0, drops, 0, 8000, 10);
    }

    @Test void siblingTelevisionsAndSameTvViewersRetainIndependentLatestReports() {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        VideoPackets.ClientHealth a = health(first, 2), b = health(second, 5), follower = health(first, 8);
        assertEquals(ACCEPTED, registry.record(client, a, 0, authority));
        assertEquals(ACCEPTED, registry.record(client, b, 0, authority));
        assertEquals(ACCEPTED, registry.record(other, follower, 0, authority));
        VideoPackets.ClientHealth nextA = health(first, 3);
        assertEquals(ACCEPTED, registry.record(client, nextA, 1, authority));
        List<VideoHealthRegistry.Report> reports = registry.currentReports(1, authority);
        assertEquals(3, reports.size()); assertEquals(2, registry.clients());
        assertSame(nextA, reports.get(0).value()); assertEquals(client, reports.get(0).client());
        assertSame(b, reports.get(1).value()); assertSame(follower, reports.get(2).value());
        reports.clear(); assertEquals(3, registry.size(), "Diagnostic snapshots must not mutate ownership");
    }

    @Test void qualityReplacementSeekAndRetuneRejectLateReportsWithoutOverwritingSiblings() {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        registry.record(client, health(first, 1), 0, authority);
        VideoPackets.ClientHealth sibling = health(second, 2);
        registry.record(client, sibling, 0, authority);
        VideoStreamIdentity replacement = new VideoStreamIdentity(party, 3, first.streamId(), 2);
        current.remove(first); current.add(replacement);
        assertEquals(ACCEPTED, registry.record(client, health(replacement, 0), 1, authority));
        assertEquals(IGNORE_STALE, registry.record(client, health(first, 99), 2, authority));
        assertEquals(2, registry.size());
        assertTrue(registry.currentReports(2, authority).stream().anyMatch(report -> report.value() == sibling));
        // A shared seek changes only the timeline generation, invalidating both old reports.
        VideoStreamIdentity seek = new VideoStreamIdentity(party, 4, replacement.streamId(), 2);
        current.clear(); current.add(seek);
        assertEquals(IGNORE_STALE, registry.record(client, health(replacement, 99), 3, authority));
        assertEquals(ACCEPTED, registry.record(client, health(seek, 0), 3, authority));
        assertEquals(1, registry.size());
        // Matching media ID/generation cannot attach a report from a different watch party.
        VideoStreamIdentity retuned = new VideoStreamIdentity(UUID.randomUUID(), 4, seek.streamId(), 2);
        current.clear(); current.add(retuned);
        assertEquals(ACCEPTED, registry.record(client, health(retuned, 0), 4, authority));
        assertEquals(IGNORE_STALE, registry.record(client, health(seek, 99), 5, authority));
        assertEquals(retuned, registry.currentReports(5, authority).get(0).value().identity());
    }

    @Test void rejectsInvalidOrUnownedTelemetryBeforeAnyMutation() {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        registry.record(client, health(first, 1), 0, authority);
        BiPredicate<VideoStreamIdentity, UUID> never = (identity, viewer) -> { fail("Invalid telemetry reached authority lookup"); return false; };
        assertEquals(INVALID, registry.record(client, null, 1, never));
        assertEquals(INVALID, registry.record(client, health(second, -1), 1, never));
        assertEquals(IGNORE_STALE, registry.record(UUID.randomUUID(), health(first, 9), 1, authority));
        assertEquals(IGNORE_STALE, registry.record(client, health(identity(), 9), 1, authority));
        assertEquals(1, registry.size()); assertEquals(1, registry.clients());
        assertEquals(1, registry.currentReports(1, authority).get(0).value().videoDrops());
    }

    @Test void capsAdmissionWithoutEvictingHealthyReportsAndReclaimsExpiredOrRetiredSlots() {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        VideoStreamIdentity third = identity(); current.add(third);
        registry.record(client, health(first, 1), 0, authority);
        registry.record(client, health(second, 2), 500, authority);
        assertEquals(AT_CAPACITY, registry.record(client, health(third, 3), 999, authority));
        assertEquals(2, registry.size());
        assertEquals(ACCEPTED, registry.record(client, health(third, 3), 1000, authority));
        assertEquals(2, registry.size());
        current.remove(second);
        assertEquals(ACCEPTED, registry.record(client, health(first, 4), 1001, authority));
        assertEquals(2, registry.size());
        assertFalse(registry.currentReports(1001, authority).stream().anyMatch(r -> r.value().identity().equals(second)));
    }

    @Test void expiryIsPerReportAndRemovesClientBucketsWithoutNeedingFurtherPackets() {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        registry.record(client, health(first, 1), 0, authority);
        registry.record(client, health(second, 2), 500, authority);
        registry.record(other, health(first, 3), 0, authority);
        registry.prune(999, authority); assertEquals(3, registry.size());
        registry.prune(1000, authority); assertEquals(1, registry.size()); assertEquals(1, registry.clients());
        assertEquals(second, registry.currentReports(1000, authority).get(0).value().identity());
        registry.prune(1500, authority); assertEquals(0, registry.size()); assertEquals(0, registry.clients());
        assertThrows(IllegalArgumentException.class, () -> new VideoHealthRegistry(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new VideoHealthRegistry(1000, 0));
    }

    @Test void visibilityDisconnectAndShutdownRetireOnlyTheirOwnedReports() {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        registry.record(client, health(first, 1), 0, authority);
        registry.record(client, health(second, 2), 0, authority);
        registry.record(other, health(first, 3), 0, authority);
        registry.retain(client, Collections.singleton(second));
        assertEquals(2, registry.size()); assertEquals(2, registry.clients());
        registry.remove(client); assertEquals(1, registry.size());
        assertEquals(other, registry.currentReports(0, authority).get(0).client());
        VideoStreamIdentity wrongGeneration = new VideoStreamIdentity(party, 4, first.streamId(), 1);
        registry.retain(other, Collections.singleton(wrongGeneration));
        assertEquals(0, registry.size()); assertEquals(0, registry.clients());
        registry.record(client, health(first, 1), 1, authority);
        registry.clear(); assertEquals(0, registry.size()); assertEquals(0, registry.clients());
    }

    @Test void realPoolRetirementAndSharedSeekInvalidateHealthBeforeReplacementStarts() throws Exception {
        VideoHealthRegistry registry = new VideoHealthRegistry(30_000, 2);
        Queue<Runnable> work = new ArrayDeque<>();
        AtomicInteger closed = new AtomicInteger();
        try (VideoSessionCoordinator timeline = new VideoSessionCoordinator(2, 1000, (id, generation, item, offset) -> () -> {});
             TelevisionStreamPool pool = new TelevisionStreamPool(2, 1000, (request, id, generation) -> closed::incrementAndGet,
                     operation -> {
                         CompletableFuture<Void> result = new CompletableFuture<>();
                         work.add(() -> { try { result.complete(operation.get()); } catch (Throwable failure) { result.completeExceptionally(failure); } });
                         return result;
                     })) {
            UUID tvA = UUID.randomUUID(), tvB = UUID.randomUUID();
            timeline.tune(tvA, "party"); timeline.tune(tvB, "party");
            timeline.play("party", new VideoMediaItem(
                    MediaKind.MOVIE, "fixture", "Fixture", "", "", 0, 900_000), 0, 1000);
            TvDisplaySettings display = TvDisplaySettings.defaults(
                    PresentationMode.FIT);
            for (UUID television : Arrays.asList(tvA, tvB))
                pool.update(new TelevisionStreamPool.Request(television, timeline.snapshot("party", 1000),
                        display, 16, 9, Collections.singleton(client)), 1000);
            pool.tick(1000); assertEquals(2, work.size());
            while (!work.isEmpty()) work.remove().run();
            VideoSessionCoordinator.Snapshot a = pool.snapshot(tvA, 1000), b = pool.snapshot(tvB, 1000);
            VideoStreamIdentity identityA = pool.identity(a.id(), a.generation()), identityB = pool.identity(b.id(), b.generation());
            BiPredicate<VideoStreamIdentity, UUID> live = (identity, viewer) ->
                    timeline.snapshotIfPresent(identity.timelineId(), identity.timelineGeneration(), 1000) != null
                            && pool.isViewer(identity, viewer);
            assertEquals(ACCEPTED, registry.record(client, health(identityA, 1), 1000, live));
            assertEquals(ACCEPTED, registry.record(client, health(identityB, 2), 1000, live));
            pool.retain(Collections.singleton(tvB));
            registry.prune(1100, live);
            assertEquals(1, registry.size());
            assertEquals(identityB, registry.currentReports(1100, live).get(0).value().identity());
            assertEquals(IGNORE_STALE, registry.record(client, health(identityA, 99), 1100, live));
            timeline.seek("party", 60_000, 1200);
            // The manager's timeline guard must reject health even before pool.update sees the seek.
            assertEquals(IGNORE_STALE, registry.record(client, health(identityB, 99), 1200, live));
            registry.prune(1200, live); assertEquals(0, registry.size()); assertEquals(0, registry.clients());
        }
        assertEquals(2, closed.get(), "Both TV media handles must close exactly once");
    }

    @Test void concurrentAdmissionCannotExceedTheConfiguredClientBound() throws Exception {
        VideoHealthRegistry registry = new VideoHealthRegistry(1000, 2);
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(4), start = new CountDownLatch(1);
        List<Future<VideoHealthRegistry.Result>> attempts = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                VideoPackets.ClientHealth value = health(identity(), i);
                attempts.add(workers.submit(() -> {
                    ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
                    return registry.record(client, value, 0, (identity, viewer) -> true);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            int accepted = 0;
            for (Future<VideoHealthRegistry.Result> attempt : attempts)
                if (attempt.get(5, TimeUnit.SECONDS) == ACCEPTED) accepted++;
            assertEquals(2, accepted); assertEquals(2, registry.size());
        } finally { start.countDown(); workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); }
    }
}
