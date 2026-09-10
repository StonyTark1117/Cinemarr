package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplaySettingsDraftTest {
    @Test void applyAndCancelKeepRevisionedAtomicDraftSeparateFromOriginal() {
        TvDisplaySettings original = TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsDraft draft = new DisplaySettingsDraft(original).layout(PresentationMode.FILL)
                .mapping(PixelMapping.ONE_PIXEL_PER_BLOCK).resolution(ResolutionChoice.custom(320, 180));
        assertTrue(draft.dirty()); assertEquals(0, original.revision());
        TvDisplaySettings applied = draft.apply();
        assertEquals(1, applied.revision()); assertEquals(PresentationMode.FILL, applied.layout());
        draft.cancel(); assertEquals(PresentationMode.FIT, draft.layout()); assertFalse(draft.dirty());
    }

    @Test void quickTvLocksMappingAndResolutionButAllowsLayout() {
        TvDisplaySettings quick = new TvDisplaySettings(TvDisplaySettings.Origin.QUICK, PresentationMode.FIT,
                PixelMapping.DETAILED, ResolutionChoice.preset("144p"), 3);
        DisplaySettingsDraft draft = new DisplaySettingsDraft(quick).mapping(PixelMapping.ONE_PIXEL_PER_BLOCK);
        assertFalse(draft.dirty()); assertTrue(draft.error().contains("locked"));
        draft.cancel(); draft.resolution(ResolutionChoice.preset("1080p"));
        assertFalse(draft.dirty()); assertTrue(draft.error().contains("locked"));
        assertDoesNotThrow(() -> draft.layout(PresentationMode.STRETCH).apply());
    }

    @Test void malformedAndOversizedCustomDimensionsAreRejectedBeforeDraftMutation() {
        DisplaySettingsDraft draft = new DisplaySettingsDraft(TvDisplaySettings.defaults(PresentationMode.FIT));
        assertThrows(IllegalArgumentException.class, () -> ResolutionChoice.custom(1, 200));
        assertThrows(IllegalArgumentException.class, () -> ResolutionChoice.custom(8193, 2));
        assertFalse(draft.dirty());
    }
}
