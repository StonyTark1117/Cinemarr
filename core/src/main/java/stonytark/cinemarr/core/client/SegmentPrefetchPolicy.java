package stonytark.cinemarr.core.client;

/** Shared count and byte bounds for completed compressed segments awaiting decode. */
public final class SegmentPrefetchPolicy {
    public static final int MAX_READY_SEGMENTS = 16;
    public static final long MAX_READY_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_READY_RETAINED_BYTES = MAX_READY_BYTES + VideoSegmentAssembler.MAX_SEGMENT_BYTES;

    /**
     * Permits another request while the retained queue is below both bounds.
     * The response currently in flight can cross the byte threshold by at most
     * one assembler-bounded segment, reflected by MAX_READY_RETAINED_BYTES.
     */
    public static boolean allowsAnother(int readySegments, long readyBytes) {
        if (readySegments < 0 || readyBytes < 0L) throw new IllegalArgumentException("Invalid segment queue state");
        return readySegments < MAX_READY_SEGMENTS && readyBytes < MAX_READY_BYTES;
    }

    private SegmentPrefetchPolicy() {}
}
