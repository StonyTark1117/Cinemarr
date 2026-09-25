package stonytark.cinemarr.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyVideoManagerTest {
    @Test
    void parsesPlexMediaPlaylistIntoAuthoritativeTimeline() {
        List<stonytark.cinemarr.core.server.ActiveVideoMedia.SegmentReference> values = LegacyVideoManager.parsePlaylist(
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:2.500, nodesc\nsegment-0.ts\n#EXTINF:1.25,\nsegment-1.ts\n", 0);
        assertEquals(2, values.size());
        assertEquals("segment-0.ts", values.get(0).uri); assertEquals(0, values.get(0).pts); assertEquals(2_500, values.get(0).duration);
        assertEquals("segment-1.ts", values.get(1).uri); assertEquals(2_500, values.get(1).pts); assertEquals(1_250, values.get(1).duration);
    }

    @Test
    void skipsPlexPlaceholdersWhilePreservingOriginalSegmentBoundaries() {
        StringBuilder playlist = new StringBuilder("#EXTM3U\n");
        for (int index = 0; index < 40; index++) playlist.append("#EXTINF:8, nodesc\nsegment-").append(index).append(".ts\n");
        List<stonytark.cinemarr.core.server.ActiveVideoMedia.SegmentReference> values = LegacyVideoManager.parsePlaylist(playlist.toString(), 290_000);
        assertEquals("segment-36.ts", values.get(0).uri);
        // Segment 36 contains the requested time but begins at 36 * 8 seconds.
        assertEquals(288_000, values.get(0).pts);
        assertEquals(8_000, values.get(0).duration);
        assertEquals("segment-37.ts", values.get(1).uri);
        assertEquals(296_000, values.get(1).pts);
        assertEquals(4, values.size());
    }

    @Test
    void rejectsPlaylistWithoutMediaSegments() {
        assertThrows(IllegalArgumentException.class, () -> LegacyVideoManager.parsePlaylist("#EXTM3U\n#EXT-X-ENDLIST\n", 0));
    }
}
