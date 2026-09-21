package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;

/** Version-specific screen access kept outside the shared protocol state. */
final class CinemarrClientUi {
    static void showScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    static void openVideoScreen(long controllerPos) {
        Minecraft.getInstance().setScreen(new CinemarrVideoScreen(controllerPos, CinemarrVideoClientState.INSTANCE));
    }

    static void refreshVideoScreen() {
        if (Minecraft.getInstance().screen instanceof CinemarrVideoScreen screen) screen.stateChanged();
    }

    static void showVideoError(String message) {
        if (Minecraft.getInstance().screen instanceof stonytark.cinemarr.core.video.DisplaySettingsFeedback settings) settings.showError(message);
        if (Minecraft.getInstance().screen instanceof CinemarrVideoScreen screen) screen.showError(message);
    }

    private CinemarrClientUi() {}
}
