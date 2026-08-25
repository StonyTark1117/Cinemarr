package stonytark.cinemarr.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.network.ClientPayloadBridge;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;

@Mod(value = Cinemarr.MODID, dist = net.neoforged.api.distmarker.Dist.CLIENT)
public final class CinemarrClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(Cinemarr.MODID, "controls"));
    private static final KeyMapping OPEN = new KeyMapping("key.cinemarr.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);
    private boolean openOnNextTick;

    public CinemarrClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::keys);
        modBus.addListener(this::soundEngineLoaded);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new CinemarrClientConfigScreen(parent));
        NeoForge.EVENT_BUS.register(this);
        ClientPayloadBridge.install(CinemarrClientState.INSTANCE::accept);
    }
    private void keys(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    @SubscribeEvent public void keyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        InputConstants.Key pressed = InputConstants.getKey(event.getKeyEvent());
        if (event.getAction() == GLFW.GLFW_PRESS && minecraft.screen == null && OPEN.getKey().equals(pressed)) {
            // Raw input remains unambiguous even when vanilla has another
            // mapping on P. The next post-tick runs after vanilla key handling.
            openOnNextTick = true;
        }
    }
    @SubscribeEvent public void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean openNow = openOnNextTick;
        openOnNextTick = false;
        while (OPEN.consumeClick()) { /* Prevent a duplicate open next tick. */ }
        if (openNow && minecraft.player != null) {
            minecraft.setScreen(new CinemarrScreen(CinemarrClientState.INSTANCE));
            CinemarrNetwork.sendToServer(new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "", 0));
        }
        CinemarrClientState.INSTANCE.tick();
    }
    @SubscribeEvent public void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        net.minecraft.network.Connection connection = event.getConnection();
        net.minecraft.network.DisconnectionDetails details = connection == null ? null : connection.getDisconnectionDetails();
        net.minecraft.network.chat.Component reason = details == null ? null : details.reason();
        if (reason != null) stonytark.cinemarr.Cinemarr.LOGGER.info("Client disconnected with reason: {}", reason.getString());
        CinemarrClientState.INSTANCE.stop();
    }
    @SubscribeEvent public void login(ClientPlayerNetworkEvent.LoggingIn event) { CinemarrClientState.INSTANCE.hello(); }
    private void soundEngineLoaded(SoundEngineLoadEvent event) { CinemarrClientState.INSTANCE.audioEngineReloaded(); }
}
