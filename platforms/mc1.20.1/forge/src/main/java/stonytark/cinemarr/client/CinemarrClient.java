package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;

public final class CinemarrClient {
    private static final CinemarrClient INSTANCE = new CinemarrClient();
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.cinemarr");

    public static void register(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(INSTANCE::keys);
        modBus.addListener(INSTANCE::reloadListeners);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(CinemarrClientConfigScreen::new));
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
    }

    private void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    private void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)this::resourcesReloaded);
    }
    private void resourcesReloaded(ResourceManager ignored) { CinemarrClientState.INSTANCE.audioEngineReloaded(); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && OPEN.consumeClick()) {
            minecraft.setScreen(new CinemarrScreen(CinemarrClientState.INSTANCE));
            CinemarrNetwork.sendToServer(new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "", 0));
        }
        CinemarrClientState.INSTANCE.tick();
    }
    @SubscribeEvent public void login(ClientPlayerNetworkEvent.LoggingIn event) { CinemarrClientState.INSTANCE.hello(); }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        net.minecraft.network.Connection connection = event.getConnection();
        net.minecraft.network.chat.Component reason = connection == null ? null : connection.getDisconnectedReason();
        if (reason != null) stonytark.cinemarr.Cinemarr.LOGGER.info("Client disconnected with reason: {}", reason.getString());
        CinemarrClientState.INSTANCE.stop();
    }

    private CinemarrClient() {}
}
