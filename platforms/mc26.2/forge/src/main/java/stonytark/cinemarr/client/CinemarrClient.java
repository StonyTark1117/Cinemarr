package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.network.ClientPayloadBridge;

public final class CinemarrClient {
    private static final CinemarrClient INSTANCE = new CinemarrClient();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Cinemarr.MODID, "controls"));
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, CATEGORY);
    private static final CinemarrVideoPlaybackManager VIDEO = new CinemarrVideoPlaybackManager();
    private static final CinemarrVideoRenderer VIDEO_RENDERER = new CinemarrVideoRenderer();
    private static final CinemarrVideoAudioManager VIDEO_AUDIO = new CinemarrVideoAudioManager();
    private int acceptanceVideoReadyTicks;
    private boolean acceptanceVideoScreenshotSaved;

    public static void register() {
        RegisterKeyMappingsEvent.BUS.addListener(INSTANCE::keys);
        RegisterClientReloadListenersEvent.BUS.addListener(INSTANCE::reloadListeners);
        TickEvent.ClientTickEvent.Post.BUS.addListener(INSTANCE::tick);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(INSTANCE::login);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(INSTANCE::logout);
        net.minecraftforge.common.MinecraftForge.registerConfigScreen(CinemarrClientConfigScreen::new);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
    }

    private void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    private void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) this::resourcesReloaded);
    }
    private void resourcesReloaded(ResourceManager ignored) {
        VIDEO_AUDIO.audioEngineReloaded();
        CinemarrClientState.INSTANCE.audioEngineReloaded();
    }

    public void tick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Cinemarr: use a TV Controller to open its video controls"));
        }
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);
        VIDEO_AUDIO.tick(VIDEO, CinemarrVideoClientState.INSTANCE);
        captureAcceptanceVideo(minecraft);
    }

    public static void submitVideoGeometry(PoseStack poseStack, Vec3 camera, SubmitNodeCollector submits) {
        VIDEO_RENDERER.submit(poseStack, camera, submits, VIDEO, CinemarrVideoClientState.INSTANCE);
    }

    public void login(ClientPlayerNetworkEvent.LoggingIn event) { CinemarrClientState.INSTANCE.hello(); }
    public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIDEO_AUDIO.reset();
        VIDEO.reset();
        CinemarrClientState.INSTANCE.stop();
        acceptanceVideoReadyTicks = 0;
        acceptanceVideoScreenshotSaved = false;
    }

    private void captureAcceptanceVideo(Minecraft minecraft) {
        if (!ProtocolLimits.videoProbeEnabled() || acceptanceVideoScreenshotSaved
                || !VIDEO.hasPresentedFrame() || !VIDEO.presentedFrameCaughtUp() || !VIDEO_AUDIO.anyReady()) {
            acceptanceVideoReadyTicks = 0;
            return;
        }
        if (++acceptanceVideoReadyTicks < 40) return;
        acceptanceVideoScreenshotSaved = true;
        String frame = VIDEO.presentedFrameSha256();
        long pts = VIDEO.presentedFrameTimeUs();
        Cinemarr.LOGGER.info("Acceptance video ready: frameSha256={} ptsUs={} audio=true", frame, pts);
        Screenshot.grab(minecraft.gameDirectory, "cinemarr-video-acceptance.png",
                minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> Cinemarr.LOGGER.info("Acceptance video screenshot: frameSha256={} ptsUs={} result={}",
                        frame, pts, message.getString()));
    }

    private CinemarrClient() {}
}
