package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionLifecycleTest {
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
