package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.network.Hashing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDecodeRetirementTest {
    @Test void closeDoesNotReportInterruptedRetiredDecodeAsRecovery() throws Exception {
        CountDownLatch decoding = new CountDownLatch(1);
        try (LegacyVideoPlayback playback = new LegacyVideoPlayback(bytes -> {
            decoding.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException interrupted) { throw new CancellationException("retired decode interrupted"); }
            throw new AssertionError("unreachable");
        })) {
            submit(playback);
            assertTrue(decoding.await(5, TimeUnit.SECONDS));
            playback.close();
            awaitCompletion(playback);
            assertEquals(0, playback.decoderRecoveries());
            assertTrue(decoded(playback).isEmpty());
        }
    }

    @Test void closeDiscardsLateSuccessfulNativeReturn() throws Exception {
        lateCompletion(true, false);
    }

    @Test void resetDiscardsLateSuccessfulDecode() throws Exception {
        lateCompletion(false, false);
    }

    @Test void resetDiscardsOldFailureButActiveFailureStillCounts() throws Exception {
        lateCompletion(false, true);
        try (LegacyVideoPlayback playback = new LegacyVideoPlayback(bytes -> {
            throw new CancellationException("unexpected active cancellation");
        })) {
            submit(playback);
            awaitCompletion(playback);
            assertEquals(1, playback.decoderRecoveries());
        }
    }

    private static void lateCompletion(boolean close, boolean fail) throws Exception {
        CountDownLatch decoding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (LegacyVideoPlayback playback = new LegacyVideoPlayback(bytes -> {
            decoding.countDown();
            boolean released = false;
            while (!released) {
                try { released = release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { continue; } // Native returns can outlive interruption.
                if (!released) throw new AssertionError("decode was never released");
            }
            if (fail) throw new CancellationException("superseded decode");
            return new LegacyDecodedMediaSegment(Collections.singletonList(
                    new LegacyDecodedVideoFrame(0, 1, 1, new byte[4])), Collections.emptyList());
        })) {
            try {
                submit(playback);
                assertTrue(decoding.await(5, TimeUnit.SECONDS));
                if (close) playback.close(); else playback.reset();
            } finally { release.countDown(); }
            awaitCompletion(playback);
            assertTrue(decoded(playback).isEmpty(), "retired worker must not repopulate cleared buffers");
            assertEquals(0, playback.decoderRecoveries());
        } finally { release.countDown(); }
    }

    private static void submit(LegacyVideoPlayback playback) throws Exception {
        UUID session = UUID.randomUUID();
        byte[] bytes = { 1 };
        String sha = Hashing.sha256(bytes);
        VideoSegmentAssembler assembler = new VideoSegmentAssembler();
        assembler.begin(session, 1, 1, 0, 1, sha, 0, true);
        VideoSegmentAssembler.CompletedSegment segment = assembler.accept(session, 1, 1, 0, 0, 1,
                sha, 0, true, bytes).get();
        Field identity = LegacyVideoPlayback.class.getDeclaredField("identity");
        identity.setAccessible(true); identity.set(playback, segment.identity());
        Method submit = LegacyVideoPlayback.class.getDeclaredMethod("submit", VideoSegmentAssembler.CompletedSegment.class);
        submit.setAccessible(true);
        submit.invoke(playback, segment);
    }

    private static void awaitCompletion(LegacyVideoPlayback playback) throws Exception {
        AtomicInteger pending = (AtomicInteger) field(playback, "pending");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pending.get() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(0, pending.get(), "decode worker did not finish");
    }

    private static Queue<?> decoded(LegacyVideoPlayback playback) throws Exception {
        return (Queue<?>) field(playback, "decoded");
    }

    private static Object field(LegacyVideoPlayback playback, String name) throws Exception {
        Field field = LegacyVideoPlayback.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(playback);
    }
}
