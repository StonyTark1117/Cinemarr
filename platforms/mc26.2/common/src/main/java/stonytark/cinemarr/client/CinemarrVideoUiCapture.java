package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import stonytark.cinemarr.Cinemarr;

/** Minecraft 26.2 release-UI screenshot bridge. */
final class CinemarrVideoUiCapture {
    static void capture(Minecraft minecraft) {
        final stonytark.cinemarr.core.client.AtomicScreenshotFile screenshot = stonytark.cinemarr.core.client.AtomicScreenshotFile.create(minecraft.gameDirectory, "cinemarr-video-ui-acceptance.png");
        Screenshot.grab(minecraft.gameDirectory, screenshot.fileName(),
                minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> Cinemarr.LOGGER.info("Acceptance video UI screenshot: {}", screenshot.publish(message.getString())));
    }

    private CinemarrVideoUiCapture() {}
}
