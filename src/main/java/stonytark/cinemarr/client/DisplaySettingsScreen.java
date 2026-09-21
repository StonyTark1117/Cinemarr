package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.DisplaySettingsEditor;
import stonytark.cinemarr.core.video.DisplaySettingsFeedback;
import stonytark.cinemarr.core.video.TvDisplaySettings;

/** 1.21.1 display adapter; draft and command ownership live in the shared editor. */
public final class DisplaySettingsScreen extends Screen implements DisplaySettingsFeedback {
    private final long controllerPos;
    private final CinemarrVideoClientState state;
    private final CinemarrVideoScreen parent;
    private final DisplaySettingsEditor editor = new DisplaySettingsEditor();
    private EditBox widthBox, heightBox;
    private Button layoutButton, mappingButton, qualityButton, applyButton, cancelButton;

    public DisplaySettingsScreen(long pos, CinemarrVideoClientState state, CinemarrVideoScreen parent) {
        super(Component.literal("Display Settings"));
        controllerPos = pos; this.state = state; this.parent = parent;
    }
    @Override protected void init() {
        boolean widthFocused = widthBox != null && widthBox.isFocused();
        boolean heightFocused = heightBox != null && heightBox.isFocused();
        observe();
        int left = Math.max(4, (width - 304) / 2);
        layoutButton = button(left, 28, 304, editor.layoutLabel(), () -> editor.nextLayout());
        mappingButton = button(left, 52, 304, editor.mappingLabel(), () -> editor.nextMapping());
        qualityButton = button(left, 76, 304, editor.resolutionLabel(), () -> editor.nextResolution());
        widthBox = field(widthBox, left + 40, editor.width(), "Width");
        heightBox = field(heightBox, left + 190, editor.height(), "Height");
        applyButton = button(left + 148, 212, 74, "Apply", () -> apply());
        cancelButton = button(left + 228, 212, 76, "Cancel", () -> back());
        refresh();
        if (stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display UI: width={} height={} editable={} qualityEditable={}", width, height, editor.editable(), editor.qualityEditable());
        if (widthFocused) setFocused(widthBox);
        else if (heightFocused) setFocused(heightBox);
    }
    private Button button(int x, int y, int w, String text, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> { action.run(); refresh(); }).bounds(x, y, w, 20).build());
    }
    private EditBox field(EditBox old, int x, String value, String label) {
        EditBox box = old;
        if (box == null) {
            box = new EditBox(font, x, 102, 108, 20, Component.literal(label));
            box.setMaxLength(4); box.setValue(value);
        } else { box.setX(x); box.setY(102); }
        addRenderableWidget(box);
        return box;
    }
    private void observe() {
        VideoPackets.SessionState current = state.session(controllerPos);
        editor.observe(current == null ? null : current.displaySettings(), current != null && current.canControl(), System.currentTimeMillis());
    }
    @Override public void tick() {
        editor.dimensions(widthBox.getValue(), heightBox.getValue());
        observe();
        if (editor.applied()) { back(); return; }
        refresh();
    }
    private void refresh() {
        if (layoutButton == null) return;
        layoutButton.setMessage(Component.literal(editor.layoutLabel())); layoutButton.active = editor.editable();
        mappingButton.setMessage(Component.literal(editor.mappingLabel())); mappingButton.active = editor.qualityEditable();
        qualityButton.setMessage(Component.literal(editor.resolutionLabel())); qualityButton.active = editor.qualityEditable();
        widthBox.active = heightBox.active = editor.customEditable();
        widthBox.setEditable(editor.customEditable()); heightBox.setEditable(editor.customEditable());
        applyButton.active = editor.editable(); cancelButton.active = !editor.pending();
    }
    private void apply() {
        try {
            observe(); editor.dimensions(widthBox.getValue(), heightBox.getValue());
            VideoPackets.SessionState current = state.session(controllerPos);
            TvDisplaySettings requested = editor.submit(System.currentTimeMillis());
            state.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_DISPLAY, controllerPos,
                    "", current.item() == null ? "" : current.item().key(), "", current.presentationMode(),
                    current.timelineGeneration(), CinemarrVideoPlayback.authoritativePositionMsLocal(current),
                    current.selectedAudioStreamId(), current.selectedSubtitleStreamId()).withDisplay(requested));
        } catch (RuntimeException failure) { showError(failure.getMessage()); }
    }
    @Override public void showError(String message) {
        editor.fail(message); refresh();
        if (stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display UI error: {}", editor.message());
    }
    private void back() { if (!editor.pending()) CinemarrClientUi.showScreen(parent); }
    @Override public void onClose() { back(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial);
        super.render(g, mx, my, partial);
        g.drawCenteredString(font, title, width / 2, 8, 0xffffffff);
        int left = Math.max(4, (width - 304) / 2);
        g.drawString(font, "W:", left + 18, 108, 0xffffffff, false);
        g.drawString(font, "H:", left + 168, 108, 0xffffffff, false);
        g.drawCenteredString(font, "Pixel mode: one color per screen block", width / 2, 130, 0xffa0d8ff);
        VideoPackets.SessionState current = state.session(controllerPos);
        String actual = current == null || current.effectiveWidth() == 0 ? "pending" : current.effectiveWidth() + "x" + current.effectiveHeight();
        String screen = current == null ? "unknown" : current.screenWidth() + "x" + current.screenHeight();
        g.drawCenteredString(font, "Actual: " + actual + "   Screen: " + screen, width / 2, 144, 0xffa0d8ff);
        String requested = editor.resolutionLabel() + (editor.customEditable() ? " " + widthBox.getValue() + "x" + heightBox.getValue() : "");
        g.drawCenteredString(font, requested, width / 2, 158, 0xffa0d8ff);
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(editor.message()), 304);
        for (int i = 0; i < Math.min(3, lines.size()); i++) g.drawCenteredString(font, lines.get(i), width / 2, 173 + i * 10, 0xffffb36b);
    }
}
