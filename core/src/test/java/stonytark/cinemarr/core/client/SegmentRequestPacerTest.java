package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;
import static org.junit.jupiter.api.Assertions.*;

class SegmentRequestPacerTest {
    @Test void multipleTvsShareAFairBudgetBelowTheServerLimit() {
        SegmentRequestPacer pacer = new SegmentRequestPacer();
        List<Long> sent = new ArrayList<Long>();
        List<Integer> turns = new ArrayList<Integer>();
        for (int tv = 0; tv < 4; tv++) {
            final int owner = tv;
            LongConsumer[] next = new LongConsumer[1];
            next[0] = now -> { sent.add(now); turns.add(owner); pacer.enqueue(owner, next[0]); };
            pacer.enqueue(owner, next[0]);
        }
        for (long now = 0; now < 10_000; now++) {
            pacer.tick(now);
            assertEquals(4, pacer.pendingStreams(), "One queued window per TV, not one per attempted send");
        }
        for (int index = 0; index < turns.size(); index++) assertEquals(index % 4, turns.get(index).intValue());
        for (long start : sent) {
            int count = 0;
            for (long time : sent) if (time >= start && time < start + 1000) count++;
            assertTrue(count <= 36, "A rolling second must leave headroom below the server's 40 requests");
        }
        assertTrue(sent.size() >= 300, "Pacing must still make steady progress");
    }

    @Test void replacementAndCancellationDoNotRefillTheConnectionBudget() {
        SegmentRequestPacer pacer = new SegmentRequestPacer();
        List<String> sent = new ArrayList<String>();
        for (int i = 0; i < 4; i++) pacer.enqueue(i, now -> sent.add("initial"));
        pacer.tick(1000);
        pacer.enqueue("old", now -> fail("Retired generation dispatched"));
        pacer.cancel("old");
        pacer.enqueue("new", now -> sent.add("new"));
        pacer.tick(1000);
        assertEquals(4, sent.size());
        pacer.tick(1032);
        assertEquals("new", sent.get(4));
        assertEquals(0, pacer.pendingStreams());
    }

    @Test void queuedRetryIsReplacedInPlaceAndTimestampStartsAtDispatch() {
        SegmentRequestPacer pacer = new SegmentRequestPacer();
        List<String> sent = new ArrayList<String>();
        pacer.enqueue("first", now -> fail("Superseded window dispatched"));
        pacer.enqueue("second", now -> sent.add("second:" + now));
        pacer.enqueue("first", now -> sent.add("first:" + now));
        assertTrue(sent.isEmpty());
        assertEquals(2, pacer.pendingStreams());
        pacer.tick(5000);
        assertEquals(java.util.Arrays.asList("first:5000", "second:5000"), sent);
    }

    @Test void delayedAndBackwardTicksCannotCreateUnboundedCatchUpBursts() {
        SegmentRequestPacer pacer = new SegmentRequestPacer();
        List<Long> sent = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) pacer.enqueue(i, sent::add);
        pacer.tick(1000);
        assertEquals(4, sent.size());
        pacer.tick(900); pacer.tick(950); pacer.tick(1000);
        assertEquals(4, sent.size(), "A clock rollback cannot mint tokens");
        pacer.tick(100_000);
        assertEquals(8, sent.size(), "Only the bounded burst is available after a stall");
        pacer.tick(100_000);
        assertEquals(8, sent.size());
        pacer.reset();
        assertEquals(0, pacer.pendingStreams());
        pacer.tick(100_001);
        assertEquals(8, sent.size(), "Disconnect must discard queued callbacks");
    }
}
