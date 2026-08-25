package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CinemarrClient implements ClientModInitializer {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Cinemarr.MODID, "controls"));
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CinemarrClientState.INSTANCE.stop());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(Cinemarr.MODID, "sound_engine_reload");
            }
            @Override public void onResourceManagerReload(ResourceManager manager) {
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
            minecraft.setScreenAndShow(new CinemarrScreen(CinemarrClientState.INSTANCE));
            CinemarrNetwork.sendToServer(new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "", 0));
        }
        CinemarrClientState.INSTANCE.tick();
    }

    private static void registerReceivers() {
        receive(CinemarrPayloads.OpenScreen.TYPE, CinemarrPayloads.OpenScreen.CODEC);
        receive(CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC);
        receive(CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC);
        receive(CinemarrPayloads.BrowseResults.TYPE, CinemarrPayloads.BrowseResults.CODEC);
        receive(CinemarrPayloads.AudioManifest.TYPE, CinemarrPayloads.AudioManifest.CODEC);
        receive(CinemarrPayloads.AudioChunk.TYPE, CinemarrPayloads.AudioChunk.CODEC);
        receive(CinemarrPayloads.PlaybackState.TYPE, CinemarrPayloads.PlaybackState.CODEC);
        receive(CinemarrPayloads.StationState.TYPE, CinemarrPayloads.StationState.CODEC);
        receive(CinemarrPayloads.AdventurePreview.TYPE, CinemarrPayloads.AdventurePreview.CODEC);
        receive(CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC);
    }

    private static <T extends CustomPacketPayload & stonytark.cinemarr.core.protocol.CinemarrMessage> void receive(CustomPacketPayload.Type<T> type,
                                                                 StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> ClientPayloadBridge.accept(payload));
    }
}
