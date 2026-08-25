package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WatchPartyRegistryTest {
    @Test void sharedNameReusesOneSessionAndIndependentNamesConsumeSlots() {
        WatchPartyRegistry registry = new WatchPartyRegistry(2);
        WatchPartyRegistry.Session livingRoom = registry.tune("family", UUID.randomUUID());
        WatchPartyRegistry.Session secondTv = registry.tune("family", UUID.randomUUID());
        WatchPartyRegistry.Session independent = registry.tune("private", UUID.randomUUID());
        assertEquals(livingRoom.id(), secondTv.id());
        assertNotEquals(livingRoom.id(), independent.id());
        assertEquals(2, registry.activeSessions());
        assertThrows(IllegalStateException.class, () -> registry.tune("third", UUID.randomUUID()));
    }
}
