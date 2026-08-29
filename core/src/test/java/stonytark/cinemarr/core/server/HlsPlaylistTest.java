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

    @Test void keepsAPlaylistThatPlexAlreadyRebasedAtTheRequestedOffset() {
        String playlist = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:40\n#EXTINF:8,\nmedia-40.ts\n#EXTINF:8,\nmedia-41.ts\n";
        java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist, 290_000);
        assertEquals(2, segments.size());
        assertEquals("media-40.ts", segments.get(0).uri());
        assertEquals(290_000, segments.get(0).presentationTimeMs());
    }

    @Test void keepsLongSeekRelativeWindowsWhenMediaSequenceIsNonZero() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:2\n");
        for (int index = 2; index < 12; index++) playlist.append("#EXTINF:2,\nmedia-").append(index).append(".ts\n");
        java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist.toString(), 10_000);
        assertEquals(10, segments.size());
        assertEquals("media-2.ts", segments.get(0).uri());
        assertEquals(10_000, segments.get(0).presentationTimeMs());
    }

    @Test void distinguishesMasterAndMediaPlaylistsAndSelectsARealVariantLine() {
        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000\nnested/media.m3u8\n";
        assertEquals(false, HlsPlaylist.isMediaPlaylist(master));
        assertEquals("nested/media.m3u8", HlsPlaylist.firstVariantReference(master));
        assertEquals(true, HlsPlaylist.isMediaPlaylist("#EXTM3U\n#EXTINF:2,\nsegment.ts\n"));
    }

    @Test void rejectsMediaReferencesWithoutPositiveDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> HlsPlaylist.mediaSegments("#EXTM3U\nsegment.ts\n", 0));
        assertThrows(IllegalArgumentException.class,
                () -> HlsPlaylist.mediaSegments("#EXTM3U\n#EXTINF:0,\nsegment.ts\n", 0));
        assertThrows(IllegalArgumentException.class,
                () -> HlsPlaylist.mediaSegments("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:-1\n#EXTINF:2,\nsegment.ts\n", 0));
    }
}
