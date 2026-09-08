package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class DecodedBufferBudgetTest {
    @Test void compressedSizeCannotPermitUnboundedPcmRetention() {
        DecodedBufferBudget budget = new DecodedBufferBudget();
        for (int n = 0; n < 64; n++) assertEquals(1024 * 1024, budget.reservePcm16(512 * 1024));
        assertEquals(DecodedBufferBudget.MAX_AUDIO_BYTES, budget.audioBytes());
        assertThrows(IllegalArgumentException.class, () -> budget.reservePcm16(1));
        assertEquals(64, budget.audioFrames());
        assertEquals(DecodedBufferBudget.MAX_AUDIO_BYTES, budget.audioBytes());
    }

    @Test void tinyOrEmptyFramesCannotBuildAnUnboundedList() {
        DecodedBufferBudget budget = new DecodedBufferBudget();
        for (int n = 0; n < DecodedBufferBudget.MAX_AUDIO_FRAMES; n++) budget.reservePcm16(0);
        assertThrows(IllegalArgumentException.class, () -> budget.reservePcm16(0));
        assertEquals(0, budget.audioBytes());
    }

    @Test void hugeSamplesAndDimensionsFailBeforeAllocationOrArithmeticOverflow() {
        DecodedBufferBudget budget = new DecodedBufferBudget();
        for (long samples : new long[]{-1, 524289, Integer.MAX_VALUE, Long.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> budget.reservePcm16(samples));
        assertEquals(0, budget.audioFrames());
        assertEquals(1920 * 1080 * 4, DecodedBufferBudget.rgbaBytes(1920,1080));
        assertEquals(128 * 1024 * 1024, DecodedBufferBudget.rgbaBytes(8192,4096));
        for (int[] size : new int[][]{{0,1},{1,-1},{8192,4097},{Integer.MAX_VALUE,Integer.MAX_VALUE}})
            assertThrows(IllegalArgumentException.class, () -> DecodedBufferBudget.rgbaBytes(size[0],size[1]));
    }

    @Test void cancellationPreservesInterruptAndDoesNotReserveBuffers() {
        DecodedBufferBudget budget = new DecodedBufferBudget();
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> budget.reservePcm16(1));
            assertThrows(CancellationException.class, () -> DecodedBufferBudget.rgbaBytes(1,1));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, budget.audioFrames());
        } finally { Thread.interrupted(); }
    }
}
