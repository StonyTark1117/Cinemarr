package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.library.LibraryRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loader-neutral asynchronous Plex connection state with bounded automatic retry. */
public final class PlexConnectionLifecycle implements AutoCloseable {
    public enum State { DISABLED, CONNECTING, READY, DEGRADED }
    public interface MainThread { void execute(Runnable action); }
    public interface ReadyHandler { void install(Connection connection) throws Exception; }
    public interface FailureHandler { void failed(String redactedMessage, long retryDelayMs); }
    public static final long RETRY_DELAY_MS = 30_000L;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Cinemarr Plex connector"); thread.setDaemon(true); return thread;
    });
    private State state = State.DISABLED;
    private String url = "";
    private String token = "";
    private List<LibraryRule> rules = Collections.emptyList();
    private MainThread mainThread;
    private ReadyHandler readyHandler;
    private FailureHandler failureHandler;
    private String lastFailure = "";
    private long retryAtMs;
    private long generation;
    private boolean closed;

    public synchronized void configure(String url, String token, List<LibraryRule> rules,
                                       MainThread mainThread, ReadyHandler readyHandler,
                                       FailureHandler failureHandler) {
        if (closed) throw new IllegalStateException("Plex lifecycle is closed");
        this.url = url == null ? "" : url;
        this.token = token == null ? "" : token.trim();
        this.rules = rules == null ? Collections.<LibraryRule>emptyList()
                : Collections.unmodifiableList(new ArrayList<LibraryRule>(rules));
        this.mainThread = mainThread; this.readyHandler = readyHandler; this.failureHandler = failureHandler;
        this.lastFailure = ""; this.retryAtMs = 0L; generation++;
        if (this.rules.isEmpty() || this.token.isEmpty()) { state = State.DISABLED; return; }
        begin(false);
    }

    public synchronized void tick(long nowMs) {
        if (state == State.DEGRADED && nowMs >= retryAtMs) begin(false);
    }

    public synchronized boolean retry() {
        if (state == State.DISABLED || closed) return false;
        if (state == State.CONNECTING || state == State.READY) return true;
        begin(true); return true;
    }

    public synchronized State state() { return state; }
    public synchronized String lastFailure() { return lastFailure; }
    public synchronized long retryInMs(long nowMs) { return state == State.DEGRADED ? Math.max(0L, retryAtMs - nowMs) : 0L; }

    private synchronized void begin(boolean requested) {
        if (closed || rules.isEmpty() || token.isEmpty()) { state = State.DISABLED; return; }
        state = State.CONNECTING; retryAtMs = 0L;
        final long attempt = ++generation;
        final String attemptUrl = url, attemptToken = token;
        final List<LibraryRule> attemptRules = rules;
        worker.execute(() -> {
            Connection connection = null; Throwable failure = null;
            try {
                PlexVideoService service = new PlexVideoService(attemptUrl, attemptToken);
                connection = new Connection(service, service.resolveLibraries(attemptRules), requested);
            } catch (Throwable error) { failure = error; }
            final Connection result = connection; final Throwable problem = failure;
            MainThread scheduler;
            synchronized (PlexConnectionLifecycle.this) { scheduler = mainThread; }
            if (scheduler != null) scheduler.execute(() -> finish(attempt, result, problem));
        });
    }

    private synchronized void finish(long attempt, Connection connection, Throwable failure) {
        if (closed || attempt != generation) return;
        if (failure == null) {
            try { readyHandler.install(connection); state = State.READY; lastFailure = ""; retryAtMs = 0L; return; }
            catch (Throwable installFailure) { failure = installFailure; }
        }
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        state = State.DEGRADED;
        lastFailure = SecretRedactor.message(cause, token, url);
        retryAtMs = System.currentTimeMillis() + RETRY_DELAY_MS;
        if (failureHandler != null) failureHandler.failed(lastFailure, RETRY_DELAY_MS);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; generation++; state = State.DISABLED; retryAtMs = 0L;
        url = ""; token = ""; rules = Collections.emptyList(); mainThread = null; readyHandler = null; failureHandler = null;
        worker.shutdownNow();
    }

    public static final class Connection {
        private final PlexVideoService service;
        private final List<PlexVideoService.ResolvedLibrary> libraries;
        private final boolean requested;
        private Connection(PlexVideoService service, List<PlexVideoService.ResolvedLibrary> libraries, boolean requested) {
            this.service = service; this.libraries = Collections.unmodifiableList(new ArrayList<PlexVideoService.ResolvedLibrary>(libraries)); this.requested = requested;
        }
        public PlexVideoService service() { return service; }
        public List<PlexVideoService.ResolvedLibrary> libraries() { return libraries; }
        public boolean requested() { return requested; }
    }
}
