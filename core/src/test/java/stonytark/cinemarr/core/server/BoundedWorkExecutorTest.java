package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BoundedWorkExecutorTest {
    @Test void rejectsBeyondWorkerAndQueueCapacity() throws Exception {
        BoundedWorkExecutor executor=new BoundedWorkExecutor(1,1,"bounded-test-");CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            CompletableFuture<String> running=executor.supply(()->{started.countDown();try{release.await();}catch(InterruptedException error){Thread.currentThread().interrupt();}return "running";});
            assertTrue(started.await(2,TimeUnit.SECONDS));CompletableFuture<String> queued=executor.supply(()->"queued");CompletableFuture<String> rejected=executor.supply(()->"rejected");
            ExecutionException failure=assertThrows(ExecutionException.class,rejected::get);assertInstanceOf(BoundedWorkExecutor.WorkQueueFullException.class,failure.getCause());assertEquals(1,executor.queuedTasks());assertEquals(1,executor.rejectedTasks());
            release.countDown();assertEquals("running",running.get(2,TimeUnit.SECONDS));assertEquals("queued",queued.get(2,TimeUnit.SECONDS));
        } finally { release.countDown();executor.close(); }
    }

    @Test void closeRejectsNewWorkAndTaskFailuresStayExceptional() {
        BoundedWorkExecutor executor=new BoundedWorkExecutor(1,1,"closed-test-");
        ExecutionException failure=assertThrows(ExecutionException.class,()->executor.supply(()->{throw new IllegalStateException("boom");}).get());assertInstanceOf(IllegalStateException.class,failure.getCause());
        executor.close();CompletableFuture<Void> rejected=executor.run(()->{});ExecutionException closed=assertThrows(ExecutionException.class,rejected::get);assertInstanceOf(BoundedWorkExecutor.WorkExecutorClosedException.class,closed.getCause());assertTrue(executor.isShutdown());
    }

    @Test void closeCompletesQueuedWorkAsCancelled() throws Exception {
        BoundedWorkExecutor executor=new BoundedWorkExecutor(1,2,"cancel-test-");CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        executor.run(()->{started.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        assertTrue(started.await(2,TimeUnit.SECONDS));CompletableFuture<String> queued=executor.supply(()->"late");executor.close();
        assertThrows(java.util.concurrent.CancellationException.class,queued::get);release.countDown();
    }
}
