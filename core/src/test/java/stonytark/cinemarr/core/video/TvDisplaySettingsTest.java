package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import static org.junit.jupiter.api.Assertions.*;

class TvDisplaySettingsTest {
    @Test void customDefaultsAndRevisionsAreIndependentOfPlayback() {
        TvDisplaySettings initial = TvDisplaySettings.defaults(PresentationMode.FIT);
        assertEquals(PixelMapping.DETAILED, initial.mapping());
        assertEquals(ResolutionChoice.AUTO, initial.resolution());
        TvDisplaySettings changed = initial.apply(0, PresentationMode.FILL, PixelMapping.ONE_PIXEL_PER_BLOCK, ResolutionChoice.custom(17, 11));
        assertEquals(1, changed.revision());
        assertEquals(0, initial.revision());
        assertThrows(IllegalStateException.class, () -> changed.apply(0, PresentationMode.FIT, initial.mapping(), initial.resolution()));
    }
    @Test void allPresetsAndCustomBoundsValidateBeforeAllocation() {
        for (QuickTvPreset preset : QuickTvPreset.values()) assertEquals(preset.renditionWidth(), ResolutionChoice.preset(preset.id()).width());
        assertEquals(8192, ResolutionChoice.custom(8192, 2).width());
        for (int bad : new int[]{Integer.MIN_VALUE, -1, 0, 1, 8193, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> ResolutionChoice.custom(bad, 2));
        assertThrows(IllegalArgumentException.class, () -> ResolutionChoice.custom(8192, 8192));
        assertThrows(IllegalArgumentException.class, () -> ResolutionChoice.preset("bogus"));
    }
    @Test void quickQualityIsLockedButLayoutCanChange() {
        TvDisplaySettings quick = new TvDisplaySettings(TvDisplaySettings.Origin.QUICK, PresentationMode.FIT,
                PixelMapping.DETAILED, ResolutionChoice.preset("1080p"), 0);
        assertEquals(PresentationMode.FILL, quick.apply(0, PresentationMode.FILL, quick.mapping(), quick.resolution()).layout());
        assertThrows(IllegalArgumentException.class, () -> quick.apply(0, quick.layout(), PixelMapping.ONE_PIXEL_PER_BLOCK, quick.resolution()));
        assertThrows(IllegalArgumentException.class, () -> quick.apply(0, quick.layout(), quick.mapping(), ResolutionChoice.AUTO));
        TvDisplaySettings unknown = new TvDisplaySettings(TvDisplaySettings.Origin.UNKNOWN, quick.layout(), quick.mapping(), ResolutionChoice.AUTO, 0);
        assertThrows(IllegalStateException.class, () -> unknown.apply(0, quick.layout(), PixelMapping.ONE_PIXEL_PER_BLOCK, ResolutionChoice.AUTO));
    }
}
