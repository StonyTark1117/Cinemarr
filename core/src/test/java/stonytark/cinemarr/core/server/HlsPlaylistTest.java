package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HlsPlaylistTest {
    @Test void effectiveDimensionsComeFromSelectedMediaNotRequestedBounds() {
        assertArrayEquals(new int[] {160, 90}, HlsPlaylist.firstVariantDimensions(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100,RESOLUTION=160x90,CODECS=avc1\nsmall.m3u8\n"
                + "#EXT-X-STREAM-INF:RESOLUTION=1920x1080\nlarge.m3u8\n"));
        assertArrayEquals(new int[] {0, 0}, HlsPlaylist.firstVariantDimensions(
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\nmedia.m3u8\n"));
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.firstVariantDimensions(
                "#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=999999999x999999999\nmedia.m3u8\n"));
    }

    @Test void parsesDurationsWithEmptyAndDescriptiveTitles() {
        assertEquals(2_500, HlsPlaylist.durationMillis("#EXTINF:2.500,"));
        assertEquals(8_000, HlsPlaylist.durationMillis("#EXTINF:8, nodesc"));
    }

    @Test void rejectsMalformedOrNegativeDurations() {
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.durationMillis("#EXTINF:nodesc"));
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.durationMillis("#EXTINF:-1,"));
        assertThrows(IllegalArgumentException.class, () -> HlsPlaylist.durationMillis("#EXT-X-ENDLIST"));
    }

    @Test void removesPlexPreSeekPlaceholdersWithoutMovingTheRetainedSegment() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int index = 0; index < 40; index++) playlist.append("#EXTINF:8, nodesc\nmedia-").append(index).append(".ts\n");
        java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist.toString(), 290_000);
        assertEquals("media-36.ts", segments.get(0).uri());
        assertEquals(288_000, segments.get(0).presentationTimeMs());
        assertEquals(296_000, segments.get(1).presentationTimeMs());
        assertEquals(4, segments.size());
    }

    @Test void requestsInsideOneVodSegmentKeepTheSameContentAnchor() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:0\n");
        for (int index = 0; index < 6; index++) playlist.append("#EXTINF:8,\nmedia-").append(index).append(".ts\n");
        for (long offset : new long[] {0, 1000, 7999, 8000, 8050, 31_000, 31_999, 32_000}) {
            java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist.toString(), offset);
            long segmentStart = offset / 8000 * 8000;
            assertEquals("media-" + offset / 8000 + ".ts", segments.get(0).uri());
            assertEquals(segmentStart, segments.get(0).presentationTimeMs(),
                    "Seeking inside a segment must not relabel earlier content as the requested time");
            assertEquals(segmentStart + 8000, segments.get(1).presentationTimeMs());
        }
    }

    @Test void variableDurationVodSegmentsKeepTheirCumulativeTimeline() {
        String playlist = "#EXTM3U\n#EXTINF:2.5,\na.ts\n#EXTINF:5.5,\nb.ts\n#EXTINF:3.25,\nc.ts\n";
        java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist, 7500);
        assertEquals("b.ts", segments.get(0).uri());
        assertEquals(2500, segments.get(0).presentationTimeMs());
        assertEquals(8000, segments.get(1).presentationTimeMs());
    }

    @Test void preservesShortSeekRelativeFallbackWithoutAnAbsoluteTimeline() {
        String playlist = "#EXTM3U\n#EXTINF:8,\na.ts\n#EXTINF:8,\nb.ts\n";
        java.util.List<HlsPlaylist.MediaSegment> segments = HlsPlaylist.mediaSegments(playlist, 290_000);
        assertEquals(2, segments.size());
        assertEquals(290_000, segments.get(0).presentationTimeMs());
        assertEquals(298_000, segments.get(1).presentationTimeMs());
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
