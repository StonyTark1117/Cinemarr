package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;

/** Version-specific screen access kept outside the shared protocol state. */
final class CinemarrClientUi {
    static void openVideoScreen(long controllerPos) {
        Minecraft.getInstance().setScreen(new CinemarrVideoScreen(controllerPos, CinemarrVideoClientState.INSTANCE));
    }

    static void openDisplayScreen(long controllerPos) {
        new CinemarrVideoScreen(controllerPos, CinemarrVideoClientState.INSTANCE).openDisplaySettings();
    }

    static void refreshVideoScreen() {
        if (Minecraft.getInstance().screen instanceof CinemarrVideoScreen screen) screen.stateChanged();
    }

    static void showVideoError(String message) {
        if (Minecraft.getInstance().screen instanceof CinemarrVideoScreen screen) screen.showError(message);
        if (Minecraft.getInstance().screen instanceof DisplaySettingsErrorTarget screen) screen.showError(message);
    }

    static void openScreen(net.minecraft.client.gui.screens.Screen screen) { Minecraft.getInstance().setScreen(screen); }

    static void acceptanceReload(String nonce) {
        try {
            Minecraft.getInstance().reloadResourcePacks().whenComplete((ignored,failure) ->
                stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display reload: request={} complete={}",nonce,failure==null));
        } catch(RuntimeException failure) {
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance display reload: request={} complete=false",nonce);
        }
    }

    static double[] acceptanceCamera() {
        var player=Minecraft.getInstance().player;
        return new double[]{player.getX(),player.getY()+player.getEyeHeight(),player.getZ(),
                player.getYRot(),player.getXRot(),Minecraft.getInstance().options.fov().get(),player.getBoundingBox().minY,
                Minecraft.getInstance().screen!=null?1:0,Minecraft.getInstance().options.hideGui?1:0};
    }

    private CinemarrClientUi() {}
}
