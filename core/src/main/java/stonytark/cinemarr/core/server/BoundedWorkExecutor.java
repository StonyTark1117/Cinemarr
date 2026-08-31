package stonytark.cinemarr.core.server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Fixed worker pool with a bounded backlog, explicit rejection, and observable state. */
public final class BoundedWorkExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    public BoundedWorkExecutor(int threads, int queueCapacity, final String threadPrefix) {
        if (threads <= 0 || queueCapacity <= 0 || threadPrefix == null || threadPrefix.isEmpty()) {
            throw new IllegalArgumentException("worker limits");
        }
        final AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadPrefix + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> supply(final Supplier<T> supplier) {
        if (supplier == null) throw new IllegalArgumentException("supplier");
        final Work<T> work=new Work<T>(supplier);
        if(closed.get()){work.future.completeExceptionally(new WorkExecutorClosedException("Cinemarr background work executor is closed"));return work.future;}
        try {
            executor.execute(work);
        } catch (RejectedExecutionException full) {
            rejected.incrementAndGet();
            if(closed.get()||executor.isShutdown())work.future.completeExceptionally(new WorkExecutorClosedException("Cinemarr background work executor is closed",full));
            else work.future.completeExceptionally(new WorkQueueFullException("Cinemarr background work queue is full", full));
        }
        return work.future;
    }

    public CompletableFuture<Void> run(final Runnable task) {
        if (task == null) throw new IllegalArgumentException("task");
        return supply(() -> { task.run(); return null; });
    }

    public int queuedTasks() { return executor.getQueue().size(); }
    public int activeTasks() { return executor.getActiveCount(); }
    public long rejectedTasks() { return rejected.get(); }
    public boolean isShutdown() { return executor.isShutdown(); }

    @Override public void close() { if(!closed.compareAndSet(false,true))return;List<Runnable> pending=executor.shutdownNow();for(Runnable task:pending)if(task instanceof Work<?>)((Work<?>)task).cancel(); }

    private static final class Work<T> implements Runnable {
        private final Supplier<T> supplier;private final CompletableFuture<T> future=new CompletableFuture<T>();
        private Work(Supplier<T> supplier){this.supplier=supplier;}
        @Override public void run(){if(future.isDone())return;try{future.complete(supplier.get());}catch(Throwable error){future.completeExceptionally(error);}}
        private void cancel(){future.completeExceptionally(new java.util.concurrent.CancellationException("Cinemarr background work was cancelled during shutdown"));}
    }

    public static final class WorkQueueFullException extends RuntimeException {
        public WorkQueueFullException(String message, Throwable cause) { super(message, cause); }
    }
    public static final class WorkExecutorClosedException extends RuntimeException {
        public WorkExecutorClosedException(String message){super(message);}
        public WorkExecutorClosedException(String message,Throwable cause){super(message,cause);}
    }
}
