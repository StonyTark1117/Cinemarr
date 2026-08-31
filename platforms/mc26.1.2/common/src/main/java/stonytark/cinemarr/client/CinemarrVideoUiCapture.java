package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import stonytark.cinemarr.Cinemarr;

/** Minecraft 26.1.2 release-UI screenshot bridge. */
final class CinemarrVideoUiCapture {
    static void capture(Minecraft minecraft) {
        Screenshot.grab(minecraft.gameDirectory, "cinemarr-video-ui-acceptance.png",
                minecraft.getMainRenderTarget(), 1,
                message -> Cinemarr.LOGGER.info("Acceptance video UI screenshot: {}", message.getString()));
    }

    private CinemarrVideoUiCapture() {}
}
