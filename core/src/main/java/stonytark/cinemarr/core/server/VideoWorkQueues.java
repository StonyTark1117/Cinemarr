package stonytark.cinemarr.core.server;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Independent bounded budgets: optional browsing cannot consume playback workers. */
public final class VideoWorkQueues implements AutoCloseable {
    public static final int BROWSE_THREADS = 1;
    public static final int BROWSE_QUEUE_CAPACITY = 16;
    public static final int PLAYBACK_THREADS = 2;
    public static final int PLAYBACK_QUEUE_CAPACITY = 64;
    private final BoundedWorkExecutor browse;
    private final BoundedWorkExecutor playback;

    public VideoWorkQueues(String threadPrefix) {
        browse = new BoundedWorkExecutor(BROWSE_THREADS, BROWSE_QUEUE_CAPACITY, threadPrefix + "browse ");
        playback = new BoundedWorkExecutor(PLAYBACK_THREADS, PLAYBACK_QUEUE_CAPACITY, threadPrefix + "playback ");
    }

    public <T> CompletableFuture<T> browse(Supplier<T> task) { return browse.supply(task); }
    public <T> CompletableFuture<T> supply(Supplier<T> task) { return playback.supply(task); }
    public int queuedTasks() { return playback.queuedTasks(); }
    public int activeTasks() { return playback.activeTasks(); }
    public long rejectedTasks() { return playback.rejectedTasks(); }
    public int browseQueuedTasks() { return browse.queuedTasks(); }
    public int browseActiveTasks() { return browse.activeTasks(); }
    public long browseRejectedTasks() { return browse.rejectedTasks(); }
    public String browseDiagnostics() {
        return "; browseQueued=" + browseQueuedTasks() + "; browseActive=" + browseActiveTasks()
                + "; browseRejected=" + browseRejectedTasks();
    }

    @Override public void close() { browse.close(); playback.close(); }
}
