package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CinemarrVideoAudioScheduleTest {
    @Test
    void schedulesBeyondThePhysicalPrerollOnSharedQuarterSecondBoundary() {
        assertEquals(1_750_000L, CinemarrVideoAudio.scheduledStartUs(125_000L));
        assertEquals(2_750_000L, CinemarrVideoAudio.scheduledStartUs(1_001_000L));
    }

    @Test
    void acceptsOnlyContinuousAudioWithPostBoundaryRunway() {
        long start = 4_000_000L;
        assertTrue(CinemarrVideoAudio.hasStartRunway(start,
                3_989_000L, 4_010_000L, 7_000_000L));
        assertFalse(CinemarrVideoAudio.hasStartRunway(start,
                4_101_000L, 4_122_000L, 7_500_000L), "a gap at the media boundary must not start");
        assertFalse(CinemarrVideoAudio.hasStartRunway(start,
                3_989_000L, 4_010_000L, 6_999_999L), "less than three seconds of runway must wait");
        assertFalse(CinemarrVideoAudio.hasStartRunway(start,
                3_950_000L, 4_000_000L, 5_000_000L), "a frame ending at the boundary has no playable samples");
    }

    @Test
    void sourcePlaybackCursorDoesNotWrapWithTheOpenAlStreamingBufferOffset() {
        assertEquals(521_000L, CinemarrVideoAudio.totalSourcePlayedUs(521_000L, 21_000L));
        assertEquals(42_000L, CinemarrVideoAudio.totalSourcePlayedUs(15_000L, 42_000L));
        assertEquals(0L, CinemarrVideoAudio.totalSourcePlayedUs(-1L, -1L));
    }
}
