package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionLifecycleTest {
    @Test void disconnectDuringJoinCleanupCannotSendHello() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        Object connection = new Object();
        ClientConnectionLifecycle[] lifecycle = new ClientConnectionLifecycle[1];
        lifecycle[0] = new ClientConnectionLifecycle(tasks::add,
                () -> lifecycle[0].disconnected(connection));
        lifecycle[0].joined(connection, () -> fail("Transport closed during reset"));
        while (!tasks.isEmpty()) tasks.remove().run();
        assertFalse(lifecycle[0].isActive(connection));
        assertFalse(lifecycle[0].prepareTick(connection));
    }

    @Test void disconnectInvalidatesPacketsAndTheNextTickBeforeQueuedCleanup() throws Exception {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> calls = new ArrayList<>();
        Thread clientThread = Thread.currentThread();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(tasks::add, () -> {
            assertSame(clientThread, Thread.currentThread());
            calls.add("reset");
        });
        Object connection = new Object();
        lifecycle.joined(connection, () -> calls.add("hello")); tasks.remove().run();
        assertTrue(lifecycle.prepareTick(connection));
        Thread network = new Thread(() -> lifecycle.disconnected(connection));
        network.start(); network.join();
        assertFalse(lifecycle.isActive(connection), "Queued packets cannot revive the disconnected stream");
        assertEquals(Arrays.asList("reset", "hello"), calls, "No native cleanup on the network thread");
        if (lifecycle.prepareTick(connection)) fail("Media polled a segment after disconnect");
        assertEquals(Arrays.asList("reset", "hello", "reset"), calls);
        tasks.remove().run();
        assertFalse(lifecycle.prepareTick(null));
        assertEquals(3, calls.size(), "Queued disconnect must not reset twice");
    }

    @Test void detachedTransportResetsBeforeItsDisconnectCallbackArrives() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> calls = new ArrayList<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(tasks::add, () -> calls.add("reset"));
        Object connection = new Object();
        lifecycle.joined(connection, () -> calls.add("hello")); tasks.remove().run();
        assertFalse(lifecycle.prepareTick(null));
        assertFalse(lifecycle.isActive(connection));
        lifecycle.disconnected(connection); tasks.remove().run();
        assertEquals(Arrays.asList("reset", "hello", "reset"), calls);
    }

    @Test void oldPacketsAndQueuedDisconnectCannotTouchReplacementMedia() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> calls = new ArrayList<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(tasks::add, () -> calls.add("reset"));
        Object first = new Object(), second = new Object();
        lifecycle.joined(first, () -> calls.add("first")); tasks.remove().run();
        lifecycle.disconnected(first);
        lifecycle.joined(second, () -> calls.add("second"));
        assertFalse(lifecycle.prepareTick(second));
        assertFalse(lifecycle.isActive(first));
        while (!tasks.isEmpty()) tasks.remove().run();
        assertTrue(lifecycle.prepareTick(second));
        lifecycle.disconnected(first); tasks.remove().run();
        assertTrue(lifecycle.isActive(second));
        assertFalse(lifecycle.isActive(first));
        assertEquals(Arrays.asList("reset", "first", "reset", "reset", "second"), calls);
    }

    @Test void disconnectCancelsAJoinStillWaitingForTheClientExecutor() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> calls = new ArrayList<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(tasks::add, () -> calls.add("reset"));
        Object connection = new Object();
        lifecycle.joined(connection, () -> calls.add("hello"));
        lifecycle.disconnected(connection);
        while (!tasks.isEmpty()) tasks.remove().run();
        assertTrue(calls.isEmpty(), "A closed connection must never send its delayed hello");
    }

    @Test void networkCallbacksNeverRunCleanupOrHelloBeforeClientExecutorDrains() throws Exception {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> calls = new ArrayList<>();
        Thread clientThread = Thread.currentThread();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(tasks::add, () -> {
            assertSame(clientThread, Thread.currentThread()); calls.add("reset");
        });
        Object connection = new Object();
        Thread network = new Thread(() -> lifecycle.joined(connection, () -> calls.add("hello")));
        network.start(); network.join();
        assertTrue(calls.isEmpty()); tasks.remove().run();
        assertEquals(Arrays.asList("reset", "hello"), calls);
        network = new Thread(() -> lifecycle.disconnected(connection));
        network.start(); network.join();
        assertEquals(2, calls.size()); tasks.remove().run();
        assertEquals(Arrays.asList("reset", "hello", "reset"), calls);
    }

    @Test void lateAndDuplicateDisconnectsCannotResetAReplacementConnection() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        List<String> calls = new ArrayList<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(tasks::add, () -> calls.add("reset"));
        Object first = new Object(), second = new Object();
        lifecycle.joined(first, () -> calls.add("first")); tasks.remove().run();
        lifecycle.joined(second, () -> calls.add("second")); tasks.remove().run();
        lifecycle.disconnected(first); tasks.remove().run();
        lifecycle.joined(second, () -> fail("duplicate hello")); tasks.remove().run();
        assertEquals(Arrays.asList("reset", "first", "reset", "second"), calls);
        lifecycle.disconnected(second); lifecycle.disconnected(second);
        tasks.remove().run(); tasks.remove().run();
        assertEquals(Arrays.asList("reset", "first", "reset", "second", "reset"), calls);
    }

    @Test void disconnectBeforeJoinAndResetFailuresAreNotReportedAsSuccessfulJoins() {
        List<String> calls = new ArrayList<>();
        ClientConnectionLifecycle lifecycle = new ClientConnectionLifecycle(Runnable::run, () -> {
            calls.add("reset"); throw new IllegalStateException("close failed");
        });
        Object connection = new Object();
        lifecycle.disconnected(connection); assertTrue(calls.isEmpty());
        assertThrows(IllegalStateException.class, () -> lifecycle.joined(connection, () -> fail("hello after failed reset")));
        lifecycle.disconnected(connection);
        assertEquals(Arrays.asList("reset"), calls);
    }
}
