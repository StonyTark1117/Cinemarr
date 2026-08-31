package stonytark.cinemarr.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TransferWindowFlowTest {
    @Test void largeSegmentAcknowledgesEveryWindowBeforeContinuing() {
        TransferWindowFlow.Decision beforeBoundary = TransferWindowFlow.afterChunk(6, 0, 8, 25, false);
        assertFalse(beforeBoundary.acknowledgesWindow());

        TransferWindowFlow.Decision first = TransferWindowFlow.afterChunk(7, 0, 8, 25, false);
        assertTrue(first.acknowledgesWindow());
        assertEquals(7, first.receivedThroughChunk());
        assertTrue(first.continuesSegment());
        assertEquals(8, first.nextWindowStart());

        TransferWindowFlow.Decision second = TransferWindowFlow.afterChunk(15, 8, 8, 25, false);
        assertEquals(15, second.receivedThroughChunk());
        assertEquals(16, second.nextWindowStart());

        TransferWindowFlow.Decision third = TransferWindowFlow.afterChunk(23, 16, 8, 25, false);
        assertEquals(23, third.receivedThroughChunk());
        assertEquals(24, third.nextWindowStart());

        TransferWindowFlow.Decision complete = TransferWindowFlow.afterChunk(24, 24, 8, 25, true);
        assertEquals(24, complete.receivedThroughChunk());
        assertFalse(complete.continuesSegment());
    }

    @Test void rejectsInvalidWindowCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> TransferWindowFlow.afterChunk(0, 0, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> TransferWindowFlow.afterChunk(1, 0, 8, 1, false));
    }
}
