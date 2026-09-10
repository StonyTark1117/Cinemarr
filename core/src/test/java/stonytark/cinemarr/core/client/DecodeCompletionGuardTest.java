package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DecodeCompletionGuardTest {
    @Test void retiredDecodeCannotPublishBuffersOrCancellationFailures() {
        DecodeCompletionGuard guard = new DecodeCompletionGuard();
        long old = guard.epoch();
        List<Object> buffers = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        guard.close(buffers::clear);
        assertFalse(guard.publish(old, () -> buffers.add(new byte[1024])));
        assertFalse(guard.publish(old, failures::incrementAndGet));
        assertTrue(buffers.isEmpty());
        assertEquals(0, failures.get());
        guard.reset(buffers::clear);
        assertFalse(guard.publish(guard.epoch(), failures::incrementAndGet), "reset must not reopen a closed pipeline");
    }

    @Test void replacementAcceptsOnlyItsOwnSuccessAndFailures() {
        DecodeCompletionGuard guard = new DecodeCompletionGuard();
        long old = guard.epoch();
        List<String> results = new ArrayList<>();
        assertTrue(guard.publish(old, () -> results.add("old")));
        guard.reset(results::clear);
        assertFalse(guard.publish(old, () -> results.add("late")));
        assertTrue(guard.publish(guard.epoch(), () -> results.add("new")));
        assertEquals(java.util.Collections.singletonList("new"), results);
        // Cancellation while still owned is a real failure, not blanket-exempt.
        assertThrows(CancellationException.class, () -> guard.publish(guard.epoch(), () -> {
            throw new CancellationException("unexpected active cancellation");
        }));
    }

    @Test void closeClearsAConcurrentPublicationBeforeReturning() throws Exception {
        DecodeCompletionGuard guard = new DecodeCompletionGuard();
        long epoch = guard.epoch();
        List<String> buffers = new ArrayList<>();
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FutureTask<Boolean> publish = new FutureTask<>(() -> guard.publish(epoch, () -> {
            publishing.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            buffers.add("decoded");
        }));
        Thread worker = new Thread(publish);
        worker.start();
        try {
            assertTrue(publishing.await(5, TimeUnit.SECONDS));
            FutureTask<Void> close = new FutureTask<>(() -> { guard.close(buffers::clear); return null; });
            Thread closer = new Thread(close);
            closer.start();
            release.countDown();
            assertTrue(publish.get(5, TimeUnit.SECONDS));
            close.get(5, TimeUnit.SECONDS);
            closer.join(5000);
            assertTrue(buffers.isEmpty());
            assertFalse(guard.publish(epoch, () -> buffers.add("late")));
        } finally { release.countDown(); worker.join(5000); }
    }
}
