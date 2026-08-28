package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HlsPlaylistTest {
    @Test void parsesDurationsWithEmptyAndDescriptiveTitles() {
        assertEquals(2_500, HlsPlaylist.durationMillis("#EXTINF:2.500,"));
        assertEquals(8_000, HlsPlaylist.durationMillis("#EXTINF:8, nodesc"));
    }

    @Test void rejectsMalformedOrNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.durationMillis("#EXTINF:nodesc"));
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.durationMillis("#EXTINF:-1,"));
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.durationMillis("#EXT-X-ENDLIST"));
    }

    @Test void removesPlexPreSeekPlaceholderEntriesAndRebasesPresentationTime() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int index = 0; index < 40; index++) playlist.append("#EXTINF:8, nodesc\nmedia-").append(index).append(".ts\n");
        java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist.toString(), 290_000);
        assertEquals("media-36.ts", segments.get(0).uri());
        assertEquals(290_000, segments.get(0).presentationTimeMs());
        assertEquals(298_000, segments.get(1).presentationTimeMs());
        assertEquals(4, segments.size());
    }
}
