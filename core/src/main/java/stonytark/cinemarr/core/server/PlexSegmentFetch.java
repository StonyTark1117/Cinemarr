package stonytark.cinemarr.core.server;

import java.io.IOException;

/** Bounded retry policy for HLS segment files which Plex has listed but has not materialized yet. */
public final class PlexSegmentFetch {
    private static final long[] NOT_READY_DELAYS_MS =
            { 100L, 250L, 500L, 750L, 1_000L, 1_500L, 2_000L, 2_500L, 3_000L };
    private static final long[] TRANSIENT_DELAYS_MS = { 100L, 250L };

    public interface Operation { byte[] fetch() throws IOException; }
    public interface RetryListener { void retry(long delayMs); }
    interface Sleeper { void sleep(long delayMs) throws InterruptedException; }

    public static byte[] fetch(Operation operation, RetryListener listener) throws IOException {
        return fetch(operation, listener, new Sleeper() {
            @Override public void sleep(long delayMs) throws InterruptedException { Thread.sleep(delayMs); }
        });
    }

    static byte[] fetch(Operation operation, RetryListener listener, Sleeper sleeper) throws IOException {
        if (operation == null || listener == null || sleeper == null) throw new IllegalArgumentException("retry arguments");
        int notReadyRetries = 0;
        int transientRetries = 0;
        while (true) {
            try {
                return operation.fetch();
            } catch (IOException failure) {
                long delayMs;
                if (isNotReady(failure) && notReadyRetries < NOT_READY_DELAYS_MS.length) {
                    delayMs = NOT_READY_DELAYS_MS[notReadyRetries++];
                } else if (isTransient(failure) && transientRetries < TRANSIENT_DELAYS_MS.length) {
                    delayMs = TRANSIENT_DELAYS_MS[transientRetries++];
                } else {
                    throw failure;
                }
                listener.retry(delayMs);
                try {
                    sleeper.sleep(delayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for a Plex video segment", interrupted);
                }
            }
        }
    }

    private static boolean isNotReady(IOException failure) {
        return failure instanceof PlexException
                && ((PlexException) failure).kind() == PlexException.Kind.NOT_FOUND;
    }

    private static boolean isTransient(IOException failure) {
        return !(failure instanceof PlexException)
                || ((PlexException) failure).kind() == PlexException.Kind.OFFLINE;
    }

    private PlexSegmentFetch() {}
}
