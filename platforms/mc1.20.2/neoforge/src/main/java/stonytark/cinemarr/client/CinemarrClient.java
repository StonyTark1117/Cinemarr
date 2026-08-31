package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.ConfigScreenHandler;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.core.protocol.ProtocolLimits;

public final class CinemarrClient {
    private static final CinemarrClient INSTANCE = new CinemarrClient();
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.cinemarr");
    private static final CinemarrVideoPlaybackManager VIDEO = new CinemarrVideoPlaybackManager();
    private static final CinemarrVideoRenderer VIDEO_RENDERER = new CinemarrVideoRenderer();
    private static final CinemarrVideoAudioManager VIDEO_AUDIO = new CinemarrVideoAudioManager();
    private int acceptanceVideoReadyTicks;
    private boolean acceptanceVideoScreenshotSaved;

    public static void register(IEventBus modBus) {
        modBus.addListener(INSTANCE::keys);
        modBus.addListener(INSTANCE::reloadListeners);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new CinemarrClientConfigScreen(parent)));
        NeoForge.EVENT_BUS.register(INSTANCE);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
    }

    private void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    private void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)this::resourcesReloaded);
    }
    private void resourcesReloaded(ResourceManager ignored) {
        VIDEO_AUDIO.audioEngineReloaded();
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Cinemarr: use a TV Controller to open its video controls"), false);
        }
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);
        VIDEO_AUDIO.tick(VIDEO, CinemarrVideoClientState.INSTANCE);
        captureAcceptanceVideo(minecraft);
    }
    @SubscribeEvent public void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            VIDEO_RENDERER.render(event.getPoseStack(), event.getCamera().getPosition(), VIDEO,
                    CinemarrVideoClientState.INSTANCE);
        }
    }
    @SubscribeEvent public void login(ClientPlayerNetworkEvent.LoggingIn event) { CinemarrClientState.INSTANCE.hello(); }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        net.minecraft.network.Connection connection = event.getConnection();
        net.minecraft.network.chat.Component reason = connection == null ? null : connection.getDisconnectedReason();
        if (reason != null) stonytark.cinemarr.Cinemarr.LOGGER.info("Client disconnected with reason: {}", reason.getString());
        CinemarrClientState.INSTANCE.stop();
        VIDEO_AUDIO.reset();
        VIDEO.reset();
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
        stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance video ready: frameSha256={} ptsUs={} audio=true", frame, pts);
        Screenshot.grab(minecraft.gameDirectory, "cinemarr-video-acceptance.png", minecraft.getMainRenderTarget(),
                message -> stonytark.cinemarr.Cinemarr.LOGGER.info(
                        "Acceptance video screenshot: frameSha256={} ptsUs={} result={}",
                        frame, pts, message.getString()));
    }

    private CinemarrClient() {}
}
