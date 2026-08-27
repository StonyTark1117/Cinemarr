package stonytark.cinemarr;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;
import stonytark.cinemarr.config.LegacyConfig;
import stonytark.cinemarr.client.LegacyClient;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.server.LegacyCommands;
import stonytark.cinemarr.server.LegacyGlobalPlayer;
import stonytark.cinemarr.server.LegacySavedData;
import stonytark.cinemarr.screen.LegacyBlocks;

import java.io.IOException;
import java.util.Map;

@Mod(
        modid = Cinemarr.MOD_ID,
        name = Cinemarr.MOD_NAME,
        version = Cinemarr.VERSION,
        acceptableRemoteVersions = "*",
        guiFactory = "stonytark.cinemarr.client.LegacyGuiFactory"
)
public final class Cinemarr {
    public static final String MOD_ID = "cinemarr";
    public static final String MOD_NAME = "Cinemarr";
    public static final String VERSION = "1.0.0";
    public static final int PROTOCOL = 8;

    public static Logger LOGGER;
    private static LegacyGlobalPlayer coordinator;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        LOGGER.info("Initializing Cinemarr {} for Forge 1.7.10 protocol {}", VERSION, PROTOCOL);
        LegacyBlocks.register();
        if (event.getSide().isClient()) {
            try {
                LegacyConfig.installClient(event.getModConfigurationDirectory());
            } catch (IOException error) {
                throw new IllegalStateException("Unable to load canonical Cinemarr client configuration", error);
            }
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LegacyBlocks.registerRecipes();
        LegacyNetwork.register();
        FMLCommonHandler.instance().bus().register(this);
        if (event.getSide().isClient()) LegacyClient.register();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        try {
            LegacyConfig.installServer(event.getServer());
            LegacySavedData.get(event.getServer());
            coordinator = new LegacyGlobalPlayer(event.getServer());
            event.registerServerCommand(new LegacyCommands(coordinator));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load canonical Cinemarr server configuration", error);
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        if (coordinator != null) {
            coordinator.close();
            coordinator = null;
        }
        LegacyNetwork.shutdown();
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && coordinator != null) coordinator.tick();
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (coordinator != null && event.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            coordinator.playerLeft((net.minecraft.entity.player.EntityPlayerMP) event.player);
        }
    }

    @NetworkCheckHandler
    public boolean requireMatchingClient(Map<String, String> remoteVersions, Side remoteSide) {
        if (remoteSide == Side.CLIENT) {
            // Let an absent/older client reach LegacyNetwork's explicit protocol-8 hello gate so
            // it receives Cinemarr's clear timeout/mismatch text instead of FML's generic timeout.
            return true;
        }
        return remoteVersions != null && VERSION.equals(remoteVersions.get(MOD_ID));
    }
}
