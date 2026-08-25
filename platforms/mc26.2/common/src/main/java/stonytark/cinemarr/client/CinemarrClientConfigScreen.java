package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.platform.CinemarrSettings;

/** Local settings exposed by NeoForge's Mod List before joining a server. */
public final class CinemarrClientConfigScreen extends Screen {
    private final Screen parent;

    public CinemarrClientConfigScreen(Screen parent) {
        super(Component.translatable("cinemarr.config.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(listeningLabel(), button -> {
            CinemarrSettings.enabled(!CinemarrSettings.enabled());
            CinemarrSettings.saveEnabled();
            CinemarrClientState.INSTANCE.listeningChanged();
            rebuildWidgets();
        }).bounds(center - 100, height / 2 - 28, 200, 20).build());
        addRenderableWidget(new VolumeSlider(center - 100, height / 2, 200, 20));
        addRenderableWidget(Button.builder(Component.translatable("cinemarr.config.done"), button -> onClose())
                .bounds(center - 100, height / 2 + 38, 200, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - 62, 0xFFFFFF);
        graphics.centeredText(font, Component.translatable("cinemarr.config.local_only"), width / 2, height / 2 - 46, 0xA0D8FF);
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static Component listeningLabel() {
        return Component.translatable("cinemarr.config.listening", Component.translatable(CinemarrSettings.enabled() ? "cinemarr.config.on" : "cinemarr.config.off"));
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
