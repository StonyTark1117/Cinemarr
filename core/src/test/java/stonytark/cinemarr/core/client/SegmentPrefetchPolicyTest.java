package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SegmentPrefetchPolicyTest {
    @Test
    void keepsEnoughShortSegmentsToCoverLegacyDecodeJitter() {
        assertTrue(SegmentPrefetchPolicy.allowsAnother(15, SegmentPrefetchPolicy.MAX_READY_BYTES - 1));
        assertFalse(SegmentPrefetchPolicy.allowsAnother(16, 0));
        assertFalse(SegmentPrefetchPolicy.allowsAnother(0, SegmentPrefetchPolicy.MAX_READY_BYTES));
    }

    @Test
    void boundsTheQueueIncludingOneAlreadyGrantedCompletion() {
        assertEquals(96L * 1024L * 1024L, SegmentPrefetchPolicy.MAX_READY_RETAINED_BYTES);
        assertThrows(IllegalArgumentException.class, () -> SegmentPrefetchPolicy.allowsAnother(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> SegmentPrefetchPolicy.allowsAnother(0, -1));
    }
}
