package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VideoSessionCoordinatorAsyncTest {
    private static VideoMediaItem movie() {
        return new VideoMediaItem(MediaKind.MOVIE, "1", "Movie", "", "PG", 0, 90_000);
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IOException("held operation timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("held operation interrupted", interrupted);
        }
    }

    @Test void asynchronousStopNeverRunsHttpOnTheCallerAndCleanupBacklogIsBounded() throws Exception {
        CountDownLatch closing = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger stops = new AtomicInteger();
        AtomicReference<Thread> closeThread = new AtomicReference<>();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0, (id, generation, item, offset) -> () -> {
            closeThread.set(Thread.currentThread()); closing.countDown(); await(release); stops.incrementAndGet();
        }, true);
        coordinator.tune(UUID.randomUUID(), "party");
        try {
            coordinator.play("party", movie(), 0, 1_000);
            assertNull(coordinator.stop("party", 2_000).item());
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertNotSame(Thread.currentThread(), closeThread.get());
            assertEquals(0, coordinator.activeStreamCount());
            assertEquals(1, coordinator.retiringMedia());
            coordinator.play("party", movie(), 0, 3_000);
            coordinator.stop("party", 4_000);
            assertEquals(2, coordinator.retiringMedia());
            assertThrows(IllegalStateException.class, () -> coordinator.play("party", movie(), 0, 5_000));
            assertEquals(0, coordinator.pendingStarts());
        } finally {
            release.countDown(); coordinator.close();
        }
        assertEquals(2, stops.get());
        assertEquals(0, coordinator.retiringMedia());
    }

    @Test void pendingStartsReserveStreamCapacityAndCancelledHandlesCannotReplaceANewerStart() throws Exception {
        CountDownLatch starting = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger(), stops = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0, (id, generation, item, offset) -> {
            if (starts.incrementAndGet() == 1) { starting.countDown(); await(release); }
            return stops::incrementAndGet;
        }, true);
        coordinator.tune(UUID.randomUUID(), "party");
        coordinator.tune(UUID.randomUUID(), "other");
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            Future<?> original = thread.submit(() -> {
                assertThrows(IllegalStateException.class, () -> coordinator.play("party", movie(), 0, 1_000));
            });
            assertTrue(starting.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> coordinator.play("other", movie(), 0, 1_000));
            assertThrows(IllegalStateException.class, () -> coordinator.play("party", movie(), 0, 1_000));
            assertEquals(1, starts.get());
            coordinator.stop("party", 2_000);
            VideoSessionCoordinator.Snapshot replacement = coordinator.play("party", movie(), 10_000, 3_000);
            assertEquals(3, replacement.generation());
            release.countDown(); original.get(5, TimeUnit.SECONDS);
            assertEquals(replacement.generation(), coordinator.snapshot("party", 3_000).generation());
            assertEquals(10_000, coordinator.snapshot("party", 3_000).positionMs());
            assertEquals(1, coordinator.activeStreamCount());
        } finally {
            release.countDown(); thread.shutdownNow();
            assertTrue(thread.awaitTermination(5, TimeUnit.SECONDS)); coordinator.close();
        }
        assertEquals(2, stops.get());
    }

    @Test void failedStartNeverReusesItsGenerationAndSeekReturnsTheActualCommittedSnapshot() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0, (id, generation, item, offset) -> {
            if (starts.incrementAndGet() == 2) throw new IOException("fixture failure");
            return () -> {};
        }, true);
        coordinator.tune(UUID.randomUUID(), "party");
        try {
            VideoSessionCoordinator.Snapshot first = coordinator.play("party", movie(), 0, 1_000);
            assertThrows(IOException.class, () -> coordinator.play("party", movie(), 0, 2_000, first.generation()));
            assertEquals(first.generation(), coordinator.snapshot("party", 2_000).generation());
            VideoSessionCoordinator.Snapshot seek = coordinator.seek("party", 20_000, 3_000, first.generation());
            assertEquals(3, seek.generation()); assertEquals(20_000, seek.positionMs());
        } finally { coordinator.close(); }
    }

    @Test void shutdownRejectsNewWorkAndDrainsALateUninterruptibleStart() throws Exception {
        CountDownLatch starting = new CountDownLatch(1), release = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        AtomicInteger stops = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0, (id, generation, item, offset) -> {
            starting.countDown();
            // Model bounded HTTP which only returns after its I/O completes,
            // even if the owning worker is interrupted during shutdown.
            boolean done = false;
            while (!done) try { done = release.await(10, TimeUnit.SECONDS); if (!done) throw new IOException("timeout"); }
            catch (InterruptedException ignored) { interrupted.countDown(); }
            return stops::incrementAndGet;
        }, true);
        coordinator.tune(UUID.randomUUID(), "party");
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> start = threads.submit(() -> assertThrows(IllegalStateException.class,
                    () -> coordinator.play("party", movie(), 0, 1_000)));
            assertTrue(starting.await(5, TimeUnit.SECONDS));
            Future<?> shutdown = threads.submit(() -> { coordinator.close(); return null; });
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertEquals(0, coordinator.sessionCount());
            assertThrows(IllegalStateException.class, () -> coordinator.tune(UUID.randomUUID(), "new"));
            assertThrows(IllegalStateException.class, () -> coordinator.play("party", movie(), 0, 2_000));
            assertFalse(shutdown.isDone());
            release.countDown(); start.get(5, TimeUnit.SECONDS); shutdown.get(5, TimeUnit.SECONDS);
            assertEquals(1, stops.get()); assertEquals(0, coordinator.pendingStarts());
            assertEquals(0, coordinator.retiringMedia());
        } finally {
            release.countDown(); threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS)); coordinator.close();
        }
    }

    @Test void cleanupFailuresAreObservableAndShutdownStillDrainsTheOtherHandles() throws Exception {
        AtomicInteger stops = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0, (id, generation, item, offset) -> () -> {
            if (stops.incrementAndGet() == 1) throw new IOException("fixture cleanup failure");
        }, true);
        coordinator.tune(UUID.randomUUID(), "first"); coordinator.tune(UUID.randomUUID(), "second");
        coordinator.play("first", movie(), 0, 1_000); coordinator.play("second", movie(), 0, 1_000);
        assertThrows(IOException.class, coordinator::close);
        assertEquals(2, stops.get()); assertEquals(1, coordinator.closeFailures());
        assertEquals(0, coordinator.retiringMedia()); assertEquals(0, coordinator.activeStreamCount());
        assertThrows(IOException.class, () -> coordinator.tick(2_000));
    }

    @Test void pausedRestoredSessionsDoNotOpenAndImmediatelyCloseATranscode() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0, (id, generation, item, offset) -> {
            starts.incrementAndGet(); return () -> {};
        }, true);
        coordinator.tune(UUID.randomUUID(), "party");
        try {
            VideoSessionCoordinator.Snapshot restored = coordinator.restore("party", movie(), 30_000, true, 1_000);
            assertTrue(coordinator.restart("party", 2_000, restored.generation()).paused());
            assertEquals(0, starts.get());
            coordinator.resume("party", 3_000);
            assertTrue(coordinator.restart("party", 3_000, restored.generation()).transcoding());
            assertEquals(1, starts.get());
        } finally { coordinator.close(); }
    }

    @Test void playbackMetadataRevisionSurvivesPauseButChangesForReplacementAndRestore() throws Exception {
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0,
                (id, generation, item, offset) -> () -> {}, true);
        coordinator.tune(UUID.randomUUID(), "party");
        try {
            VideoSessionCoordinator.Snapshot playing = coordinator.play("party", movie(), 0, 1_000);
            coordinator.pause("party", 2_000);
            VideoSessionCoordinator.Snapshot paused = coordinator.snapshot("party", 2_000);
            assertNotEquals(playing.generation(), paused.generation());
            assertEquals(playing.playbackGeneration(), paused.playbackGeneration());
            coordinator.stop("party", 3_000);
            VideoSessionCoordinator.Snapshot restored = coordinator.restore("party", movie(), 20_000, true, 4_000);
            assertEquals(restored.generation(), restored.playbackGeneration());
            assertNotEquals(playing.playbackGeneration(), restored.playbackGeneration());
        } finally { coordinator.close(); }
    }

    @Test void metadataCompletionCannotMutateASupersededOrClosedSession() throws Exception {
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 0,
                (id, generation, item, offset) -> () -> {}, true);
        coordinator.tune(UUID.randomUUID(), "party");
        AtomicInteger commits = new AtomicInteger();
        VideoSessionCoordinator.Snapshot playing = coordinator.play("party", movie(), 0, 1_000);
        assertTrue(coordinator.applyIfCurrent(playing, commits::incrementAndGet));
        VideoSessionCoordinator.Snapshot stopped = coordinator.stop("party", 2_000);
        assertFalse(coordinator.applyIfCurrent(playing, commits::incrementAndGet));
        assertEquals(1, commits.get());
        coordinator.close();
        assertFalse(coordinator.applyIfCurrent(stopped, commits::incrementAndGet));
        assertEquals(1, commits.get());
    }
}
