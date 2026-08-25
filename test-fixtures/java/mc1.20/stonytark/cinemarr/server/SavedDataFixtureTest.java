package stonytark.cinemarr.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.network.CinemarrPayloads;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedDataFixtureTest {
    @Test void migratesSchemasOneThroughThreeAndRoundTripsCanonicalSchemaFour() throws Exception {
        assertSchemaOne(roundTrip("schema-1.snbt"));
        assertSchemaTwo(roundTrip("schema-2.snbt"));
        assertSchemaThree(roundTrip("schema-3.snbt"));
    }

    private static CinemarrSavedData roundTrip(String fixture) throws Exception {
        CinemarrSavedData migrated = CinemarrSavedData.load(read(fixture));
        CompoundTag canonical = migrated.save(new CompoundTag());
        assertEquals(4, canonical.getInt("schemaVersion"), fixture);
        CinemarrSavedData restored = CinemarrSavedData.load(canonical);
        assertEquivalent(migrated, restored, fixture);
        return restored;
    }

    private static CompoundTag read(String fixture) throws Exception {
        InputStream stream = SavedDataFixtureTest.class.getResourceAsStream("/saved-data/" + fixture);
        assertNotNull(stream, fixture);
        try { return TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8)); }
        finally { stream.close(); }
    }

    private static void assertSchemaOne(CinemarrSavedData state) {
        assertEquals("v1-current", state.current().key());
        assertEquals(CinemarrPayloads.PlaybackOrigin.MANUAL, state.currentOrigin());
        assertEquals("Manual request", state.currentSourceName());
        assertEquals("v1-next", state.queue().get(0).key());
        assertEquals(111, state.checkpointMs());
        assertTrue(state.paused());
    }

    private static void assertSchemaTwo(CinemarrSavedData state) {
        assertEquals("v2-current", state.current().key());
        assertEquals(CinemarrPayloads.PlaybackOrigin.STATION, state.currentOrigin());
        assertEquals("Station", state.currentSourceName());
        assertEquals("v2-next", state.queue().get(0).key());
        assertEquals(222, state.checkpointMs());
        assertTrue(state.paused());
    }

    private static void assertSchemaThree(CinemarrSavedData state) {
        assertEquals("v3-current", state.current().key());
        assertEquals(CinemarrPayloads.PlaybackOrigin.ADVENTURE, state.currentOrigin());
        assertEquals("Sonic Adventure: Fixture Route", state.currentSourceName());
        assertEquals(CinemarrPayloads.StationType.SONIC_ADVENTURE, state.station().type());
        assertEquals(17, state.station().generation());
        assertEquals(2, state.station().seeds().size());
        assertTrue(state.autoplayEnabled());
        assertEquals("history-a", state.history().get(0).key());
        assertFalse(state.paused());
    }

    private static void assertEquivalent(CinemarrSavedData expected, CinemarrSavedData actual, String fixture) {
        assertEquals(expected.queue(), actual.queue(), fixture + " queue");
        assertEquals(expected.history(), actual.history(), fixture + " history");
        assertEquals(expected.current(), actual.current(), fixture + " current");
        assertEquals(expected.currentOrigin(), actual.currentOrigin(), fixture + " origin");
        assertEquals(expected.currentSourceName(), actual.currentSourceName(), fixture + " source");
        assertEquals(expected.station().type(), actual.station().type(), fixture + " station type");
        assertEquals(expected.station().name(), actual.station().name(), fixture + " station name");
        assertEquals(expected.station().generation(), actual.station().generation(), fixture + " generation");
        assertEquals(expected.station().seeds(), actual.station().seeds(), fixture + " seeds");
        assertEquals(expected.autoplayEnabled(), actual.autoplayEnabled(), fixture + " autoplay");
        assertEquals(expected.checkpointMs(), actual.checkpointMs(), fixture + " checkpoint");
        assertEquals(expected.paused(), actual.paused(), fixture + " paused");
    }
}
