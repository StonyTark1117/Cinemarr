package stonytark.cinemarr.core.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;

/** One fair request budget shared by every TV on a client connection. */
public final class SegmentRequestPacer {
    public static final int REQUESTS_PER_SECOND = 32;
    public static final int MAX_BURST = 4;
    private final Map<Object, LongConsumer> pending = new LinkedHashMap<Object, LongConsumer>();
    private long lastTick = Long.MIN_VALUE;
    private long credit = MAX_BURST * 1000L;

    /** Replaces a stream's queued window without changing its FIFO position. */
    public void enqueue(Object stream, LongConsumer dispatch) {
        if (stream == null || dispatch == null) throw new IllegalArgumentException("Stream and dispatch are required");
        pending.put(stream, dispatch);
    }

    public void cancel(Object stream) { pending.remove(stream); }

    public void tick(long now) {
        if (lastTick != Long.MIN_VALUE && now < lastTick) return;
        if (lastTick != Long.MIN_VALUE && now > lastTick) {
            long elapsed = Math.min(1000L, now - lastTick);
            credit = Math.min(MAX_BURST * 1000L, credit + elapsed * REQUESTS_PER_SECOND);
        }
        lastTick = now;
        while (credit >= 1000L && !pending.isEmpty()) {
            Map.Entry<Object, LongConsumer> next = pending.entrySet().iterator().next();
            LongConsumer dispatch = next.getValue();
            pending.remove(next.getKey());
            credit -= 1000L;
            dispatch.accept(now);
        }
    }

    public int pendingStreams() { return pending.size(); }

    /** Only a disconnected client may reset the connection-wide budget. */
    public void reset() { pending.clear(); lastTick = Long.MIN_VALUE; credit = MAX_BURST * 1000L; }
}
