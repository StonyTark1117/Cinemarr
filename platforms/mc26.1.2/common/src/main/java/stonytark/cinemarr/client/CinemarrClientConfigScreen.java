package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.client.runtime.NativeRuntime;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

/** Local settings exposed by NeoForge's Mod List before joining a server. */
public final class CinemarrClientConfigScreen extends Screen {
    private final Screen parent;
    private Button runtimeRetryButton;
    private String runtimeTooltipMessage;

    public CinemarrClientConfigScreen(Screen parent) {
        super(Component.translatable("cinemarr.config.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(listeningLabel(), button -> {
            CinemarrSettings.enabled(!CinemarrSettings.enabled());
            CinemarrSettings.saveEnabled();
            rebuildWidgets();
        }).bounds(center - 100, height / 2 - 66, 200, 20).build());
        addRenderableWidget(new VolumeSlider(center - 100, height / 2 - 38, 200, 20));
        addRenderableWidget(Button.builder(decoderLabel(), button -> {
            VideoDecoderBackend next = CinemarrSettings.videoDecoderBackend().next();
            CinemarrSettings.videoDecoderBackend(next);
            CinemarrSettings.saveVideoDecoder();
            rebuildWidgets();
        }).bounds(center - 100, height / 2 - 10, 200, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.config.library_folder"), button ->
                NativeRuntime.openLibraryDirectory()).bounds(center - 100, height / 2 + 18, 98, 20).build());
        runtimeTooltipMessage = NativeRuntime.message();
        runtimeRetryButton = addRenderableWidget(Button.builder(Component.translatable("cinemarr.config.library_retry"), button ->
                NativeRuntime.requestRescan()).bounds(center + 2, height / 2 + 18, 98, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(runtimeTooltipMessage))).build());
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.config.done"), button -> onClose())
                .bounds(center - 100, height / 2 + 58, 200, 20).build());
    }

    @Override public void tick() {
        super.tick();
        String currentMessage = NativeRuntime.message();
        if (runtimeRetryButton != null && !currentMessage.equals(runtimeTooltipMessage)) {
            runtimeTooltipMessage = currentMessage;
            runtimeRetryButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(currentMessage)));
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 100, 0xFFFFFF);
        graphics.centeredText(font, Component.translatable("cinemarr.config.local_only"), width / 2, height / 2 - 84, 0xA0D8FF);
        graphics.centeredText(font, Component.literal(NativeRuntime.statusLabel()),
                width / 2, height / 2 + 44, 0xA0D8FF);
        if (CinemarrSettings.videoDecoderBackend() != VideoDecoderBackend.SOFTWARE) {
            graphics.centeredText(font, Component.translatable("cinemarr.config.video_decoder_warning"),
                    width / 2, height / 2 + 84, 0xFFB060);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static Component listeningLabel() {
        return Component.translatable("cinemarr.config.listening", Component.translatable(CinemarrSettings.enabled() ? "cinemarr.config.on" : "cinemarr.config.off"));
    }

    private static Component decoderLabel() {
        return Component.translatable("cinemarr.config.video_decoder",
                CinemarrSettings.videoDecoderBackend().configValue());
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        private VolumeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), CinemarrSettings.volume());
            updateMessage();
        }

        @Override protected void updateMessage() { setMessage(Component.translatable("cinemarr.screen.volume", Math.round(value * 100))); }
        @Override protected void applyValue() { CinemarrSettings.volume(value); CinemarrSettings.saveVolume(); }
    }
}
