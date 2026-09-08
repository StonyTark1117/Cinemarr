package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlexSegmentFetchTest {
    @Test
    void waitsForAnAdvertisedSegmentToMaterialize() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> observed = new ArrayList<Long>();
        byte[] expected = new byte[] { 1, 2, 3 };

        byte[] actual = PlexSegmentFetch.fetch(() -> {
            if (attempts.getAndIncrement() < 5) throw notFound();
            return expected;
        }, observed::add, delay -> {});

        assertArrayEquals(expected, actual);
        assertEquals(6, attempts.get());
        assertEquals(Arrays.asList(100L, 250L, 500L, 750L, 1_000L), observed);
    }

    @Test
    void exhaustsTheBoundedNotReadyWindow() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> observed = new ArrayList<Long>();

        PlexException failure = assertThrows(PlexException.class, () -> PlexSegmentFetch.fetch(() -> {
            attempts.incrementAndGet();
            throw notFound();
        }, observed::add, delay -> {}));

        assertEquals(PlexException.Kind.NOT_FOUND, failure.kind());
        assertEquals(10, attempts.get());
        assertEquals(9, observed.size());
        assertEquals(11_600L, observed.stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void keepsOrdinaryTransportRetriesShortAndRejectsAuthenticationImmediately() {
        AtomicInteger offlineAttempts = new AtomicInteger();
        IOException offline = assertThrows(IOException.class, () -> PlexSegmentFetch.fetch(() -> {
            offlineAttempts.incrementAndGet();
            throw new IOException("offline");
        }, delay -> {}, delay -> {}));
        assertEquals("offline", offline.getMessage());
        assertEquals(3, offlineAttempts.get());

        AtomicInteger authenticationAttempts = new AtomicInteger();
        PlexException authentication = assertThrows(PlexException.class, () -> PlexSegmentFetch.fetch(() -> {
            authenticationAttempts.incrementAndGet();
            throw new PlexException(PlexException.Kind.AUTHENTICATION, "denied");
        }, delay -> {}, delay -> {}));
        assertEquals(PlexException.Kind.AUTHENTICATION, authentication.kind());
        assertEquals(1, authenticationAttempts.get());
    }

    @Test
    void interruptionIsPreserved() {
        IOException failure = assertThrows(IOException.class, () -> PlexSegmentFetch.fetch(
                () -> { throw notFound(); }, delay -> {}, delay -> { throw new InterruptedException("stop"); }));
        assertTrue(Thread.interrupted(), "the worker interrupt flag must be restored before propagation");
        assertTrue(failure.getMessage().contains("Interrupted"));
    }

    private static PlexException notFound() {
        return new PlexException(PlexException.Kind.NOT_FOUND, "Plex request returned HTTP 404");
    }
}
