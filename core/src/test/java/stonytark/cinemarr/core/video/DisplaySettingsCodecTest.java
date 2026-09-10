package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.screen.QuickTvPreset;

import static org.junit.jupiter.api.Assertions.*;

class DisplaySettingsCodecTest {
    @Test void roundTripsEveryLayoutMappingAndResolutionChoice() {
        for (PresentationMode layout : PresentationMode.values()) {
            for (PixelMapping mapping : PixelMapping.values()) {
                check(new TvDisplaySettings(TvDisplaySettings.Origin.CUSTOM, layout, mapping, ResolutionChoice.AUTO, 0));
                check(new TvDisplaySettings(TvDisplaySettings.Origin.CUSTOM, layout, mapping, ResolutionChoice.custom(17, 11), 42));
                for (QuickTvPreset preset : QuickTvPreset.values())
                    check(new TvDisplaySettings(TvDisplaySettings.Origin.CUSTOM, layout, mapping, ResolutionChoice.preset(preset.id()), Long.MAX_VALUE));
            }
            for (QuickTvPreset preset : QuickTvPreset.values())
                check(new TvDisplaySettings(TvDisplaySettings.Origin.QUICK, layout, PixelMapping.DETAILED, ResolutionChoice.preset(preset.id()), 1));
            check(new TvDisplaySettings(TvDisplaySettings.Origin.UNKNOWN, layout, PixelMapping.DETAILED, ResolutionChoice.AUTO, 0));
        }
    }

    @Test void rejectsMalformedEnumsDimensionsRevisionsAndQuickQuality() {
        String canonical = "1|CUSTOM|FIT|DETAILED|AUTO||0|0|0";
        String[] fields = canonical.split("\\|", -1);
        String[][] invalid = {{"2", ""}, {"ALIEN"}, {"CROP"}, {"PIXELS"}, {"OTHER"}, {"1080p"}, {"-1", "9999999999999"}, {"2"}, {"-1", "9223372036854775808"}};
        for (int i = 0; i < fields.length; i++) {
            for (String replacement : invalid[i]) {
                String[] changed = fields.clone(); changed[i] = replacement;
                assertThrows(IllegalArgumentException.class, () -> DisplaySettingsCodec.decode(String.join("|", changed)));
            }
        }
        for (String value : new String[] {null, "", canonical + "|extra",
                "1|CUSTOM|FIT|DETAILED|CUSTOM||1|2|0",
                "1|CUSTOM|FIT|DETAILED|CUSTOM||8192|8192|0",
                "1|CUSTOM|FIT|DETAILED|PRESET|1080p|2|2|0",
                "1|QUICK|FIT|DETAILED|AUTO||0|0|0",
                "1|QUICK|FIT|ONE_PIXEL_PER_BLOCK|PRESET|1080p|1920|1080|0"})
            assertThrows(IllegalArgumentException.class, () -> DisplaySettingsCodec.decode(value));
        assertThrows(IllegalArgumentException.class, () -> DisplaySettingsCodec.decode(new String(new char[DisplaySettingsCodec.MAX_LENGTH + 1])));
    }

    @Test void corruptSavedDataDefersClassificationAndPreservesLegacyLayout() {
        for (PresentationMode layout : PresentationMode.values()) {
            for (String invalid : new String[] {null, "", "corrupt"}) {
                TvDisplaySettings loaded = DisplaySettingsCodec.load(invalid, layout);
                assertEquals(TvDisplaySettings.Origin.UNKNOWN, loaded.origin());
                assertEquals(layout, loaded.layout());
                assertEquals(PixelMapping.DETAILED, loaded.mapping());
                assertEquals(ResolutionChoice.AUTO, loaded.resolution());
                assertEquals(0, loaded.revision());
            }
        }
    }

    @Test void exhaustedRevisionCannotWrapOrMutateSettings() {
        TvDisplaySettings value = new TvDisplaySettings(TvDisplaySettings.Origin.CUSTOM, PresentationMode.FIT,
                PixelMapping.DETAILED, ResolutionChoice.AUTO, Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, () -> value.apply(Long.MAX_VALUE, PresentationMode.FILL,
                PixelMapping.DETAILED, ResolutionChoice.AUTO));
        assertEquals(Long.MAX_VALUE, value.revision());
        assertEquals(PresentationMode.FIT, value.layout());
    }

    private static void check(TvDisplaySettings value) {
        String encoded = DisplaySettingsCodec.encode(value);
        assertTrue(encoded.length() <= DisplaySettingsCodec.MAX_LENGTH);
        TvDisplaySettings decoded = DisplaySettingsCodec.decode(encoded);
        assertEquals(value.origin(), decoded.origin());
        assertEquals(value.layout(), decoded.layout());
        assertEquals(value.mapping(), decoded.mapping());
        assertEquals(value.resolution(), decoded.resolution());
        assertEquals(value.revision(), decoded.revision());
        assertEquals(encoded, DisplaySettingsCodec.encode(decoded));
    }
}
