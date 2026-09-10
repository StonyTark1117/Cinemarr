package stonytark.cinemarr.core.client;

/** Serializes decoded-result publication with reset and permanent retirement. */
public final class DecodeCompletionGuard {
    private long epoch;
    private boolean closed;

    public synchronized long epoch() { return epoch; }

    public synchronized boolean publish(long submittedEpoch, Runnable completion) {
        if (closed || submittedEpoch != epoch) return false;
        completion.run();
        return true;
    }

    public synchronized void reset(Runnable clearResults) {
        epoch++;
        clearResults.run();
    }

    public synchronized void close(Runnable clearResults) {
        closed = true;
        reset(clearResults);
    }
}
