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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoSessionCoordinatorResponsivenessTest {
    private static VideoMediaItem movie() {
        return new VideoMediaItem(MediaKind.MOVIE, "1", "Movie", "", "PG", 0, 90_000);
    }

    private static void await(CountDownLatch release) throws IOException {
        try {
            if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("held media operation timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("held media operation interrupted", interrupted);
        }
    }

    @Test void serverThreadQueriesStayResponsiveWhilePlexStarts() throws Exception {
        CountDownLatch starting = new CountDownLatch(1), release = new CountDownLatch(1);
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0, (id, generation, item, offset) -> {
            starting.countDown();
            await(release);
            return () -> {};
        });
        UUID viewer = UUID.randomUUID();
        coordinator.tune(UUID.randomUUID(), "party");
        coordinator.viewerEntered("party", viewer);
        VideoSessionCoordinator.Snapshot original = coordinator.snapshot("party", 0);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> start = threads.submit(() -> coordinator.play("party", movie(), 0, 1_000));
            assertTrue(starting.await(5, TimeUnit.SECONDS));
            threads.submit(() -> {
                assertTrue(coordinator.isViewer(original.id(), original.generation(), viewer));
                assertEquals(0, coordinator.activeStreamCount());
                assertNull(coordinator.snapshot("party", 2_000).item());
                assertEquals(1, coordinator.sessionNames().size());
            }).get(2, TimeUnit.SECONDS);
            release.countDown();
            start.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
            coordinator.close();
        }
    }

    @Test void stopCancelsAHeldStartWithoutWaitingOrPublishingItsLateResult() throws Exception {
        CountDownLatch starting = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0, (id, generation, item, offset) -> {
            starting.countDown();
            await(release);
            return closed::incrementAndGet;
        });
        coordinator.tune(UUID.randomUUID(), "party");
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> start = threads.submit(() -> {
                try {
                    coordinator.play("party", movie(), 0, 1_000);
                } catch (IllegalStateException cancelled) {
                    // Cancellation must close the returned handle, not publish it.
                }
                return null;
            });
            assertTrue(starting.await(5, TimeUnit.SECONDS));
            VideoSessionCoordinator.Snapshot stopped = threads.submit(() -> coordinator.stop("party", 2_000))
                    .get(2, TimeUnit.SECONDS);
            assertNull(stopped.item());
            release.countDown();
            start.get(5, TimeUnit.SECONDS);
            assertNull(coordinator.snapshot("party", 3_000).item());
            assertFalse(coordinator.snapshot("party", 3_000).transcoding());
            assertEquals(1, closed.get());
        } finally {
            release.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
            coordinator.close();
        }
    }

    @Test void serverThreadQueriesStayResponsiveWhileAPlexHandleCloses() throws Exception {
        CountDownLatch closing = new CountDownLatch(1), release = new CountDownLatch(1);
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0,
                (id, generation, item, offset) -> () -> {
                    closing.countDown();
                    await(release);
                });
        coordinator.tune(UUID.randomUUID(), "party");
        coordinator.play("party", movie(), 0, 1_000);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> stop = threads.submit(() -> coordinator.stop("party", 2_000));
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            threads.submit(() -> {
                coordinator.activeStreamCount();
                coordinator.snapshot("party", 3_000);
                coordinator.sessionNames();
            }).get(2, TimeUnit.SECONDS);
            release.countDown();
            stop.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
            coordinator.close();
        }
    }
}
