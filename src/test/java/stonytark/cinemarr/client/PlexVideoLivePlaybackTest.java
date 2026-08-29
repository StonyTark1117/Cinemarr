package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.server.HlsPlaylist;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.core.video.RenditionPolicy;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Credentialed real-Plex gate for the same metadata, HLS resolution, segment
 * fetch, and native decoder path used after the in-game controller selects a
 * video. Credentials remain process-environment only and are never printed.
 */
final class PlexVideoLivePlaybackTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "CINEMARR_LIVE_TEST", matches = "true")
    void controllerPlaybackPathDecodesRealPlexVideoAndAudio() throws Exception {
        PlexVideoService service = new PlexVideoService(required("CINEMARR_PLEX_URL"),
                required("CINEMARR_PLEX_TOKEN"));
        String section = environment("CINEMARR_LIVE_VIDEO_LIBRARY", "Movies");
        LibraryRule rule = new LibraryRule("live", section, "Live", true, true, "", 0);
        PlexVideoService.ResolvedLibrary library = service.resolveLibraries(Collections.singletonList(rule)).get(0);
        PlexVideoService.Page page = service.browse(library, "", "", 0, 20, 0);
        assertFalse(page.items().isEmpty(), "Configured Plex video library is empty");

        PlexVideoService.PlaybackMetadata metadata = service.metadataDetails(page.items().get(0).key());
        verifyPlayback(service, metadata, 0);
        if (metadata.item().durationMs() >= 120_000) {
            verifyPlayback(service, metadata, Math.min(290_000, metadata.item().durationMs() / 2));
        }
    }

    private static void verifyPlayback(PlexVideoService service, PlexVideoService.PlaybackMetadata metadata,
                                       long offsetMs) throws Exception {
        Integer audio = selectedStream(metadata, VideoStreamOption.Kind.AUDIO);
        Integer subtitle = selectedStream(metadata, VideoStreamOption.Kind.SUBTITLE);
        if (subtitle == null) subtitle = Integer.valueOf(0);
        PlexVideoService.VideoSession session = null;
        try {
            VideoMediaItem item = metadata.item();
            session = service.start(item, RenditionPolicy.chooseForScreen(16, 9, 256, 144,
                            1920, 1080, 1920, 1080),
                    offsetMs, audio, subtitle);
            PlexVideoService.MediaPlaylist playlist = service.mediaPlaylist(session, offsetMs);
            assertFalse(playlist.segments().isEmpty(), "Real Plex returned no playable HLS segments");
            HlsPlaylist.MediaSegment first = playlist.segments().get(0);
            byte[] bytes = service.fetch(session, playlist, first);
            DecodedMediaSegment decoded = new FfmpegVideoDecoder().decode(bytes);
            assertFalse(decoded.video().isEmpty(), "Real Plex segment contained no decodable H.264 video");
            assertFalse(decoded.audio().isEmpty(), "Real Plex segment contained no decodable AAC audio");
            assertTrue(decoded.video().get(0).width() > 0 && decoded.video().get(0).height() > 0,
                    "Real Plex decoded an invalid video frame");
        } finally {
            if (session != null) service.stop(session);
        }
    }

    private static Integer selectedStream(PlexVideoService.PlaybackMetadata metadata, VideoStreamOption.Kind kind) {
        for (VideoStreamOption stream : metadata.streams()) {
            if (stream.kind() == kind && stream.selected()) return Integer.valueOf(stream.id());
        }
        return null;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
