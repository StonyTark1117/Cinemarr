package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
    private static volatile CinemarrServer INSTANCE;
    private PlexVideoService videoService;
    private List<PlexVideoService.ResolvedLibrary> videoLibraries = Collections.emptyList();
    private ServerVideoManager videoManager;

    public CinemarrServer() { INSTANCE = this; }
    public static CinemarrServer instance() { return INSTANCE; }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            java.nio.file.Path configDirectory = FMLPaths.CONFIGDIR.get();
            java.nio.file.Path canonical = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(CanonicalConfigFiles.SERVER_FILE_NAME);
            CanonicalConfigFiles.ServerConfig config = CanonicalConfigFiles.loadServerForLoader(canonical, configDirectory, "neoforge");
            CinemarrSettings.installServer(config);
            java.nio.file.Path libraryFile = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(LibraryAllowlistFiles.FILE_NAME);
            List<LibraryRule> rules = LibraryAllowlistFiles.load(libraryFile);
            if (rules.isEmpty()) Cinemarr.LOGGER.warn("Cinemarr has no allowed Plex video libraries; edit {} and restart", libraryFile);
            else if (CinemarrSettings.plexToken().isBlank()) Cinemarr.LOGGER.warn("Cinemarr video libraries are configured, but CINEMARR_PLEX_TOKEN/plexToken is empty");
            else {
                videoService = new PlexVideoService(CinemarrSettings.plexUrl(), CinemarrSettings.plexToken());
                videoLibraries = videoService.resolveLibraries(rules);
                videoManager = new ServerVideoManager(event.getServer(), videoService, videoLibraries,
                        CinemarrVideoSavedData.get(event.getServer()));
                Cinemarr.LOGGER.info("Validated {} allowed Plex video libraries", videoLibraries.size());
            }
        } catch (Exception error) { throw new IllegalStateException("Unable to initialize Cinemarr", error); }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (videoManager != null) { videoManager.close(); videoManager = null; }
        videoService = null;
        videoLibraries = Collections.emptyList();
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) { if (videoManager != null) videoManager.tick(); }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (videoManager != null && event.getEntity() instanceof ServerPlayer player) videoManager.synchronizeTrackingRadius(player);
    }
    @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (videoManager != null && event.getEntity() instanceof ServerPlayer player) videoManager.playerLeft(player);
    }
    @SubscribeEvent public void chunkSent(ChunkWatchEvent.Sent event) {
        if (videoManager != null) videoManager.chunkSent(event.getPlayer(), event.getLevel(), event.getPos());
    }
    @SubscribeEvent public void chunkUnwatched(ChunkWatchEvent.UnWatch event) {
        if (videoManager != null) videoManager.chunkUnwatched(event.getPlayer(), event.getLevel(), event.getPos());
    }

    public void hello(ServerPlayer sender) {
        CinemarrNetwork.sendToPlayer(sender, new CinemarrPayloads.ServerHello(ProtocolLimits.VERSION, System.currentTimeMillis()));
        if (videoManager != null) videoManager.synchronizeTrackingRadius(sender);
    }
    public void videoLibraries(ServerPlayer player) { if (videoManager != null) videoManager.sendLibraries(player); else CinemarrNetwork.sendToPlayer(player, new VideoPayloads.LibraryList(new VideoPackets.LibraryList(List.of()))); }
    public void videoBrowse(ServerPlayer player, VideoPackets.BrowseRequest request) { if (videoManager != null) videoManager.browse(player, request); }
    public void videoCommand(ServerPlayer player, VideoPackets.SessionCommand command) { if (videoManager != null) videoManager.command(player, command); }
    public void videoSegments(ServerPlayer player, VideoPackets.SegmentRequest request) { if (videoManager != null) videoManager.segments(player, request); }
    public void videoManifest(ServerPlayer player, VideoPackets.SegmentManifestRequest request) { if (videoManager != null) videoManager.manifest(player, request); }
    public void videoAcknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) { if (videoManager != null) videoManager.acknowledge(player, value); }
    public void videoHealth(ServerPlayer player, VideoPackets.ClientHealth value) { if (videoManager != null) videoManager.health(player, value); }
    public String videoStatus() { return videoManager == null ? "Cinemarr video is unavailable" : videoManager.status(); }
    public String videoDiagnostics() { return videoManager == null ? "Plex=unavailable; libraries=0; sessions=0; transcodes=0" : videoManager.diagnostics(); }
}
