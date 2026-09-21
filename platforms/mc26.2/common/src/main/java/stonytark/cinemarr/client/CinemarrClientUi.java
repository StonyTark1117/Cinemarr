package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;

/** Minecraft 26.2 screen API bridge. */
final class CinemarrClientUi {
    static void showScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    static void openVideoScreen(long controllerPos) {
        Minecraft.getInstance().setScreenAndShow(new CinemarrVideoScreen(controllerPos, CinemarrVideoClientState.INSTANCE));
    }

    static void refreshVideoScreen() {
        if (Minecraft.getInstance().gui.screen() instanceof CinemarrVideoScreen screen) screen.stateChanged();
    }

    static void showVideoError(String message) {
        if (Minecraft.getInstance().gui.screen() instanceof stonytark.cinemarr.core.video.DisplaySettingsFeedback settings) settings.showError(message);
        if (Minecraft.getInstance().gui.screen() instanceof CinemarrVideoScreen screen) screen.showError(message);
    }

    private CinemarrClientUi() {}
}
