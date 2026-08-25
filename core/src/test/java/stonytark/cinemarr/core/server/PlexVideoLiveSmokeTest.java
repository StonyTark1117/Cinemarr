package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.library.VideoMediaItem;
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
        VideoMediaItem item = page.items().get(0);
        PlexVideoService.VideoSession session = null;
        try {
            session = service.start(item, RenditionPolicy.choose(320, 180, 1920, 1080, 640, 360), 0, null, null);
            String reference = firstReference(session.playlist());
            byte[] fetched = service.fetch(session, reference);
            if (reference.contains(".m3u8")) fetched = service.fetch(session, reference,
                    firstReference(new String(fetched, StandardCharsets.UTF_8)));
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
    private static String environment(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
