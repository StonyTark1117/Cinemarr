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
    private final BoundedWorkExecutor timelines;

    public VideoWorkQueues(String threadPrefix) {
        timelines = new BoundedWorkExecutor(1, 16, threadPrefix + "timeline ");
        browse = new BoundedWorkExecutor(BROWSE_THREADS, BROWSE_QUEUE_CAPACITY, threadPrefix + "browse ");
        playback = new BoundedWorkExecutor(PLAYBACK_THREADS, PLAYBACK_QUEUE_CAPACITY, threadPrefix + "playback ");
    }

    public <T> CompletableFuture<T> browse(Supplier<T> task) { return browse.supply(task); }
    public <T> CompletableFuture<T> supply(Supplier<T> task) { return playback.supply(task); }
    public <T> CompletableFuture<T> timeline(Supplier<T> task) { return timelines.supply(task); }
    public int timelineQueuedTasks() { return timelines.queuedTasks(); }
    public int timelineActiveTasks() { return timelines.activeTasks(); }
    public long timelineRejectedTasks() { return timelines.rejectedTasks(); }
    public int queuedTasks() { return playback.queuedTasks(); }
    public int activeTasks() { return playback.activeTasks(); }
    public long rejectedTasks() { return playback.rejectedTasks(); }
    public int browseQueuedTasks() { return browse.queuedTasks(); }
    public int browseActiveTasks() { return browse.activeTasks(); }
    public long browseRejectedTasks() { return browse.rejectedTasks(); }
    public String browseDiagnostics() {
        return "; browseQueued=" + browseQueuedTasks() + "; browseActive=" + browseActiveTasks()
                + "; browseRejected=" + browseRejectedTasks()
                + "; timelineQueued=" + timelineQueuedTasks() + "; timelineActive=" + timelineActiveTasks()
                + "; timelineRejected=" + timelineRejectedTasks();
    }

    @Override public void close() { timelines.close(); browse.close(); playback.close(); }
}
