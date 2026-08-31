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
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.core.server.PlexConnectionLifecycle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    public static final int PROTOCOL = 9;

    public static Logger LOGGER;
    private static LegacyVideoManager videoManager;
    private static PlexConnectionLifecycle plexLifecycle;
    private static String unavailableReason = "not initialized";
    private static final Queue<Runnable> MAIN_THREAD_TASKS = new ConcurrentLinkedQueue<Runnable>();

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
            for(net.minecraft.world.WorldServer world:event.getServer().worldServers)if(world!=null)stonytark.cinemarr.screen.LegacyWorldScreens.get(world);
            LegacyVideoSavedData saved = LegacyVideoSavedData.get(event.getServer());
            String worldDirectory = event.getServer().getFolderName();
            Path libraryPath = event.getServer().getFile(worldDirectory + "/serverconfig/" + LibraryAllowlistFiles.FILE_NAME).toPath();
            List<LibraryRule> rules = LibraryAllowlistFiles.load(libraryPath);
            if (rules.isEmpty()) installUnavailable("no Plex video libraries are allowlisted");
            else if (stonytark.cinemarr.core.platform.CinemarrSettings.plexToken().trim().isEmpty()) installUnavailable("Plex token is not configured");
            else installUnavailable("Plex connection is starting");
            plexLifecycle = new PlexConnectionLifecycle();
            final net.minecraft.server.MinecraftServer server = event.getServer();
            plexLifecycle.configure(stonytark.cinemarr.core.platform.CinemarrSettings.plexUrl(),
                    stonytark.cinemarr.core.platform.CinemarrSettings.plexToken(), rules,
                    new PlexConnectionLifecycle.MainThread() {
                        @Override public void execute(Runnable action) { MAIN_THREAD_TASKS.add(action); }
                    }, new PlexConnectionLifecycle.ReadyHandler() {
                        @Override public void install(PlexConnectionLifecycle.Connection connection) {
                            videoManager = new LegacyVideoManager(server, connection.service(), connection.libraries(), saved);
                            unavailableReason = "";
                            LOGGER.info("Validated {} allowed Plex video libraries{}", connection.libraries().size(),
                                    connection.requested() ? " after manual retry" : "");
                        }
                    }, new PlexConnectionLifecycle.FailureHandler() {
                        @Override public void failed(String message, long delay) {
                            installUnavailable("Plex degraded: " + message);
                            LOGGER.warn("Plex unavailable; Cinemarr will retry in {} seconds: {}", delay / 1000L, message);
                        }
                    });
            event.registerServerCommand(new LegacyVideoCommands(event.getServer()));
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
        if (plexLifecycle != null) { plexLifecycle.close(); plexLifecycle = null; }
        MAIN_THREAD_TASKS.clear();
        LegacyNetwork.shutdown();
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable action;
        while ((action = MAIN_THREAD_TASKS.poll()) != null) action.run();
        if (plexLifecycle != null) plexLifecycle.tick(System.currentTimeMillis());
        if (videoManager != null) videoManager.tick();
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

    public static String videoStatus() {
        if (videoManager != null) return videoManager.status();
        String state = plexLifecycle == null ? "disabled" : plexLifecycle.state().name().toLowerCase(java.util.Locale.ROOT);
        return "Cinemarr video " + state + "; registeredTvs="
                + stonytark.cinemarr.core.server.TelevisionLifecycle.count() + "; activeStreams=0/"
                + stonytark.cinemarr.core.platform.CinemarrSettings.maximumConcurrentStreams()
                + "; reason=" + unavailableReason;
    }

    public static String videoDiagnostics() {
        if (videoManager != null) return videoManager.diagnostics();
        String state = plexLifecycle == null ? "disabled" : plexLifecycle.state().name().toLowerCase(java.util.Locale.ROOT);
        long retry = plexLifecycle == null ? 0L : plexLifecycle.retryInMs(System.currentTimeMillis());
        String failure = plexLifecycle == null || plexLifecycle.lastFailure().isEmpty() ? "none" : plexLifecycle.lastFailure();
        return "Plex=" + state + "; retryInMs=" + retry + "; lastFailure=" + failure
                + "; registeredTvs=" + stonytark.cinemarr.core.server.TelevisionLifecycle.count();
    }

    public static boolean retryPlex() { return plexLifecycle != null && plexLifecycle.retry(); }

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
                            new stonytark.cinemarr.network.LegacyPacketTypes.ErrorMessage(reason));
                }
            }
        });
    }

    @NetworkCheckHandler
    public boolean requireMatchingClient(Map<String, String> remoteVersions, Side remoteSide) {
        if (remoteSide == Side.CLIENT) {
            // Let an absent/older client reach LegacyNetwork's explicit protocol-9 hello gate so
            // it receives Cinemarr's clear timeout/mismatch text instead of FML's generic timeout.
            return true;
        }
        return remoteVersions != null && VERSION.equals(remoteVersions.get(MOD_ID));
    }
}
