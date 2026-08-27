package stonytark.cinemarr.client;

import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.Cinemarr;
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

    public static void register() {
        RegisterKeyMappingsEvent.BUS.addListener(INSTANCE::keys);
        RegisterClientReloadListenersEvent.BUS.addListener(INSTANCE::reloadListeners);
        TickEvent.ClientTickEvent.Post.BUS.addListener(INSTANCE::tick);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(INSTANCE::login);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(INSTANCE::logout);
        AddFramePassEvent.BUS.addListener(INSTANCE::framePass);
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
        if (minecraft.screen == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Cinemarr: use a TV Controller to open its video controls"));
        }
        CinemarrClientState.INSTANCE.tick();
        VIDEO.tick(CinemarrVideoClientState.INSTANCE);
        VIDEO_AUDIO.tick(VIDEO, CinemarrVideoClientState.INSTANCE);
    }

    private void framePass(AddFramePassEvent event) {
        event.addPass(Identifier.fromNamespaceAndPath(Cinemarr.MODID, "video_screens"),
                new FramePassManager.PassDefinition() {
                    @Override public void extracts(LevelTargetBundle bundle, FramePass pass, DeltaTracker deltaTracker) {
                        bundle.main = pass.readsAndWrites(bundle.main);
                    }
                    @Override public void executes(LevelRenderState state) {
                        VIDEO_RENDERER.render(new PoseStack(), state.cameraRenderState.pos, VIDEO,
                                CinemarrVideoClientState.INSTANCE);
                    }
                });
    }

    public void login(ClientPlayerNetworkEvent.LoggingIn event) { CinemarrClientState.INSTANCE.hello(); }
    public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        VIDEO_AUDIO.reset();
        VIDEO.reset();
        CinemarrClientState.INSTANCE.stop();
    }

    private CinemarrClient() {}
}
