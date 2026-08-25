package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
import net.minecraft.resources.ResourceLocation;
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

public final class CinemarrClient implements ClientModInitializer {
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.cinemarr");

    @Override public void onInitializeClient() {
        installClientSettings();
        KeyBindingHelper.registerKeyBinding(OPEN);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
        CinemarrNetwork.installClientSender(ClientPlayNetworking::send);
        registerReceivers();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CinemarrClientState.INSTANCE.hello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CinemarrClientState.INSTANCE.stop());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, "sound_engine_reload");
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
        if (minecraft.screen == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.setScreen(new CinemarrScreen(CinemarrClientState.INSTANCE));
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
