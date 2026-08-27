package stonytark.cinemarr.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyVideoManagerTest {
    @Test
    void parsesPlexMediaPlaylistIntoAuthoritativeTimeline() {
        List<LegacyVideoManager.SegmentReference> values = LegacyVideoManager.parsePlaylist(
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:2.500,\nsegment-0.ts\n#EXTINF:1.25,\nsegment-1.ts\n", 7_000);
        assertEquals(2, values.size());
        assertEquals("segment-0.ts", values.get(0).uri); assertEquals(7_000, values.get(0).pts); assertEquals(2_500, values.get(0).duration);
        assertEquals("segment-1.ts", values.get(1).uri); assertEquals(9_500, values.get(1).pts); assertEquals(1_250, values.get(1).duration);
    }

    @Test
    void rejectsPlaylistWithoutMediaSegments() {
        assertThrows(IllegalArgumentException.class, () -> LegacyVideoManager.parsePlaylist("#EXTM3U\n#EXT-X-ENDLIST\n", 0));
    }
}
