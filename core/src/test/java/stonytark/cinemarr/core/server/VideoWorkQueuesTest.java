package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class VideoWorkQueuesTest {
    private static String hold(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        return "released";
    }

    @Test void saturatedBrowseCannotConsumePlaybackCapacity() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (VideoWorkQueues queues = new VideoWorkQueues("isolation-test-")) {
            queues.browse(() -> hold(started, release));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            List<CompletableFuture<String>> pending = new ArrayList<>();
            for (int i = 0; i < 16; i++) pending.add(queues.browse(() -> "browse"));
            ExecutionException error = assertThrows(ExecutionException.class, () -> queues.browse(() -> "excess").get());
            assertInstanceOf(BoundedWorkExecutor.WorkQueueFullException.class, error.getCause());
            assertEquals(16, queues.browseQueuedTasks()); assertEquals(1, queues.browseActiveTasks());
            assertEquals(1, queues.browseRejectedTasks()); assertEquals(0, queues.rejectedTasks());
            assertEquals("segment", queues.supply(() -> "segment").get(2, TimeUnit.SECONDS));
            assertTrue(pending.stream().noneMatch(CompletableFuture::isDone));
            release.countDown();
            for (CompletableFuture<String> task : pending) assertEquals("browse", task.get(2, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }

    @Test void closingCancelsBothBacklogsAndRejectsNewWork() throws Exception {
        CountDownLatch started = new CountDownLatch(3), release = new CountDownLatch(1);
        VideoWorkQueues queues = new VideoWorkQueues("close-isolation-test-");
        try {
            CompletableFuture<String> browse = queues.browse(() -> hold(started, release));
            CompletableFuture<String> first = queues.supply(() -> hold(started, release));
            CompletableFuture<String> second = queues.supply(() -> hold(started, release));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            CompletableFuture<String> browsePending = queues.browse(() -> "never");
            CompletableFuture<String> playbackPending = queues.supply(() -> "never");
            queues.close(); queues.close();
            assertThrows(java.util.concurrent.CancellationException.class, browsePending::get);
            assertThrows(java.util.concurrent.CancellationException.class, playbackPending::get);
            for (CompletableFuture<String> active : java.util.Arrays.asList(browse, first, second)) active.get(2, TimeUnit.SECONDS);
            assertInstanceOf(BoundedWorkExecutor.WorkExecutorClosedException.class,
                    assertThrows(ExecutionException.class, () -> queues.browse(() -> "late").get()).getCause());
            assertInstanceOf(BoundedWorkExecutor.WorkExecutorClosedException.class,
                    assertThrows(ExecutionException.class, () -> queues.supply(() -> "late").get()).getCause());
            assertEquals(0, queues.queuedTasks()); assertEquals(0, queues.browseQueuedTasks());
        } finally { release.countDown(); queues.close(); }
    }
}
