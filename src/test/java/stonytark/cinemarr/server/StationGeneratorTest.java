package stonytark.cinemarr.server;

import stonytark.cinemarr.core.model.QueueTrack;


import stonytark.cinemarr.core.server.PlexException;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.network.CinemarrPayloads;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StationGeneratorTest {
    @Test void prefersAdvertisedNativeRadioBeforeSonicNearest() throws Exception {
        FakeCatalog catalog = new FakeCatalog(); catalog.nativeTracks = List.of(track("native", "New Artist"));
        var result = new StationGenerator(catalog).generate(station(CinemarrPayloads.StationType.ARTIST_RADIO,
                seed(CinemarrPayloads.ItemKind.ARTIST, "artist")), List.of(), CinemarrPayloads.SonicCapability.READY, false);
        assertEquals(List.of("native"), keys(result.tracks()));
        assertEquals(1, catalog.nativeCalls); assertEquals(0, catalog.nearestCalls);
    }

    @Test void widensTrackDistanceOnceAndSuppressesRecentTracksAndArtists() throws Exception {
        FakeCatalog catalog = new FakeCatalog();
        catalog.nearestTracks.put(0.25, List.of());
        catalog.nearestTracks.put(0.40, List.of(track("old-key", "Fresh"), track("same-artist", "Old Artist"), track("chosen", "Fresh")));
        List<QueueTrack> history = List.of(track("old-key", "Earlier"), track("prior", "Old Artist"));
        var result = new StationGenerator(catalog).generate(station(CinemarrPayloads.StationType.TRACK_RADIO,
                seed(CinemarrPayloads.ItemKind.TRACK, "seed")), history, CinemarrPayloads.SonicCapability.READY, false);
        assertEquals(List.of("chosen"), keys(result.tracks()));
        assertEquals(List.of(0.25, 0.40), catalog.trackDistances);
    }

    @Test void gatesMetadataFallbackAndNeverUsesItForAdventure() throws Exception {
        FakeCatalog catalog = new FakeCatalog(); catalog.fallbackTracks = List.of(track("fallback", "Artist"));
        StationGenerator generator = new StationGenerator(catalog);
        StationDefinition radio = station(CinemarrPayloads.StationType.TRACK_RADIO, seed(CinemarrPayloads.ItemKind.TRACK, "seed"));
        assertThrows(PlexException.class, () -> generator.generate(radio, List.of(), CinemarrPayloads.SonicCapability.NO_PLEX_PASS, false));
        assertEquals(List.of("fallback"), keys(generator.generate(radio, List.of(), CinemarrPayloads.SonicCapability.NO_PLEX_PASS, true).tracks()));
        StationDefinition adventure = station(CinemarrPayloads.StationType.SONIC_ADVENTURE,
                seed(CinemarrPayloads.ItemKind.TRACK, "one"), seed(CinemarrPayloads.ItemKind.TRACK, "two"));
        assertThrows(PlexException.class, () -> generator.generate(adventure, List.of(), CinemarrPayloads.SonicCapability.NO_PLEX_PASS, true));
        assertEquals(1, catalog.fallbackCalls);
    }

    @Test void adventurePreservesWaypointOrderDeduplicatesJoinsAndRejectsIncompletePaths() throws Exception {
        FakeCatalog catalog = new FakeCatalog();
        catalog.paths.put("one->two", List.of(track("one", "A"), track("middle", "B"), track("two", "C")));
        catalog.paths.put("two->three", List.of(track("two", "C"), track("three", "D")));
        StationGenerator generator = new StationGenerator(catalog);
        StationDefinition adventure = station(CinemarrPayloads.StationType.SONIC_ADVENTURE,
                seed(CinemarrPayloads.ItemKind.TRACK, "one"), seed(CinemarrPayloads.ItemKind.TRACK, "two"), seed(CinemarrPayloads.ItemKind.TRACK, "three"));
        assertEquals(List.of("one", "middle", "two", "three"), keys(generator.generate(
                adventure, List.of(), CinemarrPayloads.SonicCapability.READY, false).tracks()));
        catalog.paths.put("two->three", List.of(track("two", "C")));
        PlexException partial = assertThrows(PlexException.class, () -> generator.generate(
                adventure, List.of(), CinemarrPayloads.SonicCapability.READY, false));
        assertTrue(partial.getMessage().contains("waypoint 2 and 3"));
    }

    @Test void adventureRejectsAnUnanalyzedWaypointBeforeComputingPaths() {
        FakeCatalog catalog = new FakeCatalog(); catalog.unanalyzed.add("two");
        StationDefinition adventure = station(CinemarrPayloads.StationType.SONIC_ADVENTURE,
                seed(CinemarrPayloads.ItemKind.TRACK, "one"), seed(CinemarrPayloads.ItemKind.TRACK, "two"));
        PlexException missing = assertThrows(PlexException.class, () -> new StationGenerator(catalog).generate(
                adventure, List.of(), CinemarrPayloads.SonicCapability.READY, false));
        assertTrue(missing.getMessage().contains("waypoint 2")); assertEquals(0, catalog.pathCalls);
    }

    @Test void libraryShuffleAvoidsRecentArtistsBeforeRelaxingThatRule() throws Exception {
        FakeCatalog catalog = new FakeCatalog(); catalog.randomTracks = List.of(track("one", "Recent Artist"), track("two", "New Artist"));
        var result = new StationGenerator(catalog).generate(station(CinemarrPayloads.StationType.LIBRARY_SHUFFLE),
                List.of(track("history", "Recent Artist")), CinemarrPayloads.SonicCapability.ANALYSIS_INCOMPLETE, false);
        assertEquals(List.of("two"), keys(result.tracks()));
    }

    private static StationDefinition station(CinemarrPayloads.StationType type, CinemarrPayloads.StationSeed... seeds) {
        return new StationDefinition(type, type.name(), List.of(seeds), 1);
    }
    private static CinemarrPayloads.StationSeed seed(CinemarrPayloads.ItemKind kind, String key) {
        return new CinemarrPayloads.StationSeed(kind, key, key, "Artist");
    }
    private static QueueTrack track(String key, String artist) { return new QueueTrack(key, key, artist, "Album", 1_000); }
    private static List<String> keys(List<QueueTrack> tracks) { return tracks.stream().map(QueueTrack::key).toList(); }

    private static final class FakeCatalog implements StationCatalog {
        private List<QueueTrack> nativeTracks = List.of(), fallbackTracks = List.of(), randomTracks = List.of();
        private final Map<Double, List<QueueTrack>> nearestTracks = new HashMap<>();
        private final Map<String, List<QueueTrack>> paths = new HashMap<>();
        private final Set<String> unanalyzed = new java.util.HashSet<>();
        private final List<Double> trackDistances = new ArrayList<>();
        private int nativeCalls, nearestCalls, fallbackCalls, pathCalls;

        @Override public List<QueueTrack> nativeRadioTracks(CinemarrPayloads.StationSeed seed, int limit) { nativeCalls++; return nativeTracks; }
        @Override public boolean hasSonicAnalysis(String key) { return !unanalyzed.contains(key); }
        @Override public List<PlexClient.SonicResult> nearest(CinemarrPayloads.ItemKind kind, String key, int limit, double maxDistance) { nearestCalls++; return List.of(); }
        @Override public List<QueueTrack> nearestTracks(String key, int limit, double maxDistance) {
            trackDistances.add(maxDistance); return nearestTracks.getOrDefault(maxDistance, List.of());
        }
        @Override public List<QueueTrack> sonicPath(String startKey, String endKey, int limit) { pathCalls++; return paths.getOrDefault(startKey + "->" + endKey, List.of()); }
        @Override public List<QueueTrack> randomTracks(int limit, Set<String> excluded) { return randomTracks.stream().filter(track -> !excluded.contains(track.key())).toList(); }
        @Override public List<QueueTrack> metadataFallback(List<CinemarrPayloads.StationSeed> seeds, int limit, Set<String> excluded) { fallbackCalls++; return fallbackTracks; }
        @Override public List<QueueTrack> expand(CinemarrPayloads.ItemKind kind, String key, int limit) throws IOException { return List.of(); }
    }
}
