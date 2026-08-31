package stonytark.cinemarr.core.client;

/**
 * Decides when a bounded segment-chunk window must be acknowledged before the
 * client asks for the next window. Kept in core so modern and legacy clients
 * cannot drift apart on the flow-control contract.
 */
public final class TransferWindowFlow {
    public static Decision afterChunk(int chunkIndex, int windowStart, int requestedChunks,
                                      int totalChunks, boolean segmentComplete) {
        if (chunkIndex < 0 || windowStart < 0 || requestedChunks < 1 || totalChunks < 1
                || chunkIndex >= totalChunks || windowStart >= totalChunks) {
            throw new IllegalArgumentException("Invalid segment transfer window");
        }
        int windowEnd = Math.min(totalChunks, windowStart + requestedChunks);
        if (chunkIndex < windowStart || chunkIndex >= windowEnd) return Decision.NONE;
        if (!segmentComplete && chunkIndex + 1 < windowEnd) return Decision.NONE;
        return new Decision(chunkIndex, segmentComplete || windowEnd >= totalChunks ? -1 : windowEnd);
    }

    public static final class Decision {
        private static final Decision NONE = new Decision(-1, -1);
        private final int receivedThroughChunk;
        private final int nextWindowStart;

        private Decision(int receivedThroughChunk, int nextWindowStart) {
            this.receivedThroughChunk = receivedThroughChunk;
            this.nextWindowStart = nextWindowStart;
        }

        public boolean acknowledgesWindow() { return receivedThroughChunk >= 0; }
        public int receivedThroughChunk() { return receivedThroughChunk; }
        public boolean continuesSegment() { return nextWindowStart >= 0; }
        public int nextWindowStart() { return nextWindowStart; }
    }

    private TransferWindowFlow() {}
}
