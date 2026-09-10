package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.video.PixelMapping;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.ResolutionChoice;
import stonytark.cinemarr.core.video.TvDisplaySettings;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class TelevisionStreamPoolTest {
    private static final UUID VIEWER = UUID.randomUUID();
    private static final TvDisplaySettings DEFAULTS = TvDisplaySettings.defaults(PresentationMode.FIT);

    private static class Work implements TelevisionStreamPool.Work {
        final Queue<Runnable> queued = new ArrayDeque<>();
        int rejectMode;
        @Override public CompletableFuture<Void> submit(Supplier<Void> operation) {
            if (rejectMode == 1) throw new java.util.concurrent.RejectedExecutionException("controlled queue overload");
            if (rejectMode == 2) return null;
            CompletableFuture<Void> result = new CompletableFuture<>();
            if (rejectMode == 3) {
                result.completeExceptionally(new java.util.concurrent.RejectedExecutionException("controlled queue overload"));
                return result;
            }
            queued.add(() -> {
                try { result.complete(operation.get()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            return result;
        }
        void next() { assertFalse(queued.isEmpty()); queued.remove().run(); }
    }

    private static class Fixture implements AutoCloseable {
        final Work work = new Work();
        final List<TelevisionStreamPool.Request> starts = new ArrayList<>();
        final AtomicInteger createdMedia = new AtomicInteger(), closedMedia = new AtomicInteger();
        final VideoSessionCoordinator timeline = new VideoSessionCoordinator(4, 1000, (id, generation, item, offset) -> () -> {});
        final TelevisionStreamPool pool;
        Runnable duringStart = () -> {};
        boolean failStart;
        Fixture(int capacity) throws IOException {
            timeline.tune(UUID.randomUUID(), "party");
            timeline.play("party", new VideoMediaItem(MediaKind.MOVIE, "fixture", "Fixture", "", "PG", 0, 900_000), 10_000, 1000);
            pool = new TelevisionStreamPool(capacity, 1000, (request, id, generation) -> {
                starts.add(request);
                duringStart.run();
                if (failStart) throw new IOException("controlled replacement failure");
                createdMedia.incrementAndGet();
                return closedMedia::incrementAndGet;
            }, work);
        }
        void update(UUID tv, TvDisplaySettings display, long now, UUID... viewers) throws IOException {
            pool.update(new TelevisionStreamPool.Request(tv, timeline.snapshot("party", now), display, 16, 9,
                    new HashSet<>(Arrays.asList(viewers))), now);
        }
        void start(UUID tv) throws IOException {
            update(tv, DEFAULTS, 1000, VIEWER);
            pool.tick(1000);
            work.next();
            assertTrue(pool.snapshot(tv, 1000).transcoding());
        }
        @Override public void close() throws IOException {
            pool.close(); timeline.close();
            assertEquals(createdMedia.get(), closedMedia.get(), "Every returned media handle must be retired exactly once");
        }
    }

    private static TvDisplaySettings quality(String preset) {
        return DEFAULTS.apply(0, PresentationMode.FIT, PixelMapping.DETAILED, ResolutionChoice.preset(preset));
    }

    @Test void sameTvViewersShareOneStreamAndDifferentTvsHaveDistinctIdentities() throws Exception {
        try (Fixture f = new Fixture(2)) {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            f.start(first);
            f.update(first, DEFAULTS, 1100, VIEWER, UUID.randomUUID());
            f.update(second, DEFAULTS, 1100, VIEWER);
            f.pool.tick(1100);
            assertEquals(1, f.work.queued.size());
            f.work.next();
            assertEquals(2, f.starts.size());
            assertNotEquals(f.pool.snapshot(first, 1100).id(), f.pool.snapshot(second, 1100).id());
        }
    }

    @Test void viewerJoinAdvancesRevisionForManifestRepublish() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID();
            f.start(tv);
            long before = f.pool.changes();
            UUID follower = UUID.randomUUID();
            f.update(tv, DEFAULTS, 1100, VIEWER, follower);
            assertTrue(f.pool.changes() > before);
            f.pool.tick(1100);
            assertTrue(f.pool.isViewer(f.pool.identity(f.pool.snapshot(tv, 1100).id(), f.pool.snapshot(tv, 1100).generation()), follower));
        }
    }

    @Test void fifoCapacityWaitingDeduplicatesUpdatedRequestsAndAdmitsCurrentPosition() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
            f.start(first);
            f.update(second, DEFAULTS, 1100, VIEWER);
            f.update(third, DEFAULTS, 1100, VIEWER);
            f.update(second, quality("720p"), 2000, VIEWER);
            f.pool.tick(2000);
            assertTrue(f.work.queued.isEmpty());
            assertEquals("Waiting for stream capacity", f.pool.message(second));
            f.pool.retain(new HashSet<>(Arrays.asList(second, third)));
            f.pool.tick(2000);
            assertEquals(1, f.work.queued.size());
            f.work.next();
            assertEquals(second, f.starts.get(1).televisionId);
            assertEquals(11_000, f.starts.get(1).timeline.positionMs());
            assertEquals(quality("720p").resolution(), f.starts.get(1).display.resolution());
            assertEquals("Waiting for stream capacity", f.pool.message(third));
        }
    }

    @Test void layoutAndMappingDoNotRestartTheWorkingStream() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID();
            f.start(tv);
            long generation = f.pool.snapshot(tv, 1000).generation();
            f.update(tv, DEFAULTS.apply(0, PresentationMode.FILL, PixelMapping.ONE_PIXEL_PER_BLOCK, ResolutionChoice.AUTO), 1100, VIEWER);
            f.pool.tick(1100);
            assertTrue(f.work.queued.isEmpty());
            assertEquals(generation, f.pool.snapshot(tv, 1100).generation());
        }
    }

    @Test void queuedStartUsesLatestSharedPositionWhenTheWorkerBegins() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID();
            f.update(tv, DEFAULTS, 1000, VIEWER);
            f.pool.tick(1000);
            f.update(tv, DEFAULTS, 2500, VIEWER);
            f.work.next();
            assertEquals(11_500, f.starts.get(0).timeline.positionMs());
            assertEquals(2500, f.starts.get(0).timeline.serverEpochMs());
            assertEquals(11_500, f.pool.snapshot(tv, 2500).positionMs());
        }
    }

    @Test void failedReplacementPreservesWorkingStreamAndDoesNotRetryEveryTick() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID();
            f.start(tv);
            long generation = f.pool.snapshot(tv, 1000).generation();
            f.failStart = true;
            f.update(tv, quality("720p"), 1100, VIEWER);
            f.pool.tick(1100); f.work.next();
            assertTrue(f.pool.snapshot(tv, 1100).transcoding());
            assertEquals(generation, f.pool.snapshot(tv, 1100).generation());
            assertTrue(f.pool.message(tv).startsWith("Unable to prepare"));
            for (long now = 1200; now < 1600; now += 100) {
                f.update(tv, quality("720p"), now, VIEWER); f.pool.tick(now);
            }
            assertTrue(f.work.queued.isEmpty());
        }
    }

    @Test void changingResolutionDuringReplacementCancelsOnlyTheReplacement() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID();
            f.start(tv);
            long generation = f.pool.snapshot(tv, 1000).generation();
            f.update(tv, quality("720p"), 1100, VIEWER); f.pool.tick(1100);
            f.duringStart = () -> {
                try { f.update(tv, quality("1080p"), 1200, VIEWER); }
                catch (IOException error) { throw new RuntimeException(error); }
            };
            f.work.next();
            assertTrue(f.pool.snapshot(tv, 1200).transcoding(), "A superseded replacement must not retire working media");
            assertEquals(generation, f.pool.snapshot(tv, 1200).generation());
        }
    }

    @Test void viewerLossDuringReplacementPreservesTheExistingGracePeriod() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID();
            f.start(tv);
            f.update(tv, quality("720p"), 1100, VIEWER); f.pool.tick(1100);
            f.update(tv, quality("720p"), 1200);
            f.work.next();
            assertTrue(f.pool.snapshot(tv, 1200).transcoding());
            f.pool.tick(2199); assertTrue(f.pool.snapshot(tv, 2199).transcoding());
            f.pool.tick(2200); assertFalse(f.pool.snapshot(tv, 2200).transcoding());
        }
    }

    @Test void rejectedWorkReleasesReservationAndLeavesWorkingMediaUntouched() throws Exception {
        for (int rejection = 1; rejection <= 3; rejection++) {
            try (Fixture f = new Fixture(2)) {
                UUID tv = UUID.randomUUID(), sibling = UUID.randomUUID();
                f.start(tv);
                long generation = f.pool.snapshot(tv, 1000).generation();
                f.work.rejectMode = rejection;
                f.update(tv, quality("720p"), 1100, VIEWER);
                assertDoesNotThrow(() -> f.pool.tick(1100));
                assertTrue(f.pool.message(tv).startsWith("Unable to prepare"));
                assertEquals(generation, f.pool.snapshot(tv, 1100).generation());
                assertTrue(f.pool.snapshot(tv, 1100).transcoding());
                assertEquals(0, f.pool.pendingStarts());
                assertTrue(f.work.queued.isEmpty());
                f.work.rejectMode = 0;
                f.update(sibling, DEFAULTS, 1200, VIEWER); f.pool.tick(1200); f.work.next();
                assertTrue(f.pool.snapshot(sibling, 1200).transcoding());
                // An explicit new choice retries after the error, without a stuck pending flag.
                f.update(tv, quality("1080p"), 1300, VIEWER); f.pool.tick(1300); f.work.next();
                assertTrue(f.pool.snapshot(tv, 1300).generation() > generation);
            }
        }
    }

    @Test void removalAndShutdownCancelQueuedWorkBeforeMediaPreparation() throws Exception {
        for (boolean shutdown : new boolean[] {false, true}) {
            try (Fixture f = new Fixture(1)) {
                UUID tv = UUID.randomUUID();
                f.update(tv, DEFAULTS, 1000, VIEWER); f.pool.tick(1000);
                if (shutdown) f.pool.close();
                else f.pool.retain(java.util.Collections.emptySet());
                f.work.next();
                assertTrue(f.starts.isEmpty());
                assertEquals(0, f.pool.pendingStarts());
                assertEquals(0, f.pool.activeStreamCount());
            }
        }
    }

    @Test void aChangedSharedTimelineCannotAuthorizeTheOldOrRelabelledStream() throws Exception {
        try (Fixture f = new Fixture(1)) {
            UUID tv = UUID.randomUUID(); f.start(tv);
            VideoSessionCoordinator.Snapshot stream = f.pool.snapshot(tv, 1000);
            stonytark.cinemarr.core.protocol.VideoStreamIdentity identity = f.pool.identity(stream.id(), stream.generation());
            assertNotNull(identity); assertTrue(f.pool.isViewer(identity, VIEWER));
            f.timeline.seek("party", 90_000, 1200);
            f.update(tv, DEFAULTS, 1200, VIEWER);
            assertFalse(f.pool.isViewer(identity, VIEWER));
            VideoSessionCoordinator.Snapshot timeline = f.timeline.snapshot("party", 1200);
            assertFalse(f.pool.isViewer(new stonytark.cinemarr.core.protocol.VideoStreamIdentity(
                    timeline.id(), timeline.generation(), stream.id(), stream.generation()), VIEWER));
            f.pool.tick(1200); f.work.next();
            VideoSessionCoordinator.Snapshot replacement = f.pool.snapshot(tv, 1200);
            stonytark.cinemarr.core.protocol.VideoStreamIdentity next = f.pool.identity(replacement.id(), replacement.generation());
            assertNotNull(next); assertTrue(f.pool.isViewer(next, VIEWER));
            assertEquals(timeline.id(), next.timelineId());
            assertEquals(timeline.generation(), next.timelineGeneration());
        }
    }
}
