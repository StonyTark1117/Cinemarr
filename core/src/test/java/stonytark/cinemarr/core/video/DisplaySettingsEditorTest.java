package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import static org.junit.jupiter.api.Assertions.*;

class DisplaySettingsEditorTest {
    private DisplaySettingsEditor editor(TvDisplaySettings settings, boolean control) {
        DisplaySettingsEditor editor = new DisplaySettingsEditor();
        editor.observe(settings, control, 0);
        return editor;
    }
    @Test void sendsExpectedRevisionAndWaitsForAuthoritativeAcknowledgement() {
        TvDisplaySettings original = TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsEditor editor = editor(original, true);
        editor.nextLayout(); editor.nextMapping();
        TvDisplaySettings request = editor.submit(10);
        assertEquals(original.revision(), request.revision());
        assertTrue(editor.pending()); assertFalse(editor.applied()); assertFalse(editor.editable());
        editor.observe(original, true, 20);
        assertTrue(editor.pending());
        TvDisplaySettings accepted = original.apply(request.revision(), request.layout(), request.mapping(), request.resolution());
        editor.observe(accepted, true, 30);
        assertTrue(editor.applied()); assertFalse(editor.pending());
    }
    @Test void allPresetsAndCustomAreReachableAndInvalidInputCannotSendOldQuality() {
        DisplaySettingsEditor editor = editor(TvDisplaySettings.defaults(PresentationMode.FIT), true);
        for (QuickTvPreset preset : QuickTvPreset.values()) {
            editor.nextResolution(); assertEquals("Quality: " + preset.id(), editor.resolutionLabel());
        }
        editor.nextResolution(); assertTrue(editor.customEditable());
        editor.dimensions("oops", "180");
        assertThrows(IllegalArgumentException.class, () -> editor.submit(0)); assertFalse(editor.pending());
        editor.dimensions("8193", "180");
        assertThrows(IllegalArgumentException.class, () -> editor.submit(0));
        editor.dimensions("320", "180");
        assertEquals(ResolutionChoice.custom(320, 180), editor.submit(0).resolution());
    }
    @Test void permissionAndQuickLocksAreAppliedBeforeMutation() {
        TvDisplaySettings original = TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsEditor viewer = editor(original, false);
        viewer.nextLayout(); assertEquals("Layout: FIT", viewer.layoutLabel());
        assertThrows(IllegalStateException.class, () -> viewer.submit(0));
        TvDisplaySettings quick = new TvDisplaySettings(TvDisplaySettings.Origin.QUICK, PresentationMode.FIT,
                PixelMapping.DETAILED, ResolutionChoice.preset("144p"), 7);
        DisplaySettingsEditor editor = editor(quick, true);
        editor.nextMapping(); editor.nextResolution(); editor.nextLayout();
        TvDisplaySettings request = editor.submit(0);
        assertEquals(7, request.revision()); assertEquals(quick.resolution(), request.resolution());
        assertEquals(PixelMapping.DETAILED, request.mapping()); assertEquals(PresentationMode.FILL, request.layout());
    }
    @Test void stateRefreshPreservesDraftButConflictsFailBeforeDispatch() {
        TvDisplaySettings original = TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsEditor editor = editor(original, true);
        editor.nextLayout(); editor.observe(original, true, 1);
        assertEquals("Layout: FILL", editor.layoutLabel());
        editor.observe(original.apply(0, PresentationMode.STRETCH, PixelMapping.DETAILED, ResolutionChoice.AUTO), true, 2);
        assertThrows(IllegalStateException.class, () -> editor.submit(3));
        assertFalse(editor.pending());
    }
    @Test void errorsTimeoutRemovalAndConflictingAcknowledgementsDoNotReportSuccess() {
        TvDisplaySettings original = TvDisplaySettings.defaults(PresentationMode.FIT);
        DisplaySettingsEditor editor = editor(original, true);
        editor.submit(0); editor.fail("Request rejected");
        assertFalse(editor.pending()); assertFalse(editor.applied()); assertEquals("Request rejected", editor.message());
        editor.submit(0); editor.observe(original, true, 10_000);
        assertFalse(editor.applied()); assertTrue(editor.message().contains("No reply"));
        editor.submit(11_000); editor.observe(null, false, 11_001);
        assertFalse(editor.pending()); assertFalse(editor.editable());
        editor.observe(original, true, 12_000); editor.submit(12_000);
        editor.observe(original.apply(0, PresentationMode.FILL, PixelMapping.DETAILED, ResolutionChoice.AUTO), true, 12_001);
        assertFalse(editor.applied()); assertTrue(editor.message().contains("elsewhere"));
    }
}
