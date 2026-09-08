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
    void activePrerollAbsorbsSoundExecutorSetupDelay() {
        long beforeDelay = CinemarrVideoAudio.additionalSilenceUs(
                1_500_000L, 1_000_000L, 80_000L);
        long afterDelay = CinemarrVideoAudio.additionalSilenceUs(
                1_250_000L, 750_000L, 80_000L);
        assertEquals(420_000L, beforeDelay);
        assertEquals(beforeDelay, afterDelay,
                "measuring after the executor delay must preserve the absolute media boundary");
        assertEquals(1_500_000L, 250_000L + VideoPcmAudioStream.physicalBoundaryDelayUs(
                1_000_000L + afterDelay, 250_000L, 80_000L),
                "a quarter-second executor stall must consume preroll instead of shifting program audio");
        assertEquals(0L, CinemarrVideoAudio.additionalSilenceUs(500_000L, 600_000L, 80_000L));
    }

    @Test
    void endpointDriftBudgetLeavesRoomForPairwiseCaptureJitter() {
        assertFalse(CinemarrVideoAudio.driftRequiresRebuffer(50_000L));
        assertFalse(CinemarrVideoAudio.driftRequiresRebuffer(-50_000L));
        assertTrue(CinemarrVideoAudio.driftRequiresRebuffer(50_001L));
        assertTrue(CinemarrVideoAudio.driftRequiresRebuffer(-50_001L));
    }

    @Test
    void postBoundaryTimelineAdvancesMonotonicallyFromWallTime() {
        long boundaryUs = 4_000_000L;
        long first = CinemarrVideoAudio.advanceTimelineUs(
                boundaryUs, 1_000_000_000L, 1_250_000_000L, false);
        long second = CinemarrVideoAudio.advanceTimelineUs(
                first, 1_250_000_000L, 1_500_000_000L, false);
        assertEquals(4_250_000L, first);
        assertEquals(4_500_000L, second);
        assertEquals(second, CinemarrVideoAudio.advanceTimelineUs(
                second, 1_500_000_000L, 1_400_000_000L, false),
                "a backward observation cannot rewind the media clock");
        assertEquals(second, CinemarrVideoAudio.advanceTimelineUs(
                second, 1_500_000_000L, 1_750_000_000L, true),
                "pause freezes the media clock while its observation time advances");
    }

    @Test
    void physicalClockCombinesUnqueuedBuffersAndCurrentSourceOffset() {
        assertEquals(10_520_000L, CinemarrVideoAudio.physicalTimelineUs(
                10_000_000L, 4_000_000L, 1_500_000L,
                8, 250_000L, 100_000L, 80_000L));
        assertEquals(10_000_000L, CinemarrVideoAudio.physicalTimelineUs(
                10_000_000L, 3_000_000L, 1_500_000L,
                12, 250_000L, 1_000_000L, 80_000L),
                "leading scheduling silence must not advance the media timeline");
    }

    @Test
    void physicalClockCorrectionCanStallButNeverRewindReportedTime() {
        long reported = 12_200_000L;
        assertEquals(reported, CinemarrVideoAudio.advancePhysicalTimelineUs(
                reported, 12_000_000L, 1_000_000_000L, 1_010_000_000L, false));
        assertEquals(12_250_000L, CinemarrVideoAudio.advancePhysicalTimelineUs(
                reported, 12_240_000L, 1_000_000_000L, 1_010_000_000L, false));
        assertEquals(reported, CinemarrVideoAudio.advancePhysicalTimelineUs(
                reported, 12_500_000L, 1_000_000_000L, 1_010_000_000L, true));
    }
}
