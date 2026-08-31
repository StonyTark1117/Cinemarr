package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;

/** Version-specific screen access kept outside the shared protocol state. */
final class CinemarrClientUi {
    static void openVideoScreen(long controllerPos) {
        Minecraft.getInstance().setScreen(new CinemarrVideoScreen(controllerPos, CinemarrVideoClientState.INSTANCE));
    }

    static void refreshVideoScreen() {
        if (Minecraft.getInstance().screen instanceof CinemarrVideoScreen screen) screen.stateChanged();
    }

    private CinemarrClientUi() {}
}
