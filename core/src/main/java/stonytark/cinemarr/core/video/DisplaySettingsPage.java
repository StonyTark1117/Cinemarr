package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.QuickTvPreset;

/** Persistent UI model shared by every Minecraft GUI adapter, including Java 8. */
public final class DisplaySettingsPage {
    private DisplaySettingsDraft draft;
    private VideoPackets.SessionState current;
    private TvDisplaySettings pending;
    private long sentAt;
    private String width = "1920", height = "1080", message = "";
    private boolean custom;

    public DisplaySettingsPage(VideoPackets.SessionState state) { reload(state); }
    public void reload(VideoPackets.SessionState state) {
        current = state;
        draft = new DisplaySettingsDraft(state == null ? TvDisplaySettings.defaults(PresentationMode.FIT) : state.displaySettings());
        custom = draft.resolution().kind() == ResolutionChoice.Kind.CUSTOM;
        if (draft.resolution().width() > 0) {
            width = Integer.toString(draft.resolution().width()); height = Integer.toString(draft.resolution().height());
        }
        pending = null; message = "";
    }
    public void update(VideoPackets.SessionState state, long now) {
        current = state;
        if (pending == null) return;
        if (state == null) { fail("TV is no longer available"); return; }
        if (!state.canControl()) { fail("You no longer control this TV"); return; }
        if (state.displaySettings().revision() > pending.revision()) {
            TvDisplaySettings received = state.displaySettings();
            if (received.revision() == pending.revision() + 1 && received.origin() == pending.origin() && received.layout() == pending.layout()
                    && received.mapping() == pending.mapping() && received.resolution().equals(pending.resolution())) {
                reload(state); message = "Saved";
            } else fail("Settings changed; Reload before applying");
        } else if (now - sentAt >= 10000) fail("No acknowledgement; Reload to check settings");
    }
    public boolean editable() { return current != null && current.canControl() && pending == null; }
    public boolean qualityEditable() { return editable() && draft.original().origin() == TvDisplaySettings.Origin.CUSTOM; }
    public boolean customEditable() { return qualityEditable() && custom; }
    public boolean pending() { return pending != null; }
    public String width() { return width; }
    public String height() { return height; }
    public void dimensions(String w, String h) { width = w; height = h; }
    public String layoutLabel() { return "Layout: " + draft.layout().name(); }
    public String mappingLabel() { return draft.mapping() == PixelMapping.DETAILED ? "Detailed" : "One pixel per block"; }
    public String resolutionLabel() { return "Quality: " + (custom ? "Custom" : draft.resolution()); }
    public String requestedLabel() { return "Requested: " + (custom ? width + "x" + height : draft.resolution()); }
    public String message() {
        if (pending != null) return "Applying...";
        if (!message.isEmpty()) return message;
        if (current == null) return "TV state is unavailable";
        if (!current.canControl()) return "Read only: owner or operator required";
        if (draft.original().origin() == TvDisplaySettings.Origin.QUICK) return "Quick TV quality is fixed by its preset";
        if (draft.original().origin() == TvDisplaySettings.Origin.UNKNOWN) return "Load controller to change quality";
        return "";
    }
    public void cycleLayout() {
        if (editable()) draft.layout(PresentationMode.values()[(draft.layout().ordinal()+1)%PresentationMode.values().length]);
    }
    public void cycleMapping() {
        if (qualityEditable()) draft.mapping(draft.mapping() == PixelMapping.DETAILED ? PixelMapping.ONE_PIXEL_PER_BLOCK : PixelMapping.DETAILED);
    }
    public void cycleResolution() {
        if (!qualityEditable()) return;
        if (custom) { custom = false; draft.resolution(ResolutionChoice.AUTO); return; }
        QuickTvPreset[] presets = QuickTvPreset.values();
        if (draft.resolution().kind() == ResolutionChoice.Kind.AUTO) { draft.resolution(ResolutionChoice.preset(presets[0].id())); return; }
        int index = QuickTvPreset.byId(draft.resolution().preset()).ordinal()+1;
        if (index == presets.length) custom = true;
        else draft.resolution(ResolutionChoice.preset(presets[index].id()));
    }
    public VideoPackets.SessionCommand apply(long pos, long now) {
        if (!editable()) return null;
        try {
            if (current.displaySettings().revision() != draft.original().revision())
                throw new IllegalStateException("Settings changed; Reload before applying");
            if (custom) draft.resolution(ResolutionChoice.custom(Integer.parseInt(width.trim()), Integer.parseInt(height.trim())));
            TvDisplaySettings requested = draft.apply();
            pending = requested; sentAt = now; message = "";
            return new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_DISPLAY, pos, "", "", "",
                    requested.layout(), current.timelineGeneration(), 0, -1, -1).withDisplay(requested);
        } catch (RuntimeException invalid) {
            fail(invalid instanceof NumberFormatException ? "Enter whole numbers from 2 to 8192" : invalid.getMessage());
            return null;
        }
    }
    public void fail(String error) { pending = null; message = error == null ? "Unable to apply settings" : error; }
}
