package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedstoneControlPolicyTest {
    @Test void risingEdgeTogglesActivePlayback() {
        assertEquals(RedstoneControlPolicy.Action.PAUSE,
                RedstoneControlPolicy.action(false, true, true, false));
        assertEquals(RedstoneControlPolicy.Action.RESUME,
                RedstoneControlPolicy.action(false, true, true, true));
    }

    @Test void heldLowOrIdleSignalsDoNothing() {
        assertEquals(RedstoneControlPolicy.Action.NONE,
                RedstoneControlPolicy.action(true, true, true, false));
        assertEquals(RedstoneControlPolicy.Action.NONE,
                RedstoneControlPolicy.action(false, false, true, false));
        assertEquals(RedstoneControlPolicy.Action.NONE,
                RedstoneControlPolicy.action(false, true, false, false));
    }
}
