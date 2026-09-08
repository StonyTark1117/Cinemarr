package stonytark.cinemarr.core.network;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class BoundedPacketInboxTest {
    @Test void peerLimitsDoNotConsumeAnotherPeersBudgetAndDrainIsFair() {
        BoundedPacketInbox<String, String> inbox = new BoundedPacketInbox<>(6, 60, 3, 30);
        for (int i=0; i<3; i++) assertTrue(inbox.offer("a", "a"+i, 10));
        assertFalse(inbox.offer("a", "excess", 0));
        for (int i=0; i<3; i++) assertTrue(inbox.offer("b", "b"+i, 10));
        assertFalse(inbox.offer("c", "excess", 0));
        List<String> seen = new ArrayList<>();
        assertEquals(4, inbox.drain(4, seen::add));
        assertEquals(Arrays.asList("a0", "b0", "a1", "b1"), seen);
        assertEquals(2, inbox.size()); assertEquals(20, inbox.retainedBytes());
        assertEquals(2, inbox.drain(4, seen::add));
        assertEquals(0, inbox.owners()); assertEquals(0, inbox.retainedBytes());
        assertEquals(2, inbox.rejectedPackets());
    }

    @Test void byteLimitsAndOverflowRejectWithoutRetainingOwners() {
        BoundedPacketInbox<String, String> inbox = new BoundedPacketInbox<>(8, 100, 4, 60);
        assertTrue(inbox.offer("a", "first", 60));
        assertFalse(inbox.offer("a", "peer-overflow", 1));
        assertFalse(inbox.offer("b", "global-overflow", 41));
        assertFalse(inbox.offer("b", "integer-overflow", Long.MAX_VALUE));
        assertEquals(1, inbox.owners()); assertEquals(1, inbox.size());
        assertEquals(60, inbox.retainedBytes());
        assertTrue(inbox.offer("b", "fits", 40));
        inbox.remove("a"); assertEquals(40, inbox.retainedBytes());
        inbox.remove("a"); inbox.clear();
        assertEquals(0, inbox.size()); assertEquals(0, inbox.owners()); assertEquals(0, inbox.retainedBytes());
    }

    @Test void callbackTeardownExceptionAndReentrantRefillPreserveAccounting() {
        BoundedPacketInbox<String, String> inbox = new BoundedPacketInbox<>(8, 100, 4, 60);
        inbox.offer("a", "first", 20); inbox.offer("b", "next", 20);
        assertEquals(1, inbox.drain(8, value -> inbox.clear()));
        inbox.offer("a", "first", 20); inbox.offer("b", "next", 20);
        assertThrows(IllegalStateException.class, () -> inbox.drain(8, value -> { throw new IllegalStateException(); }));
        assertEquals(1, inbox.size()); assertEquals(20, inbox.retainedBytes());
        assertEquals(8, inbox.drain(8, value -> inbox.offer("b", "refill", 20)));
        assertEquals(1, inbox.size()); assertEquals(20, inbox.retainedBytes());
        inbox.clear();
    }

    @Test void concurrentFloodStaysWithinGlobalBudgets() throws Exception {
        BoundedPacketInbox<Integer, Integer> inbox = new BoundedPacketInbox<>(128, 1024, 32, 256);
        CountDownLatch start = new CountDownLatch(1), done = new CountDownLatch(8);
        for (int owner=0; owner<8; owner++) {
            final int key = owner;
            new Thread(() -> { try { start.await(); for(int i=0;i<1000;i++) inbox.offer(key,i,8); }
                               catch(InterruptedException error) { Thread.currentThread().interrupt(); }
                               finally { done.countDown(); } }).start();
        }
        start.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(128, inbox.size()); assertEquals(1024, inbox.retainedBytes());
        assertEquals(8000-128, inbox.rejectedPackets());
        assertEquals(128, inbox.drain(1000, value -> {}));
        assertEquals(0, inbox.owners()); assertEquals(0, inbox.retainedBytes());
    }

    @Test void invalidArgumentsCannotCorruptAccounting() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedPacketInbox<>(1, 1, 2, 1));
        BoundedPacketInbox<String, String> inbox = new BoundedPacketInbox<>(8, 100, 4, 60);
        assertThrows(IllegalArgumentException.class, () -> inbox.offer("a", "x", -1));
        assertThrows(IllegalArgumentException.class, () -> inbox.offer(null, "x", 1));
        assertThrows(IllegalArgumentException.class, () -> inbox.offer("a", null, 1));
        assertThrows(IllegalArgumentException.class, () -> inbox.drain(-1, value -> {}));
        assertEquals(0, inbox.size()); assertEquals(0, inbox.retainedBytes());
    }

    @Test void repeatedDisconnectDoesNotRetainPeerStateOrClearAnotherConnection() {
        BoundedPacketInbox<Object, String> inbox = new BoundedPacketInbox<>(8, 100, 4, 60);
        Object survivor = new Object(); inbox.offer(survivor, "survivor", 10);
        for (int i=0; i<1000; i++) {
            Object departed = new Object();
            assertTrue(inbox.offer(departed, "departing", 20));
            inbox.remove(departed);
            assertEquals(1, inbox.owners()); assertEquals(10, inbox.retainedBytes());
        }
        List<String> seen = new ArrayList<>(); inbox.drain(8, seen::add);
        assertEquals(Arrays.asList("survivor"), seen);
        assertEquals(0, inbox.owners());
    }
}
