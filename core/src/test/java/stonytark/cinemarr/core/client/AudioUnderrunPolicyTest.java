package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioUnderrunPolicyTest {
    @Test void activePlaybackCountsBackendStarvationExactlyOnce() {
        assertEquals(1, AudioUnderrunPolicy.additionalUnderruns(0, 1, true, false, false));
        assertEquals(2, AudioUnderrunPolicy.additionalUnderruns(3, 5, false, false, false));
        assertEquals(1, AudioUnderrunPolicy.additionalUnderruns(4, 4, true, false, false));
    }

    @Test void pauseAndNormalTerminalDrainAreNotUnderruns() {
        assertEquals(0, AudioUnderrunPolicy.additionalUnderruns(0, 1, true, true, false));
        assertEquals(0, AudioUnderrunPolicy.additionalUnderruns(0, 1, true, false, true));
    }
}
