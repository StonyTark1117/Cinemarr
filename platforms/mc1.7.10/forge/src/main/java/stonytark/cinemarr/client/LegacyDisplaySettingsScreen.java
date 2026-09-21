package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.video.DisplaySettingsEditor;
import stonytark.cinemarr.core.video.DisplaySettingsFeedback;
import stonytark.cinemarr.core.video.TvDisplaySettings;

/** Java 8/LWJGL 2 adapter for the shared revisioned settings editor. */
final class LegacyDisplaySettingsScreen extends GuiScreen implements DisplaySettingsFeedback {
    private final long controllerPos;
    private final LegacyVideoClientState state;
    private final LegacyVideoScreen parent;
    private final DisplaySettingsEditor editor = new DisplaySettingsEditor();
    private GuiTextField widthBox, heightBox;
    private GuiButton layoutButton, mappingButton, qualityButton, applyButton, cancelButton;

    LegacyDisplaySettingsScreen(long pos, LegacyVideoClientState state, LegacyVideoScreen parent) {
        controllerPos = pos; this.state = state; this.parent = parent;
    }
    @Override public void initGui() {
        observe(); Keyboard.enableRepeatEvents(true); buttonList.clear();
        int left = Math.max(4, (width - 304) / 2);
        layoutButton = button(0, left, 28, 304, editor.layoutLabel());
        mappingButton = button(1, left, 52, 304, editor.mappingLabel());
        qualityButton = button(2, left, 76, 304, editor.resolutionLabel());
        widthBox = field(widthBox, left + 40, editor.width());
        heightBox = field(heightBox, left + 190, editor.height());
        applyButton = button(3, left + 148, 212, 74, "Apply");
        cancelButton = button(4, left + 228, 212, 76, "Cancel");
        refresh();
        if (stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display UI: width={} height={} editable={} qualityEditable={}", width, height, editor.editable(), editor.qualityEditable());
    }
    private GuiButton button(int id, int x, int y, int w, String text) {
        GuiButton button = new LegacyVideoButton(id, x, y, w, 20, text);
        buttonList.add(button); return button;
    }
    private GuiTextField field(GuiTextField old, int x, String value) {
        LegacyTextFieldState saved = old == null ? null : LegacyTextFieldState.capture(old);
        GuiTextField box = new GuiTextField(fontRendererObj, x, 102, 108, 20);
        box.setMaxStringLength(4); box.setText(value);
        if (saved != null) saved.restore(box);
        return box;
    }
    private void observe() {
        VideoPackets.SessionState current = state.session(controllerPos);
        editor.observe(current == null ? null : current.displaySettings(), current != null && current.canControl(), System.currentTimeMillis());
    }
    @Override public void updateScreen() {
        editor.dimensions(widthBox.getText(), heightBox.getText()); observe();
        if (editor.applied()) { back(); return; }
        widthBox.updateCursorCounter(); heightBox.updateCursorCounter(); refresh();
    }
    private void refresh() {
        if (layoutButton == null) return;
        layoutButton.displayString = editor.layoutLabel(); layoutButton.enabled = editor.editable();
        mappingButton.displayString = editor.mappingLabel(); mappingButton.enabled = editor.qualityEditable();
        qualityButton.displayString = editor.resolutionLabel(); qualityButton.enabled = editor.qualityEditable();
        widthBox.setEnabled(editor.customEditable()); heightBox.setEnabled(editor.customEditable());
        applyButton.enabled = editor.editable(); cancelButton.enabled = !editor.pending();
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (!button.enabled) return;
        editor.dimensions(widthBox.getText(), heightBox.getText());
        switch (button.id) {
            case 0: editor.nextLayout(); break;
            case 1: editor.nextMapping(); break;
            case 2: editor.nextResolution(); break;
            case 3: apply(); break;
            case 4: back(); break;
            default: break;
        }
        refresh();
    }
    private void apply() {
        try {
            observe();
            VideoPackets.SessionState current = state.session(controllerPos);
            TvDisplaySettings requested = editor.submit(System.currentTimeMillis());
            state.command(new VideoPackets.SessionCommand(VideoPackets.SessionAction.SET_DISPLAY, controllerPos,
                    "", current.item() == null ? "" : current.item().key(), "", current.presentationMode(),
                    current.timelineGeneration(), LegacyVideoPlayback.authoritativePositionMs(current, LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis())),
                    current.selectedAudioStreamId(), current.selectedSubtitleStreamId()).withDisplay(requested));
        } catch (RuntimeException failure) { showError(failure.getMessage()); }
    }
    @Override public void showError(String message) {
        editor.fail(message); refresh();
        if (stonytark.cinemarr.core.protocol.ProtocolLimits.displayProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display UI error: {}", editor.message());
    }
    private void back() { if (!editor.pending()) mc.displayGuiScreen(parent); }
    @Override protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) { back(); return; }
        if (editor.customEditable()) { widthBox.textboxKeyTyped(character, key); heightBox.textboxKeyTyped(character, key); }
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (editor.customEditable()) { widthBox.mouseClicked(x, y, button); heightBox.mouseClicked(x, y, button); }
    }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground(); super.drawScreen(mx, my, partial);
        widthBox.drawTextBox(); heightBox.drawTextBox();
        drawCenteredString(fontRendererObj, "Display Settings", width / 2, 8, 0xffffffff);
        int left = Math.max(4, (width - 304) / 2);
        drawString(fontRendererObj, "W:", left + 18, 108, 0xffffffff);
        drawString(fontRendererObj, "H:", left + 168, 108, 0xffffffff);
        drawCenteredString(fontRendererObj, "Pixel mode: one color per screen block", width / 2, 130, 0xffa0d8ff);
        VideoPackets.SessionState current = state.session(controllerPos);
        String actual = current == null || current.effectiveWidth() == 0 ? "pending" : current.effectiveWidth() + "x" + current.effectiveHeight();
        String screen = current == null ? "unknown" : current.screenWidth() + "x" + current.screenHeight();
        drawCenteredString(fontRendererObj, "Actual: " + actual + "   Screen: " + screen, width / 2, 144, 0xffa0d8ff);
        drawCenteredString(fontRendererObj, editor.resolutionLabel() + (editor.customEditable() ? " " + widthBox.getText() + "x" + heightBox.getText() : ""), width / 2, 158, 0xffa0d8ff);
        java.util.List<String> lines = fontRendererObj.listFormattedStringToWidth(editor.message(), 304);
        for (int i = 0; i < Math.min(3, lines.size()); i++) drawCenteredString(fontRendererObj, lines.get(i), width / 2, 173 + i * 10, 0xffffb36b);
    }
}
