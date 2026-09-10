package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;

import java.nio.file.Path;

public final class CinemarrClient implements ClientModInitializer {
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.cinemarr");
    private static final CinemarrVideoPlaybackManager VIDEO = new CinemarrVideoPlaybackManager();
    private static final CinemarrVideoRenderer VIDEO_RENDERER = new CinemarrVideoRenderer();
    private static final CinemarrVideoAudioManager VIDEO_AUDIO = new CinemarrVideoAudioManager();
    private int acceptanceVideoReadyTicks;
    private boolean acceptanceVideoScreenshotSaved;
    private final stonytark.cinemarr.core.client.ClientConnectionLifecycle connections =
            new stonytark.cinemarr.core.client.ClientConnectionLifecycle(
                    task -> Minecraft.getInstance().execute(task), this::resetConnection);

    @Override public void onInitializeClient() {
        installClientSettings();
        KeyBindingHelper.registerKeyBinding(OPEN);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
        CinemarrNetwork.installClientSender(payload -> {
            if (connectionReady(Minecraft.getInstance().getConnection())) ClientPlayNetworking.send(payload);
        });
        registerReceivers();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                connections.joined(handler, this::helloAfterReset));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            connections.disconnected(handler);
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.LAST.register(context -> {
            if (context.matrixStack() != null) VIDEO_RENDERER.render(context.matrixStack(),
                    context.camera().getPosition(), VIDEO, CinemarrVideoClientState.INSTANCE);
        });
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, "sound_engine_reload");
            }
            @Override public void onResourceManagerReload(ResourceManager manager) {
                VIDEO_AUDIO.audioEngineReloaded();
            }
        });
    }

    private void helloAfterReset() {
        if (ProtocolLimits.videoProbeEnabled())
            stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance client JOIN reset complete");
        CinemarrClientState.INSTANCE.hello();
    }

    private void resetConnection() {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        VIDEO_AUDIO.reset();
        VIDEO.reset();
        CinemarrClientState.INSTANCE.stop();
        acceptanceVideoReadyTicks = 0;
        acceptanceVideoScreenshotSaved = false;
        if (ProtocolLimits.videoProbeEnabled())
            Cinemarr.LOGGER.info("Acceptance client media reset complete");
    }

    private static void installClientSettings() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        try {
            CanonicalConfigFiles.ClientConfig config = CanonicalConfigFiles.loadClientForLoader(
                    configDirectory, "fabric");
            CinemarrSettings.installClient(config);
            if (config.importedFrom() != null) {
                Cinemarr.LOGGER.info("Imported legacy Cinemarr client settings from {}", config.importedFrom());
            }
        } catch (Exception error) { throw new IllegalStateException("Unable to load Cinemarr client settings", error); }
    }

    private void tick(Minecraft minecraft) {
        if (minecraft.screen == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Cinemarr: use a TV Controller to open its video controls"), false);
        }
        var handler = minecraft.getConnection();
        if (!connections.prepareTick(handler != null && handler.getConnection().isConnected()
                ? handler : null)) return;
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);
        VIDEO_AUDIO.tick(VIDEO, CinemarrVideoClientState.INSTANCE);
        captureAcceptanceVideo(minecraft);
    }

    private void captureAcceptanceVideo(Minecraft minecraft) {
        if (!ProtocolLimits.videoProbeViewReady(minecraft.player != null && minecraft.player.isAlive(),
                minecraft.screen != null)) { acceptanceVideoReadyTicks = 0; return; }
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
        final stonytark.cinemarr.core.client.AtomicScreenshotFile screenshot = stonytark.cinemarr.core.client.AtomicScreenshotFile.create(minecraft.gameDirectory, "cinemarr-video-acceptance.png");
        Screenshot.grab(minecraft.gameDirectory, screenshot.fileName(), minecraft.getMainRenderTarget(),
                message -> Cinemarr.LOGGER.info("Acceptance video screenshot: frameSha256={} ptsUs={} result={}",
                        frame, pts, screenshot.publish(message.getString())));
    }

    private boolean connectionReady(net.minecraft.client.multiplayer.ClientPacketListener handler) {
        return handler != null && handler == Minecraft.getInstance().getConnection()
                && handler.getConnection().isConnected() && connections.isActive(handler);
    }

    private void registerReceivers() {
        receive(CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC);
        receive(CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC);
        receive(CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC);
        receive(VideoPayloads.LibraryList.TYPE, VideoPayloads.LibraryList.CODEC);
        receive(VideoPayloads.OpenVideoScreen.TYPE, VideoPayloads.OpenVideoScreen.CODEC);
        receive(VideoPayloads.BrowseResults.TYPE, VideoPayloads.BrowseResults.CODEC);
        receive(VideoPayloads.SessionState.TYPE, VideoPayloads.SessionState.CODEC);
        receive(VideoPayloads.TelevisionRemoved.TYPE, VideoPayloads.TelevisionRemoved.CODEC);
        receive(VideoPayloads.SessionQueue.TYPE, VideoPayloads.SessionQueue.CODEC);
        receive(VideoPayloads.SegmentManifest.TYPE, VideoPayloads.SegmentManifest.CODEC);
        receive(VideoPayloads.SegmentChunk.TYPE, VideoPayloads.SegmentChunk.CODEC);
    }

    private <T extends CustomPacketPayload & stonytark.cinemarr.core.protocol.CinemarrMessage> void receive(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            var handler = context.player().connection;
            context.client().execute(() -> {
                if (connectionReady(handler)) ClientPayloadBridge.accept(payload);
            });
        });
    }
}
