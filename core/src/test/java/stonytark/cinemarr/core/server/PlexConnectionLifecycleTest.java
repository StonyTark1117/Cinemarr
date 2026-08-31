package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlexConnectionLifecycleTest {
    @Test void missingRulesOrTokenRemainDisabledAndCannotRetry() {
        PlexConnectionLifecycle lifecycle = new PlexConnectionLifecycle();
        try {
            lifecycle.configure("http://127.0.0.1:32400", "", Collections.emptyList(),
                    Runnable::run, connection -> {}, (message, delay) -> {});
            assertEquals(PlexConnectionLifecycle.State.DISABLED, lifecycle.state());
            assertFalse(lifecycle.retry());
            assertEquals(0L, lifecycle.retryInMs(System.currentTimeMillis()));
        } finally {
            lifecycle.close();
        }
    }

    @Test void closeIsIdempotentAndRejectsReconfiguration() {
        PlexConnectionLifecycle lifecycle = new PlexConnectionLifecycle();
        lifecycle.close();
        lifecycle.close();
        assertEquals(PlexConnectionLifecycle.State.DISABLED, lifecycle.state());
        assertFalse(lifecycle.retry());
        assertThrows(IllegalStateException.class, () -> lifecycle.configure(
                "http://127.0.0.1:32400", "token", Collections.emptyList(),
                Runnable::run, connection -> {}, (message, delay) -> {}));
    }
}
