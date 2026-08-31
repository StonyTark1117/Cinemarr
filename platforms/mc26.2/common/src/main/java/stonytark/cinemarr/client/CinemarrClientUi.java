package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;

/** Minecraft 26.2 screen API bridge. */
final class CinemarrClientUi {
    static void openVideoScreen(long controllerPos) {
        Minecraft.getInstance().setScreenAndShow(new CinemarrVideoScreen(controllerPos, CinemarrVideoClientState.INSTANCE));
    }

    static void refreshVideoScreen() {
        if (Minecraft.getInstance().gui.screen() instanceof CinemarrVideoScreen screen) screen.stateChanged();
    }

    private CinemarrClientUi() {}
}
