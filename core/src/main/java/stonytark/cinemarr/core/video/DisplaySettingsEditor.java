package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.screen.QuickTvPreset;

/** Shared, Java 8 UI state. Only an authoritative reply can complete an Apply. */
public final class DisplaySettingsEditor {
    private DisplaySettingsDraft draft;
    private TvDisplaySettings current, pending;
    private boolean canControl, custom, applied;
    private String width = "1920", height = "1080", error = "";
    private long sentAt;

    public void observe(TvDisplaySettings settings, boolean control, long now) {
        current = settings;
        canControl = control && settings != null;
        if (draft == null && settings != null) reset(settings);
        if (pending != null) {
            if (settings == null) fail("TV is no longer available");
            else if (settings.revision() > pending.revision()) {
                if (settings.revision() == pending.revision() + 1 && sameValues(settings, pending)) {
                    pending = null;
                    reset(settings);
                    applied = true;
                } else fail("Settings changed elsewhere; reopen this page");
            } else if (!canControl) fail("You no longer control this TV");
            else if (now - sentAt >= 10_000L) fail("No reply; reopen to check current settings");
        }
    }

    private void reset(TvDisplaySettings settings) {
        draft = new DisplaySettingsDraft(settings);
        custom = settings.resolution().kind() == ResolutionChoice.Kind.CUSTOM;
        if (settings.resolution().width() > 0) {
            width = Integer.toString(settings.resolution().width());
            height = Integer.toString(settings.resolution().height());
        }
    }
    private static boolean sameValues(TvDisplaySettings a, TvDisplaySettings b) {
        return a.origin() == b.origin() && a.layout() == b.layout()
                && a.mapping() == b.mapping() && a.resolution().equals(b.resolution());
    }
    public boolean editable() { return canControl && draft != null && pending == null && !applied; }
    public boolean qualityEditable() { return editable() && draft.original().origin() == TvDisplaySettings.Origin.CUSTOM; }
    public boolean customEditable() { return qualityEditable() && custom; }
    public boolean pending() { return pending != null; }
    public boolean applied() { return applied; }
    public String width() { return width; }
    public String height() { return height; }
    public void dimensions(String width, String height) { if (customEditable() && (!this.width.equals(width) || !this.height.equals(height))) { this.width = width; this.height = height; error = ""; } }
    public void nextLayout() {
        if (editable()) { PresentationMode[] values = PresentationMode.values(); draft.layout(values[(draft.layout().ordinal() + 1) % values.length]); error = ""; }
    }
    public void nextMapping() {
        if (qualityEditable()) { draft.mapping(draft.mapping() == PixelMapping.DETAILED ? PixelMapping.ONE_PIXEL_PER_BLOCK : PixelMapping.DETAILED); error = ""; }
    }
    public void nextResolution() {
        if (!qualityEditable()) return;
        QuickTvPreset[] presets = QuickTvPreset.values();
        if (custom) { custom = false; draft.resolution(ResolutionChoice.AUTO); }
        else if (draft.resolution().kind() == ResolutionChoice.Kind.AUTO) draft.resolution(ResolutionChoice.preset(presets[0].id()));
        else {
            int index = QuickTvPreset.byId(draft.resolution().preset()).ordinal() + 1;
            if (index == presets.length) custom = true;
            else draft.resolution(ResolutionChoice.preset(presets[index].id()));
        }
        error = "";
    }
    public TvDisplaySettings submit(long now) {
        if (!editable()) throw new IllegalStateException("Only the TV owner or an operator can apply settings");
        if (current.revision() != draft.original().revision()) throw new IllegalStateException("Settings changed elsewhere; reopen this page");
        if (custom) {
            if (!width.matches("[0-9]{1,4}") || !height.matches("[0-9]{1,4}"))
                throw new IllegalArgumentException("Enter whole dimensions from 2 to 8192");
            draft.resolution(ResolutionChoice.custom(Integer.parseInt(width), Integer.parseInt(height)));
        }
        TvDisplaySettings validated = draft.apply();
        // SET_DISPLAY carries the expected current revision, not the server's next revision.
        pending = new TvDisplaySettings(validated.origin(), validated.layout(), validated.mapping(), validated.resolution(), draft.original().revision());
        sentAt = now;
        error = "";
        return pending;
    }
    public void fail(String message) { pending = null; error = message == null || message.isEmpty() ? "Unable to apply settings" : message; }
    public String layoutLabel() { return "Layout: " + (draft == null ? "unavailable" : draft.layout().name()); }
    public String mappingLabel() { return "Mapping: " + (draft == null || draft.mapping() == PixelMapping.DETAILED ? "Detailed" : "One pixel per block"); }
    public String resolutionLabel() {
        if (draft == null) return "Quality: unavailable";
        ResolutionChoice choice = draft.resolution();
        return "Quality: " + (custom ? "Custom" : choice.kind() == ResolutionChoice.Kind.AUTO ? "Auto" : choice.preset());
    }
    public String message() {
        if (!error.isEmpty()) return error;
        if (pending != null) return "Applying settings...";
        if (current == null) return "TV state is unavailable";
        if (!canControl) return "Read only: owner or operator required";
        if (current.origin() == TvDisplaySettings.Origin.QUICK) return "Quick TV quality and mapping are locked";
        if (current.origin() == TvDisplaySettings.Origin.UNKNOWN) return "Load the controller to change quality";
        return "Quality is bounded by source, server and memory";
    }
}
