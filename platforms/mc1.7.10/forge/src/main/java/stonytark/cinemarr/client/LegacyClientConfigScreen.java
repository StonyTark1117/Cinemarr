package stonytark.cinemarr.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class LegacyClientConfigScreen extends GuiScreen {
    private final GuiScreen parent;
    public LegacyClientConfigScreen(GuiScreen parent) { this.parent = parent; }

    @Override public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(1, width / 2 - 100, height / 2 - 24, 200, 20,
                "Listening: " + (CinemarrSettings.enabled() ? "On" : "Off")));
        buttonList.add(new GuiButton(2, width / 2 - 100, height / 2, 64, 20, "Volume -"));
        buttonList.add(new GuiButton(3, width / 2 + 36, height / 2, 64, 20, "Volume +"));
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 + 36, 200, 20, "Done"));
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) { mc.displayGuiScreen(parent); return; }
        if (button.id == 1) {
            CinemarrSettings.enabled(!CinemarrSettings.enabled()); CinemarrSettings.saveEnabled();
            LegacyClientState.INSTANCE.listeningChanged();
        } else if (button.id == 2 || button.id == 3) {
            CinemarrSettings.volume(CinemarrSettings.volume() + (button.id == 3 ? 0.1 : -0.1));
            CinemarrSettings.saveVolume();
        }
        initGui();
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Cinemarr Client Settings", width / 2, height / 2 - 66, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "These settings affect only this Minecraft client", width / 2, height / 2 - 50, 0xA0D8FF);
        drawCenteredString(fontRendererObj, "Volume " + Math.round(CinemarrSettings.volume() * 100.0) + "%", width / 2, height / 2 + 6, 0xCFCFCF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
