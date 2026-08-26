package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.library.LibraryAllowlistFiles;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.Collections;
import java.util.List;

public final class CinemarrServer {
    private static final CinemarrServer INSTANCE = new CinemarrServer();
    private PlexVideoService plex;
    private List<PlexVideoService.ResolvedLibrary> libraries = Collections.emptyList();
    private ServerVideoManager manager;
    private long ticks;

    public static CinemarrServer instance() { return INSTANCE; }
    public static void register() { NeoForge.EVENT_BUS.register(INSTANCE); }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            java.nio.file.Path configDirectory = FMLPaths.CONFIGDIR.get();
            java.nio.file.Path canonical = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(CanonicalConfigFiles.SERVER_FILE_NAME);
            CinemarrSettings.installServer(CanonicalConfigFiles.loadServerForLoader(
                    canonical, configDirectory, "neoforge"));
            java.nio.file.Path libraryFile = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(LibraryAllowlistFiles.FILE_NAME);
            List<LibraryRule> rules = LibraryAllowlistFiles.load(libraryFile);
            if (rules.isEmpty()) {
                Cinemarr.LOGGER.warn("Cinemarr has no allowed Plex video libraries; edit {} and restart", libraryFile);
            } else if (CinemarrSettings.plexToken().isBlank()) {
                Cinemarr.LOGGER.warn("Cinemarr video libraries are configured, but CINEMARR_PLEX_TOKEN/plexToken is empty");
            } else {
                plex = new PlexVideoService(CinemarrSettings.plexUrl(), CinemarrSettings.plexToken());
                libraries = plex.resolveLibraries(rules);
                manager = new ServerVideoManager(event.getServer(), plex, libraries,
                        CinemarrVideoSavedData.get(event.getServer()));
                Cinemarr.LOGGER.info("Validated {} allowed Plex video libraries", libraries.size());
            }
            ticks = 0;
        } catch (Exception error) {
            throw new IllegalStateException("Unable to initialize Cinemarr", error);
        }
    }

    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (manager != null) { manager.close(); manager = null; }
        plex = null;
        libraries = Collections.emptyList();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (manager != null) {
            manager.tick();
            if (ticks % 10 == 0) for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                manager.synchronizeTrackingRadius(player);
            }
        }
    }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (manager != null && event.getEntity() instanceof ServerPlayer player) manager.synchronizeTrackingRadius(player);
    }
    @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (manager != null && event.getEntity() instanceof ServerPlayer player) manager.playerLeft(player);
    }

    public void hello(ServerPlayer sender) {
        CinemarrNetwork.sendToPlayer(sender, new CinemarrPayloads.ServerHello(
                ProtocolLimits.VERSION, System.currentTimeMillis()));
        if (manager != null) manager.synchronizeTrackingRadius(sender);
    }
    public void videoLibraries(ServerPlayer player) {
        if (manager != null) manager.sendLibraries(player);
        else CinemarrNetwork.sendToPlayer(player, new VideoPayloads.LibraryList(new VideoPackets.LibraryList(List.of())));
    }
    public void videoBrowse(ServerPlayer player, VideoPackets.BrowseRequest value) { if (manager != null) manager.browse(player, value); }
    public void videoCommand(ServerPlayer player, VideoPackets.SessionCommand value) { if (manager != null) manager.command(player, value); }
    public void videoSegments(ServerPlayer player, VideoPackets.SegmentRequest value) { if (manager != null) manager.segments(player, value); }
    public void videoManifest(ServerPlayer player, VideoPackets.SegmentManifestRequest value) { if (manager != null) manager.manifest(player, value); }
    public void videoAcknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) { if (manager != null) manager.acknowledge(player, value); }
    public void videoHealth(ServerPlayer player, VideoPackets.ClientHealth value) { if (manager != null) manager.health(player, value); }
    public String videoStatus() { return manager == null ? "Cinemarr video is unavailable" : manager.status(); }
    public String videoDiagnostics() { return manager == null ? "Plex=unavailable; libraries=0; sessions=0; transcodes=0" : manager.diagnostics(); }
}
