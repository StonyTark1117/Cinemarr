package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBackendQueueGuardTest {
    @Test void recoversStoppedMidTrackBackendAfterGrace(){assertTrue(LegacyBackendQueueGuard.shouldRecover(false,false,false,2_000,1_000));}
    @Test void permitsStartOrResumeToSettle(){assertFalse(LegacyBackendQueueGuard.shouldRecover(false,false,false,999,1_000));}
    @Test void doesNotRecoverPausedFinishedOrPlaying(){assertFalse(LegacyBackendQueueGuard.shouldRecover(true,false,false,2_000,1_000));assertFalse(LegacyBackendQueueGuard.shouldRecover(false,true,false,2_000,1_000));assertFalse(LegacyBackendQueueGuard.shouldRecover(false,false,true,2_000,1_000));}
}
