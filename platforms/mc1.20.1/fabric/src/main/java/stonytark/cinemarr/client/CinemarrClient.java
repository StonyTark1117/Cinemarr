package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;

import java.nio.file.Path;

public final class CinemarrClient implements ClientModInitializer {
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.cinemarr");
    private static final CinemarrVideoPlaybackManager VIDEO=new CinemarrVideoPlaybackManager();
    private static final CinemarrVideoRenderer VIDEO_RENDERER=new CinemarrVideoRenderer();
    private static final CinemarrVideoAudioManager VIDEO_AUDIO=new CinemarrVideoAudioManager();

    @Override public void onInitializeClient() {
        installClientSettings();
        KeyBindingHelper.registerKeyBinding(OPEN);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
        CinemarrNetwork.installClientSender(payload -> {
            FriendlyByteBuf buffer = PacketByteBufs.create();
            if(VideoPayloads.supports(payload)){VideoPayloads.write(payload,buffer);ClientPlayNetworking.send(VideoPayloads.idOf(payload),buffer);}
            else{CinemarrPayloads.write(payload, buffer);ClientPlayNetworking.send(CinemarrPayloads.idOf(payload), buffer);}
        });
        registerReceivers();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CinemarrClientState.INSTANCE.hello());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            net.minecraft.network.chat.Component reason = handler.getConnection().getDisconnectedReason();
            if (reason != null) Cinemarr.LOGGER.info("Client disconnected with reason: {}", reason.getString());
            CinemarrClientState.INSTANCE.stop();
            VIDEO_AUDIO.reset();VIDEO.reset();
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        WorldRenderEvents.LAST.register(context->{if(context.matrixStack()!=null)VIDEO_RENDERER.render(context.matrixStack(),context.camera().getPosition(),VIDEO,CinemarrVideoClientState.INSTANCE);});
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() {
                return new ResourceLocation(Cinemarr.MODID, "sound_engine_reload");
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
        if (minecraft.screen == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Cinemarr: use a TV Controller to open its video controls"),false);
        }
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);VIDEO_AUDIO.tick(VIDEO,CinemarrVideoClientState.INSTANCE);
    }

    private static void registerReceivers() {
        receive(CinemarrPayloads.ServerHello.ID, CinemarrPayloads.ServerHello::read);
        receive(CinemarrPayloads.TimeSyncResponse.ID, CinemarrPayloads.TimeSyncResponse::read);
        receive(VideoPayloads.LibraryList.ID,VideoPayloads.LibraryList::read);receive(VideoPayloads.OpenVideoScreen.ID,VideoPayloads.OpenVideoScreen::read);
        receive(VideoPayloads.BrowseResults.ID,VideoPayloads.BrowseResults::read);receive(VideoPayloads.SessionState.ID,VideoPayloads.SessionState::read);
        receive(VideoPayloads.TelevisionRemoved.ID,VideoPayloads.TelevisionRemoved::read);receive(VideoPayloads.SessionQueue.ID,VideoPayloads.SessionQueue::read);
        receive(VideoPayloads.SegmentManifest.ID,VideoPayloads.SegmentManifest::read);receive(VideoPayloads.SegmentChunk.ID,VideoPayloads.SegmentChunk::read);
    }

    private static <T extends CinemarrMessage> void receive(ResourceLocation id, CinemarrNetwork.Decoder<T> decoder) {
        ClientPlayNetworking.registerGlobalReceiver(id, (client, handler, buffer, responseSender) -> {
            T payload = decoder.read(buffer);
            if (buffer.readableBytes() != 0) throw new IllegalArgumentException("Trailing bytes in Cinemarr packet");
            client.execute(() -> ClientPayloadBridge.accept(payload));
        });
    }
}
