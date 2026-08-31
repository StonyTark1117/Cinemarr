package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportTimingTest {
    @Test void estimatesClockOffsetFromMidpointAndFiltersJitter() {
        ClockSynchronizer clock = new ClockSynchronizer();
        ClockSynchronizer.Sample first = clock.accept(1_000, 1_150, 1_100);
        assertEquals(100, first.filteredOffsetMs()); assertEquals(1_900, clock.toLocalTime(2_000));
        clock.accept(2_000, 2_500, 2_800);
        assertEquals(100, clock.offsetMs());
        ClockSynchronizer.Sample better = clock.accept(3_000, 3_042, 3_020);
        assertEquals(32, better.filteredOffsetMs());
        assertEquals(20, clock.bestRoundTripMs());
        assertEquals(3, clock.sampleCount());
    }

    @Test void mediaClockReadinessWaitsForQualityButHasABoundedFallback() {
        ClockSynchronizer clock = new ClockSynchronizer();
        for (int sample = 0; sample < 8; sample++) {
            long sent = 1_000L + sample * 1_000L;
            clock.accept(sent, sent + 75L, sent + 150L);
        }
        assertFalse(clock.ready(8, 16, 50));

        clock.accept(20_000L, 20_010L, 20_020L);
        assertTrue(clock.ready(8, 16, 50));

        ClockSynchronizer highLatency = new ClockSynchronizer();
        for (int sample = 0; sample < 16; sample++) {
            long sent = 30_000L + sample * 1_000L;
            highLatency.accept(sent, sent + 75L, sent + 150L);
        }
        assertTrue(highLatency.ready(8, 16, 50));
    }

    @Test void synchronizedMediaClockNeverFallsBackToAnImpreciseSample() {
        ClockSynchronizer clock = new ClockSynchronizer();
        for (int sample = 0; sample < 32; sample++) {
            long sent = 40_000L + sample * 1_000L;
            clock.accept(sent, sent + 100L, sent + 200L);
        }
        assertFalse(clock.qualityReady(8, 150));

        clock.accept(80_000L, 80_030L, 80_060L);
        assertTrue(clock.qualityReady(8, 150));
    }

}
