package stonytark.cinemarr.server;

import stonytark.cinemarr.core.model.QueueTrack;


import stonytark.cinemarr.core.server.Mp3FrameIndex;
import stonytark.cinemarr.core.server.PlexException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import stonytark.cinemarr.network.CinemarrPayloads;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration test. Credentials are read only from the process environment. */
class PlexLiveSmokeTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "CINEMARR_LIVE_TEST", matches = "true")
    void browsesAndTranscodesRealPlexMusic() throws Exception {
        String url = required("CINEMARR_PLEX_URL");
        String token = required("CINEMARR_PLEX_TOKEN");
        String library = System.getenv().getOrDefault("CINEMARR_PLEX_LIBRARY", "");
        PlexClient plex = new PlexClient(url, token, library);
        plex.validate();
        PlexClient.SonicStatus sonic = plex.sonicStatus();
        assertEquals(CinemarrPayloads.SonicCapability.READY, sonic.capability(), sonic.message());

        PlexClient.Page albums = plex.browse(CinemarrPayloads.BrowseKind.ALBUMS, "", 0, 20);
        assertFalse(albums.items().isEmpty(), "The Plex music library has no albums");
        CinemarrPayloads.MediaItem album = albums.items().getFirst();
        List<QueueTrack> tracks = plex.expand(CinemarrPayloads.ItemKind.ALBUM, album.key());
        QueueTrack track = tracks.getFirst();
        PlexClient.Page search = plex.browse(CinemarrPayloads.BrowseKind.SEARCH, track.title(), 0, 20);
        assertTrue(search.items().stream().anyMatch(item -> item.key().equals(track.key())), "Plex track search did not find an exact title");

        List<QueueTrack> randomTracks = plex.analyzedTracks(10);
        assertTrue(randomTracks.size() >= 2, "The Plex library did not return two sonically analyzed tracks");
        List<QueueTrack> nearest = plex.nearestTracks(randomTracks.getFirst().key(), 25, 0.40);
        assertFalse(nearest.isEmpty(), "Track Radio seed had no sonic neighbours");
        List<QueueTrack> path = plex.sonicPath(randomTracks.getFirst().key(), randomTracks.get(1).key(), 100);
        assertTrue(path.size() >= 2, "Plex could not produce a Sonic Adventure between live-library tracks");

        StationGenerator generator = new StationGenerator(plex);
        long generation = 1;
        CinemarrPayloads.StationSeed first = seed(randomTracks.getFirst());
        CinemarrPayloads.StationSeed second = seed(randomTracks.get(1));
        assertFalse(generator.generate(new StationDefinition(CinemarrPayloads.StationType.TRACK_RADIO, "Track Radio", List.of(first), generation++),
                List.of(), sonic.capability(), false).tracks().isEmpty());
        assertFalse(generator.generate(new StationDefinition(CinemarrPayloads.StationType.LIBRARY_SHUFFLE, "Library Shuffle", List.of(), generation++),
                List.of(), sonic.capability(), false).tracks().isEmpty());
        PlexClient.Page artists = plex.browse(CinemarrPayloads.BrowseKind.ARTISTS, "", 0, 20);
        CinemarrPayloads.MediaItem artist = artists.items().stream().filter(item -> item.kind() == CinemarrPayloads.ItemKind.ARTIST).findFirst().orElseThrow();
        assertFalse(generator.generate(new StationDefinition(CinemarrPayloads.StationType.ARTIST_RADIO, "Artist Radio", List.of(
                        new CinemarrPayloads.StationSeed(artist.kind(), artist.key(), artist.title(), artist.subtitle())), generation++),
                List.of(), sonic.capability(), false).tracks().isEmpty());
        assertFalse(generator.generate(new StationDefinition(CinemarrPayloads.StationType.ALBUM_RADIO, "Album Radio", List.of(
                        new CinemarrPayloads.StationSeed(album.kind(), album.key(), album.title(), album.subtitle())), generation++),
                List.of(), sonic.capability(), false).tracks().isEmpty());
        assertFalse(generator.generate(new StationDefinition(CinemarrPayloads.StationType.SONIC_MIX, "Sonic Mix", List.of(first, second), generation++),
                List.of(), sonic.capability(), false).tracks().isEmpty());
        assertTrue(generator.generate(new StationDefinition(CinemarrPayloads.StationType.SONIC_ADVENTURE, "Adventure", List.of(first, second), generation++),
                List.of(), sonic.capability(), false).adventurePath());

        StationDefinition fallbackRadio = new StationDefinition(CinemarrPayloads.StationType.TRACK_RADIO, "Fallback Radio", List.of(first), generation++);
        assertThrows(PlexException.class, () -> generator.generate(fallbackRadio, List.of(), CinemarrPayloads.SonicCapability.ANALYSIS_INCOMPLETE, false));
        assertFalse(generator.generate(fallbackRadio, List.of(), CinemarrPayloads.SonicCapability.ANALYSIS_INCOMPLETE, true).tracks().isEmpty(),
                "Enabled metadata fallback returned no live-library tracks");

        java.util.ArrayList<QueueTrack> continuousHistory = new java.util.ArrayList<>(randomTracks.subList(0, Math.min(5, randomTracks.size())));
        for (int transition = 0; transition < 30; transition++) {
            StationGenerator.GeneratedBatch batch = generator.generate(new StationDefinition(CinemarrPayloads.StationType.AUTOPLAY, "Autoplay", List.of(), generation++),
                    continuousHistory, sonic.capability(), false);
            QueueTrack selected = batch.tracks().getFirst();
            assertTrue(continuousHistory.stream().noneMatch(previous -> previous.key().equals(selected.key())), "Autoplay repeated a recent track at transition " + transition);
            continuousHistory.add(selected);
            while (continuousHistory.size() > StationGenerator.TRACK_HISTORY_LIMIT) continuousHistory.removeFirst();
        }

        Path output = Files.createTempFile("cinemarr-live-", ".mp3");
        try {
            plex.transcode(track, output, 160);
            byte[] bytes = Files.readAllBytes(output);
            assertTrue(bytes.length > 1024, "Plex returned an empty transcode");
            assertFalse(Mp3FrameIndex.split(bytes).isEmpty(), "Plex response was not valid Layer III MP3");
            Mp3FrameIndex.Info info = Mp3FrameIndex.inspect(bytes);
            assertTrue(info.constantBitrate(), "Plex response was not constant-bitrate MP3 (range " + info.minimumBitrateKbps() + "-" + info.maximumBitrateKbps() + " kbps)");
            assertEquals(160, info.bitrateKbps(), "Plex did not honor the configured 160-kbps bitrate");
            assertEquals(2, info.channels(), "Plex did not return stereo MP3");
        } finally { Files.deleteIfExists(output); }

        for (QueueTrack candidate : tracks.stream().limit(5).toList()) {
            Path candidateOutput = Files.createTempFile("cinemarr-live-", ".mp3");
            try {
                plex.transcode(candidate, candidateOutput, 160);
                assertTrue(Files.size(candidateOutput) > 1024, "Plex returned an empty transcode for " + candidate.key());
            } catch (Exception failure) {
                throw new AssertionError("Plex transcode failed for track " + candidate.key() + " (" + candidate.title() + ")", failure);
            } finally { Files.deleteIfExists(candidateOutput); }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for the live smoke test");
        return value;
    }

    private static CinemarrPayloads.StationSeed seed(QueueTrack track) {
        return new CinemarrPayloads.StationSeed(CinemarrPayloads.ItemKind.TRACK, track.key(), track.title(), track.artist());
    }
}
