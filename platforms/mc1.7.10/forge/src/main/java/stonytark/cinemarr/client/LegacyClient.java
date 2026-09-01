package stonytark.cinemarr.client;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import cpw.mods.fml.client.registry.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.network.LegacyNetwork;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public final class LegacyClient {
    private static final LegacyClient INSTANCE = new LegacyClient();
    private static final KeyBinding OPEN = new KeyBinding("key.cinemarr.open", Keyboard.KEY_P, "key.categories.cinemarr");
    private volatile NetworkManager disconnectedManager;
    private boolean registered;

    public static synchronized void register() {
        if (INSTANCE.registered) return;
        awaitAcceptanceSoundStartup();
        LegacyNetwork.setClientListener(LegacyClientState.INSTANCE);
        ClientRegistry.registerKeyBinding(OPEN);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        INSTANCE.registered = true;
    }

    private static void awaitAcceptanceSoundStartup() {
        if (!ProtocolLimits.audioProbeEnabled() && !ProtocolLimits.videoProbeEnabled()) return;
        long deadline = System.currentTimeMillis() + 10_000L;
        while (LegacySoundAccess.soundSystem(Minecraft.getMinecraft()) == null
                && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(25L); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Cinemarr.LOGGER.info("Acceptance client let the initial legacy sound loader settle before FML reload");
    }

    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        logDisconnectReason();
        if (Minecraft.getMinecraft().theWorld != null) LegacyClientState.INSTANCE.tick();
    }

    @SubscribeEvent public void keyInput(InputEvent.KeyInputEvent event) {
        if (OPEN.isPressed() && Minecraft.getMinecraft().thePlayer != null) {
            Minecraft.getMinecraft().thePlayer.addChatMessage(new ChatComponentText(
                    "Cinemarr: use a TV Controller to open its video controls"));
        }
    }

    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        final NetworkManager manager = event.manager;
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.func_152345_ab()) {
            handleDisconnect(manager);
        } else {
            minecraft.func_152344_a(new Runnable() {
                @Override public void run() { handleDisconnect(manager); }
            });
        }
    }

    private void handleDisconnect(NetworkManager manager) {
        disconnectedManager = manager;
        // Video reset owns OpenGL textures, so it must run on Minecraft's
        // client/render thread rather than Netty's disconnect callback.
        LegacyClientState.INSTANCE.stop();
    }

    @SubscribeEvent public void chat(ClientChatReceivedEvent event) {
        if (!ProtocolLimits.commandProbeEnabled()) return;
        String message = event.message.getUnformattedText();
        Cinemarr.LOGGER.info("Acceptance command response: {}", message);
        if (message.contains("CINEMARR_ACCEPTANCE_OPERATOR_READY")) {
            LegacyClientState.INSTANCE.operatorCommandProbe();
        }
    }

    @SubscribeEvent public void soundLoaded(SoundLoadEvent event) {
        LegacyVideoRuntime.INSTANCE.audioEngineReloaded();
    }

    @SubscribeEvent public void renderWorld(RenderWorldLastEvent event) {
        LegacyVideoRuntime.INSTANCE.render();
    }

    private void logDisconnectReason() {
        NetworkManager manager = disconnectedManager;
        if (manager == null) return;
        IChatComponent reason = manager.getExitMessage();
        if (reason == null) return;
        Cinemarr.LOGGER.info("Client disconnected with reason: {}", reason.getUnformattedText());
        disconnectedManager = null;
    }

    private LegacyClient() {}
}
