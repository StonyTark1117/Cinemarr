package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.video.RenditionPolicy;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in credentialed smoke; secrets are environment-only and never build inputs or output. */
class PlexVideoLiveSmokeTest {
    @Test void startsFetchesAndStopsRealVideoTranscode() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("CINEMARR_LIVE_TEST")));
        String url = System.getenv("CINEMARR_PLEX_URL");
        String token = System.getenv("CINEMARR_PLEX_TOKEN");
        String section = environment("CINEMARR_LIVE_VIDEO_LIBRARY", "Movies");
        PlexVideoService service = new PlexVideoService(url, token);
        LibraryRule rule = new LibraryRule("live", section, "Live", true, true, "", 0);
        PlexVideoService.ResolvedLibrary library = service.resolveLibraries(Collections.singletonList(rule)).get(0);
        PlexVideoService.Page page = service.browse(library, "", "", 0, 10, 0);
        assertFalse(page.items().isEmpty(), "Configured Plex video library is empty");
        PlexVideoService.PlaybackMetadata metadata = service.metadataDetails(page.items().get(0).key());
        VideoMediaItem item = metadata.item();
        PlexVideoService.VideoSession session = null;
        try {
            session = service.start(item, RenditionPolicy.choose(320, 180, 1920, 1080, 640, 360), 0,
                    selectedStream(metadata, VideoStreamOption.Kind.AUDIO), selectedStream(metadata, VideoStreamOption.Kind.SUBTITLE));
            String reference = firstReference(session.playlist());
            byte[] fetched = service.fetch(session, reference);
            if (reference.contains(".m3u8")) {
                String mediaPlaylist = new String(fetched, StandardCharsets.UTF_8);
                assertTrue(firstDuration(mediaPlaylist) > 0, "Plex returned a non-positive media segment duration");
                fetched = service.fetch(session, reference, firstReference(mediaPlaylist));
            }
            assertTrue(fetched.length > 0, "Plex returned an empty first media segment");
        } finally {
            if (session != null) service.stop(session);
        }
    }

    private static String firstReference(String playlist) {
        for (String line : playlist.split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isEmpty() && !value.startsWith("#")) return value;
        }
        throw new IllegalArgumentException("Playlist contained no media reference");
    }
    private static long firstDuration(String playlist) {
        for (String line : playlist.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith("#EXTINF:")) return HlsPlaylist.durationMillis(value);
        }
        throw new IllegalArgumentException("Playlist contained no media duration");
    }
    private static Integer selectedStream(PlexVideoService.PlaybackMetadata metadata, VideoStreamOption.Kind kind) {
        for (VideoStreamOption stream : metadata.streams()) {
            if (stream.kind() == kind && stream.selected()) return Integer.valueOf(stream.id());
        }
        return null;
    }
    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
