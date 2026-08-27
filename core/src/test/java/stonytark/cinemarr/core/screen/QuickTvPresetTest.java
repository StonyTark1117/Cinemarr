package stonytark.cinemarr.core.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuickTvPresetTest {
    @Test void exposesEveryRequestedResolutionWithBoundedPhysicalConstruction() {
        assertEquals(8, QuickTvPreset.values().length);
        assertEquals(QuickTvPreset.P144, QuickTvPreset.byId("144p"));
        assertEquals(QuickTvPreset.P4K, QuickTvPreset.byId("4K"));
        assertEquals(7_680, QuickTvPreset.P8K.renditionWidth());
        for (QuickTvPreset preset : QuickTvPreset.values()) {
            assertTrue(preset.physicalPixels() <= 65_536);
            assertTrue(preset.physicalWidth() <= 2_048);
            assertTrue(preset.physicalHeight() <= 2_048);
        }
    }

    @Test void recipesAndFootprintsGrowWithTheNamedTier() {
        int pixels = 0;
        int tier = 0;
        for (QuickTvPreset preset : QuickTvPreset.values()) {
            assertTrue(preset.physicalPixels() > pixels);
            assertTrue(preset.resourceTier() > tier);
            pixels = preset.physicalPixels();
            tier = preset.resourceTier();
        }
    }

    @Test void producesStableConfigurationKeys() {
        assertEquals("quickTv144pEnabled", QuickTvPreset.P144.configKey());
        assertEquals("quickTv1080pEnabled", QuickTvPreset.P1080.configKey());
        assertEquals("quickTv4KEnabled", QuickTvPreset.P4K.configKey());
        assertEquals("quickTv8KEnabled", QuickTvPreset.P8K.configKey());
    }
}
