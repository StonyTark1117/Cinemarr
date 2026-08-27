package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VideoPcmAudioStreamTest {
    @Test
    void emptyReadsReturnSilenceWithoutBlockingTheSoundEngine() {
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        ByteBuffer silence = assertTimeoutPreemptively(Duration.ofMillis(100), () -> stream.read(4096));
        assertEquals(4096, silence.remaining());
        while (silence.hasRemaining()) assertEquals(0, silence.get());
        assertEquals(1, stream.starvations());
        stream.read(4096);
        assertEquals(1, stream.starvations(), "one continuous empty period is one underrun");
    }

    @Test
    void newPcmEndsAStarvationPeriodAndIsReturnedBeforePadding() {
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        stream.read(8);
        assertTrue(stream.offer(new DecodedAudioFrame(0, 48_000, 2, new byte[]{1, 2, 3, 4})));
        ByteBuffer value = stream.read(8);
        assertEquals(1, value.get());
        assertEquals(2, value.get());
        assertEquals(3, value.get());
        assertEquals(4, value.get());
        assertEquals(2, stream.starvations());
    }

    @Test
    void scheduledStartPrependsSilenceAndTrimsTheFirstFrame() {
        AtomicLong now = new AtomicLong();
        VideoPcmAudioStream stream = new VideoPcmAudioStream(1_000, 1, now::get);
        stream.scheduleSilenceFor(2_000);
        now.set(1_000_000);
        assertTrue(stream.offer(new DecodedAudioFrame(1_000, 1_000, 1,
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}), 3_000));
        ByteBuffer value = stream.read(6);
        assertEquals(1_000, stream.scheduledSilenceUs());
        assertEquals(0, value.get());
        assertEquals(0, value.get());
        assertEquals(5, value.get());
        assertEquals(6, value.get());
        assertEquals(7, value.get());
        assertEquals(8, value.get());
    }

    @Test
    void lateScheduledStartSkipsElapsedProgramAudio() {
        AtomicLong now = new AtomicLong();
        VideoPcmAudioStream stream = new VideoPcmAudioStream(1_000, 1, now::get);
        stream.scheduleSilenceFor(2_000);
        assertTrue(stream.offer(new DecodedAudioFrame(0, 1_000, 1,
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8})));
        now.set(5_000_000);

        ByteBuffer value = stream.read(4);
        assertEquals(0, stream.scheduledSilenceUs());
        assertEquals(7, value.get());
        assertEquals(8, value.get());
        assertEquals(0, value.get());
        assertEquals(0, value.get());
    }

    @Test
    void initialPumpNeverRequestsMoreAudioThanIsReadableAfterExecutorDelay() {
        AtomicLong now = new AtomicLong();
        VideoPcmAudioStream stream = new VideoPcmAudioStream(1_000, 1, now::get);
        stream.scheduleSilenceFor(2_000_000);
        assertTrue(stream.offer(new DecodedAudioFrame(0, 1_000, 1, new byte[2_000])));
        now.set(300_000_000);

        assertEquals(9, stream.initialBufferCount(250, 12));

        now.set(2_900_000_000L);
        assertEquals(0, stream.initialBufferCount(250, 12),
                "a severely delayed executor must reschedule instead of forcing a starving read");
    }

    @Test
    void physicalBoundaryIncludesBackendLatencyAfterConsumedPreroll() {
        assertEquals(2_130_000, VideoPcmAudioStream.physicalBoundaryDelayUs(2_000_000, 50_000, 180_000));
        assertEquals(180_000, VideoPcmAudioStream.physicalBoundaryDelayUs(2_000_000, 2_100_000, 180_000));
        assertEquals(0, VideoPcmAudioStream.physicalBoundaryDelayUs(2_000_000, 2_100_000, -1));
    }

    @Test
    void compensatedSilenceMakesPhysicalOutputReachTheSharedBoundary() {
        assertEquals(1_370_000, VideoPcmAudioStream.compensatingSilenceUs(2_500_000, 950_000, 180_000));
        assertEquals(0, VideoPcmAudioStream.compensatingSilenceUs(500_000, 600_000, 80_000));
    }

    @Test
    void compensationSilenceIsInsertedAheadOfQueuedProgramAudio() {
        VideoPcmAudioStream stream = new VideoPcmAudioStream(1_000, 1);
        assertTrue(stream.offer(new DecodedAudioFrame(0, 1_000, 1, new byte[]{1, 2, 3, 4})));
        assertTrue(stream.prependSilenceFor(1_000));

        ByteBuffer value = stream.read(6);
        assertEquals(0, value.get());
        assertEquals(0, value.get());
        assertEquals(1, value.get());
        assertEquals(2, value.get());
        assertEquals(3, value.get());
        assertEquals(4, value.get());
    }
}
