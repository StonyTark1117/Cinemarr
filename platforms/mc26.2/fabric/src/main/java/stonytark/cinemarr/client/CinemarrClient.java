package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class CinemarrClient implements ClientModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Cinemarr.MODID, "controls"));
    private static final CinemarrVideoPlaybackManager VIDEO=new CinemarrVideoPlaybackManager();
    private static final CinemarrVideoRenderer VIDEO_RENDERER=new CinemarrVideoRenderer();
    private static final CinemarrVideoAudioManager VIDEO_AUDIO=new CinemarrVideoAudioManager();
    private int acceptanceVideoReadyTicks;
    private boolean acceptanceVideoScreenshotSaved;
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, CATEGORY);

    @Override public void onInitializeClient() {
        initializeOnce();
    }

    public static void bootstrapQuilt() {
        if (FabricLoader.getInstance().isModLoaded("quilt_loader")) new CinemarrClient().initializeOnce();
    }

    private void initializeOnce() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        installClientSettings();
        KeyMappingHelper.registerKeyMapping(OPEN);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
        CinemarrNetwork.installClientSender(ClientPlayNetworking::send);
        registerReceivers();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CinemarrClientState.INSTANCE.hello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { VIDEO_AUDIO.reset(); VIDEO.reset(); CinemarrClientState.INSTANCE.stop(); acceptanceVideoReadyTicks=0; acceptanceVideoScreenshotSaved=false; });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> VIDEO_RENDERER.submit(context.poseStack(),
                context.levelState().cameraRenderState.pos, context.submitNodeCollector(), VIDEO,
                CinemarrVideoClientState.INSTANCE));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(Cinemarr.MODID, "sound_engine_reload");
            }
            @Override public void onResourceManagerReload(ResourceManager manager) {
                VIDEO_AUDIO.audioEngineReloaded();
                CinemarrClientState.INSTANCE.audioEngineReloaded();
            }
        });
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
        if (minecraft.gui.screen() == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Cinemarr: use a TV Controller to open its video controls"));
        }
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);
        VIDEO_AUDIO.tick(VIDEO,CinemarrVideoClientState.INSTANCE);
        captureAcceptanceVideo(minecraft);
    }
    private void captureAcceptanceVideo(Minecraft minecraft) {
        if (!ProtocolLimits.videoProbeEnabled() || acceptanceVideoScreenshotSaved || !VIDEO.hasPresentedFrame() || !VIDEO.presentedFrameCaughtUp() || !VIDEO_AUDIO.anyReady()) { acceptanceVideoReadyTicks=0; return; }
        if (++acceptanceVideoReadyTicks < 40) return;
        acceptanceVideoScreenshotSaved=true; String frame=VIDEO.presentedFrameSha256(); long pts=VIDEO.presentedFrameTimeUs();
        Cinemarr.LOGGER.info("Acceptance video ready: frameSha256={} ptsUs={} audio=true",frame,pts);
        Screenshot.grab(minecraft.gameDirectory,"cinemarr-video-acceptance.png",minecraft.gameRenderer.mainRenderTarget(),1,message -> Cinemarr.LOGGER.info("Acceptance video screenshot: frameSha256={} ptsUs={} result={}",frame,pts,message.getString()));
    }

    private static void registerReceivers() {
        receive(CinemarrPayloads.ServerHello.TYPE,CinemarrPayloads.ServerHello.CODEC);
        receive(CinemarrPayloads.TimeSyncResponse.TYPE,CinemarrPayloads.TimeSyncResponse.CODEC);
        receive(CinemarrPayloads.ErrorMessage.TYPE,CinemarrPayloads.ErrorMessage.CODEC);
        receive(VideoPayloads.LibraryList.TYPE,VideoPayloads.LibraryList.CODEC);
        receive(VideoPayloads.OpenVideoScreen.TYPE,VideoPayloads.OpenVideoScreen.CODEC);
        receive(VideoPayloads.BrowseResults.TYPE,VideoPayloads.BrowseResults.CODEC);
        receive(VideoPayloads.SessionState.TYPE,VideoPayloads.SessionState.CODEC);
        receive(VideoPayloads.TelevisionRemoved.TYPE,VideoPayloads.TelevisionRemoved.CODEC);
        receive(VideoPayloads.SessionQueue.TYPE,VideoPayloads.SessionQueue.CODEC);
        receive(VideoPayloads.SegmentManifest.TYPE,VideoPayloads.SegmentManifest.CODEC);
        receive(VideoPayloads.SegmentChunk.TYPE,VideoPayloads.SegmentChunk.CODEC);
    }

    private static <T extends CustomPacketPayload & stonytark.cinemarr.core.protocol.CinemarrMessage> void receive(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> ClientPayloadBridge.accept(payload));
    }
}
