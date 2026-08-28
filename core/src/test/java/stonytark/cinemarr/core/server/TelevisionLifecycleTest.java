package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TelevisionLifecycleTest {
    @Test void countsOwnersGloballyAndNotifiesOnlyOnceOnRemoval() {
        AtomicInteger removals = new AtomicInteger();
        TelevisionLifecycle.reset((id, session) -> removals.incrementAndGet());
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(TelevisionLifecycle.register(first, owner, 2));
        assertTrue(TelevisionLifecycle.register(second, owner, 2));
        assertFalse(TelevisionLifecycle.register(UUID.randomUUID(), owner, 2));
        assertEquals(2, TelevisionLifecycle.count(owner));
        TelevisionLifecycle.unregister(first, "party");
        TelevisionLifecycle.unregister(first, "party");
        assertEquals(1, TelevisionLifecycle.count(owner));
        assertEquals(1, removals.get());
        TelevisionLifecycle.reset(null);
    }
}
