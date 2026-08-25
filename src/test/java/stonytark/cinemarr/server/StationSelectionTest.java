package stonytark.cinemarr.server;

import stonytark.cinemarr.core.model.QueueTrack;


import stonytark.cinemarr.core.server.PlexException;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.network.CinemarrPayloads;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StationSelectionTest {
    @Test void validatesModeSpecificSeedRules() throws Exception {
        assertDoesNotThrow(() -> StationGenerator.validate(new StationDefinition(CinemarrPayloads.StationType.SONIC_ADVENTURE, "Adventure",
                List.of(seed(CinemarrPayloads.ItemKind.TRACK, "1"), seed(CinemarrPayloads.ItemKind.TRACK, "2")), 1)));
        assertThrows(PlexException.class, () -> StationGenerator.validate(new StationDefinition(CinemarrPayloads.StationType.SONIC_MIX, "Mix",
                List.of(seed(CinemarrPayloads.ItemKind.TRACK, "1"), seed(CinemarrPayloads.ItemKind.ARTIST, "2")), 1)));
        assertThrows(PlexException.class, () -> StationGenerator.validate(new StationDefinition(CinemarrPayloads.StationType.SONIC_ADVENTURE, "Adventure",
                List.of(seed(CinemarrPayloads.ItemKind.TRACK, "1")), 1)));
    }

    private static CinemarrPayloads.StationSeed seed(CinemarrPayloads.ItemKind kind, String key) { return new CinemarrPayloads.StationSeed(kind, key, key, ""); }
}
