package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.network.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportSecurityTest {
    @Test void departedRateLimitSubjectsAreReleasedWithoutResettingOtherClients() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        UUID remaining = UUID.randomUUID();
        assertTrue(limiter.allow(remaining, 1, 1000));
        for (int i = 0; i < 1000; i++) {
            UUID departed = UUID.randomUUID();
            assertTrue(limiter.allow(departed, 1, 1000));
            assertEquals(2, limiter.trackedSubjects());
            limiter.remove(departed);
            assertEquals(1, limiter.trackedSubjects());
        }
        assertFalse(limiter.allow(remaining, 1, 1000));
        limiter.remove(remaining);
        assertEquals(0, limiter.trackedSubjects());
        assertTrue(limiter.allow(remaining, 1, 1000));
    }

    @Test void serverStopClearsAllRateLimitSubjectsIdempotently() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        for (int i = 0; i < 1000; i++) limiter.allow(UUID.randomUUID(), 1, 1000);
        assertEquals(1000, limiter.trackedSubjects());
        limiter.clear(); limiter.clear();
        assertEquals(0, limiter.trackedSubjects());
    }

    @Test void rejectsAcknowledgementsFromTheWrongSession() {
        UUID active = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        UUID stale = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
        ChunkTransferPolicy.State request = ChunkTransferPolicy.begin(active, 7, 0, 4, 1_000);

        assertFalse(ChunkTransferPolicy.acknowledge(request, stale, 7, 3, 4_000, 1_100).isPresent());
        assertTrue(ChunkTransferPolicy.acknowledge(request, active, 7, 3, 4_000, 1_100).isPresent());
    }

    @Test void rejectsBadOrMalformedChunkHashes() {
        byte[] chunk = "canonical audio chunk".getBytes(StandardCharsets.UTF_8);
        String hash = Hashing.sha256(chunk);

        assertTrue(Hashing.matchesSha256(chunk, hash));
        assertFalse(Hashing.matchesSha256("tampered".getBytes(StandardCharsets.UTF_8), hash));
        assertFalse(Hashing.matchesSha256(chunk, "abcd"));
        assertFalse(Hashing.matchesSha256(chunk, null));
    }

    @Test void rateLimitIsPerSubjectAndResetsOnlyAtTheNextWindow() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter();
        UUID first = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID second = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        assertTrue(limiter.allow(first, 2, 1_000));
        assertTrue(limiter.allow(first, 2, 1_999));
        assertFalse(limiter.allow(first, 2, 1_999));
        assertTrue(limiter.allow(second, 2, 1_999));
        assertTrue(limiter.allow(first, 2, 2_000));
    }
}
