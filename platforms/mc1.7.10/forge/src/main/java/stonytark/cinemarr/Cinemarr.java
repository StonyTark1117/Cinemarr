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
import stonytark.cinemarr.server.LegacyVideoCommands;
import stonytark.cinemarr.server.LegacyVideoManager;
import stonytark.cinemarr.server.LegacyVideoSavedData;
import stonytark.cinemarr.screen.LegacyBlocks;
import stonytark.cinemarr.screen.LegacyWorldScreens;
import stonytark.cinemarr.core.library.LibraryAllowlistFiles;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.protocol.StatePackets;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.PlexVideoService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
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
    private static LegacyVideoManager videoManager;
    private static String unavailableReason = "not initialized";

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
            LegacyVideoSavedData saved = LegacyVideoSavedData.get(event.getServer());
            String worldDirectory = event.getServer().getFolderName();
            Path libraryPath = event.getServer().getFile(worldDirectory + "/serverconfig/" + LibraryAllowlistFiles.FILE_NAME).toPath();
            List<LibraryRule> rules = LibraryAllowlistFiles.load(libraryPath);
            if (rules.isEmpty()) installUnavailable("no Plex video libraries are allowlisted");
            else if (stonytark.cinemarr.core.platform.CinemarrSettings.plexToken().trim().isEmpty()) installUnavailable("Plex token is not configured");
            else {
                PlexVideoService plex = new PlexVideoService(stonytark.cinemarr.core.platform.CinemarrSettings.plexUrl(),
                        stonytark.cinemarr.core.platform.CinemarrSettings.plexToken());
                List<PlexVideoService.ResolvedLibrary> libraries = plex.resolveLibraries(rules);
                videoManager = new LegacyVideoManager(event.getServer(), plex, libraries, saved);
                unavailableReason = "";
                LOGGER.info("Validated {} allowed Plex video libraries", libraries.size());
            }
            event.registerServerCommand(new LegacyVideoCommands(videoManager, unavailableReason));
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load canonical Cinemarr server configuration", error);
        }
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        if (videoManager != null) {
            videoManager.close();
            videoManager = null;
        }
        LegacyNetwork.shutdown();
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && videoManager != null) videoManager.tick();
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (videoManager != null && event.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            videoManager.playerLeft((net.minecraft.entity.player.EntityPlayerMP) event.player);
        }
    }

    public static void televisionActivated(net.minecraft.world.WorldServer world, LegacyWorldScreens.Television television) {
        if (videoManager != null && television != null) videoManager.televisionActivated(world, television);
    }

    private static void installUnavailable(final String reason) {
        unavailableReason = reason;
        LOGGER.warn("Cinemarr video is unavailable: {}", reason);
        LegacyNetwork.setServerListener(new LegacyNetwork.ServerListener() {
            @Override public void accept(net.minecraft.entity.player.EntityPlayerMP player,
                                         stonytark.cinemarr.network.LegacyPacketTypes.Type<?> type, Object message) {
                if (type == stonytark.cinemarr.network.LegacyPacketTypes.CLIENT_HELLO
                        || type == stonytark.cinemarr.network.LegacyPacketTypes.VIDEO_LIBRARY_LIST_REQUEST) {
                    LegacyNetwork.sendToPlayer(player, stonytark.cinemarr.network.LegacyPacketTypes.VIDEO_LIBRARY_LIST,
                            new VideoPackets.LibraryList(Collections.<VideoPackets.LibrarySummary>emptyList()));
                } else if (type.id() >= stonytark.cinemarr.network.LegacyPacketTypes.VIDEO_BROWSE_REQUEST.id()) {
                    LegacyNetwork.sendToPlayer(player, stonytark.cinemarr.network.LegacyPacketTypes.ERROR,
                            new StatePackets.ErrorMessage(StatePackets.ErrorCode.PLEX_OFFLINE, reason));
                }
            }
        });
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
