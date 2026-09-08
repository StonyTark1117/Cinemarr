package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ScreenShotHelper;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolLimits;

/** Client-thread owner for legacy video decode, texture, rendering, and audio pipelines. */
final class LegacyVideoRuntime {
    static final LegacyVideoRuntime INSTANCE = new LegacyVideoRuntime();
    private final LegacyVideoPlaybackManager playback = new LegacyVideoPlaybackManager();
    private final LegacyVideoRenderer renderer = new LegacyVideoRenderer();
    private final LegacyVideoAudioManager audio = new LegacyVideoAudioManager();
    private int acceptanceVideoReadyTicks;
    private boolean acceptanceVideoScreenshotSaved;
    private long lastWorldProbeMs;
    void tick() {
        playback.tick(LegacyVideoClientState.INSTANCE);
        audio.tick(playback, LegacyVideoClientState.INSTANCE);
        captureAcceptanceVideo();
        if (ProtocolLimits.worldChangeProbeEnabled() && System.currentTimeMillis() - lastWorldProbeMs >= 1_000L) {
            lastWorldProbeMs = System.currentTimeMillis();
            int decoderThreads = 0;
            for (Thread thread : Thread.getAllStackTraces().keySet())
                if (thread.isAlive() && thread.getName().startsWith("Cinemarr legacy FFmpeg decoder ")) decoderThreads++;
            Cinemarr.LOGGER.info("Acceptance legacy world state: clientPid={} dimension={} televisions={} streams={} pipelines={} audioSources={} decoderThreads={}",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@",2)[0],
                    Minecraft.getMinecraft().theWorld.provider.dimensionId,
                    LegacyVideoClientState.INSTANCE.televisions().size(), LegacyVideoClientState.INSTANCE.streamStates().size(),
                    playback.pipelines().size(), audio.sourceCount(), decoderThreads);
        }
    }
    void render() { renderer.render(playback, LegacyVideoClientState.INSTANCE); }
    void audioEngineReloaded() { audio.audioEngineReloaded(); }
    void reset() { audio.reset(); playback.reset(); acceptanceVideoReadyTicks = 0; acceptanceVideoScreenshotSaved = false; }
    private void captureAcceptanceVideo() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!ProtocolLimits.videoProbeViewReady(minecraft.thePlayer != null && minecraft.thePlayer.isEntityAlive(),
                minecraft.currentScreen != null)) { acceptanceVideoReadyTicks = 0; return; }
        if (!ProtocolLimits.videoProbeEnabled() || acceptanceVideoScreenshotSaved || !playback.hasPresentedFrame()
                || !playback.presentedFrameCaughtUp() || !audio.anyReady()) { acceptanceVideoReadyTicks = 0; return; }
        if (++acceptanceVideoReadyTicks < 40) return;
        acceptanceVideoScreenshotSaved = true;
        String frame = playback.presentedFrameSha256(); long pts = playback.presentedFrameTimeUs();
        Cinemarr.LOGGER.info("Acceptance video ready: frameSha256={} ptsUs={} audio=true", frame, pts);
        IChatComponent result = ScreenShotHelper.saveScreenshot(minecraft.mcDataDir, "cinemarr-video-acceptance.png",
                minecraft.displayWidth, minecraft.displayHeight, minecraft.getFramebuffer());
        Cinemarr.LOGGER.info("Acceptance video screenshot: frameSha256={} ptsUs={} result={}",
                frame, pts, result == null ? "" : result.getUnformattedText());
    }
    private LegacyVideoRuntime() {}
}
