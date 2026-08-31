package stonytark.cinemarr.core.client;

/** Classifies backend exhaustion without counting a normal terminal drain as an underrun. */
public final class AudioUnderrunPolicy {
    private AudioUnderrunPolicy() {}

    public static int additionalUnderruns(int previousStarvations, int currentStarvations,
                                          boolean channelStopped, boolean paused,
                                          boolean terminalInputDrained) {
        if (paused || terminalInputDrained) return 0;
        int starvationDelta = Math.max(0, currentStarvations - Math.max(0, previousStarvations));
        // A stopped channel commonly reports the same starvation through both
        // counters. Count that physical event once rather than twice.
        return starvationDelta > 0 ? starvationDelta : channelStopped ? 1 : 0;
    }
}
