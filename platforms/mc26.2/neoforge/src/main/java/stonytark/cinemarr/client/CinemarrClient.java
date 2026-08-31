package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.network.ClientPayloadBridge;

@Mod(value = Cinemarr.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class CinemarrClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Cinemarr.MODID, "controls"));
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, CATEGORY);
    private static final CinemarrVideoPlaybackManager VIDEO = new CinemarrVideoPlaybackManager();
    private static final CinemarrVideoRenderer VIDEO_RENDERER = new CinemarrVideoRenderer();
    private static final CinemarrVideoAudioManager VIDEO_AUDIO = new CinemarrVideoAudioManager();
    private boolean openOnNextTick;
    private int acceptanceVideoReadyTicks;
    private boolean acceptanceVideoScreenshotSaved;

    public CinemarrClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::keys);
        modBus.addListener(this::soundEngineLoaded);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new CinemarrClientConfigScreen(parent));
        NeoForge.EVENT_BUS.register(this);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
    }

    private void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    @SubscribeEvent public void keyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        InputConstants.Key pressed = InputConstants.getKey(event.getKeyEvent());
        if (event.getAction() == GLFW.GLFW_PRESS && minecraft.gui.screen() == null && OPEN.getKey().equals(pressed)) {
            openOnNextTick = true;
        }
    }
    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean openNow = openOnNextTick;
        openOnNextTick = false;
        while (OPEN.consumeClick()) { }
        if (openNow && minecraft.player != null) {
            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Cinemarr: use a TV Controller to open its video controls"));
        }
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);
        VIDEO_AUDIO.tick(VIDEO, CinemarrVideoClientState.INSTANCE);
        captureAcceptanceVideo(minecraft);
    }
    @SubscribeEvent public void submitGeometry(SubmitCustomGeometryEvent event) {
        VIDEO_RENDERER.submit(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos,
                event.getSubmitNodeCollector(), VIDEO, CinemarrVideoClientState.INSTANCE);
    }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIDEO_AUDIO.reset();
        VIDEO.reset();
        CinemarrClientState.INSTANCE.stop();
        acceptanceVideoReadyTicks = 0;
        acceptanceVideoScreenshotSaved = false;
    }
    @SubscribeEvent public void login(ClientPlayerNetworkEvent.LoggingIn event) { CinemarrClientState.INSTANCE.hello(); }
    private void soundEngineLoaded(SoundEngineLoadEvent event) {
        VIDEO_AUDIO.audioEngineReloaded();
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
}
