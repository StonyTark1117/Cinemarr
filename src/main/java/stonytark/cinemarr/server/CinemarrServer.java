package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.core.library.LibraryAllowlistFiles;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CinemarrServer {
    private static volatile CinemarrServer INSTANCE;
    private GlobalPlayer player;
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
            CanonicalConfigFiles.ServerConfig config = CanonicalConfigFiles.loadServerForLoader(
                    canonical, configDirectory, "neoforge");
            CinemarrSettings.installServer(config);
            if (config.importedFrom() != null) {
                Cinemarr.LOGGER.info("Imported legacy Cinemarr server settings from {}", config.importedFrom());
            }
            java.nio.file.Path libraryFile = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(LibraryAllowlistFiles.FILE_NAME);
            List<LibraryRule> libraryRules = LibraryAllowlistFiles.load(libraryFile);
            if (libraryRules.isEmpty()) {
                Cinemarr.LOGGER.warn("Cinemarr has no allowed Plex video libraries; edit {} and restart", libraryFile);
            } else if (CinemarrSettings.plexToken().isBlank()) {
                Cinemarr.LOGGER.warn("Cinemarr video libraries are configured, but CINEMARR_PLEX_TOKEN/plexToken is empty");
            } else {
                videoService = new PlexVideoService(CinemarrSettings.plexUrl(), CinemarrSettings.plexToken());
                videoLibraries = videoService.resolveLibraries(libraryRules);
                Cinemarr.LOGGER.info("Validated {} allowed Plex video libraries", videoLibraries.size());
                videoManager = new ServerVideoManager(event.getServer(), videoService, videoLibraries,
                        CinemarrVideoSavedData.get(event.getServer()));
            }
            // The inherited audio coordinator remains isolated while its payload/UI surface is
            // replaced by the video session protocol. It is not started as part of Cinemarr.
            player = null;
        }
        catch (Exception error) { throw new IllegalStateException("Unable to initialize Cinemarr", error); }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (player != null) { player.close(); player = null; }
        if (videoManager != null) { videoManager.close(); videoManager = null; }
        videoService = null; videoLibraries = Collections.emptyList();
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) { if (player != null) player.tick(); if (videoManager != null) videoManager.tick(); }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            if (player != null) player.playerJoined(serverPlayer);
            prepareAcceptanceVideo(serverPlayer);
        }
    }
    @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer serverPlayer) { if (player != null) player.playerLeft(serverPlayer); if(videoManager!=null)videoManager.playerLeft(serverPlayer); } }
    @SubscribeEvent public void chunkSent(ChunkWatchEvent.Sent event) { if(videoManager!=null)videoManager.chunkSent(event.getPlayer(),event.getLevel(),event.getPos()); }
    @SubscribeEvent public void chunkUnwatched(ChunkWatchEvent.UnWatch event) { if(videoManager!=null)videoManager.chunkUnwatched(event.getPlayer(),event.getLevel(),event.getPos()); }

    public void hello(ServerPlayer sender) {
        CinemarrNetwork.sendToPlayer(sender, new CinemarrPayloads.ServerHello(
                ProtocolLimits.VERSION, System.currentTimeMillis()));
        if (player != null) player.hello(sender);
    }
    public void browse(ServerPlayer sender, CinemarrPayloads.BrowseRequest request) { if (player != null) player.browse(sender, request); }
    public void queue(ServerPlayer sender, CinemarrPayloads.QueueRequest request) { if (player != null) player.queue(sender, request); }
    public void control(ServerPlayer sender, CinemarrPayloads.ControlRequest request) { if (player != null) player.control(sender, request); }
    public void station(ServerPlayer sender, CinemarrPayloads.StationRequest request) { if (player != null) player.station(sender, request); }
    public void chunks(ServerPlayer sender, CinemarrPayloads.ChunkRequest request) { if (player != null) player.chunks(sender, request); }
    public void acknowledge(ServerPlayer sender, CinemarrPayloads.ChunkAcknowledgement acknowledgement) { if (player != null) player.acknowledge(sender, acknowledgement); }
    public void health(ServerPlayer sender, CinemarrPayloads.AudioHealth health) { if (player != null) player.health(sender, health); }
    public void sync(ServerPlayer sender) { if (player != null) player.sync(sender); }
    public GlobalPlayer player() { return player; }
    public PlexVideoService videoService() { return videoService; }
    public List<PlexVideoService.ResolvedLibrary> videoLibraries() { return videoLibraries; }
    public void videoLibraries(ServerPlayer player) {
        if (videoManager != null) videoManager.sendLibraries(player);
        else CinemarrNetwork.sendToPlayer(player, new stonytark.cinemarr.network.VideoPayloads.LibraryList(new VideoPackets.LibraryList(java.util.List.of())));
    }
    public void videoBrowse(ServerPlayer player, VideoPackets.BrowseRequest request) { if (videoManager != null) videoManager.browse(player, request); }
    public void videoCommand(ServerPlayer player, VideoPackets.SessionCommand command) { if (videoManager != null) videoManager.command(player, command); }
    public void videoSegments(ServerPlayer player, VideoPackets.SegmentRequest request) { if (videoManager != null) videoManager.segments(player, request); }
    public void videoManifest(ServerPlayer player, VideoPackets.SegmentManifestRequest request) { if (videoManager != null) videoManager.manifest(player, request); }
    public void videoAcknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) { if (videoManager != null) videoManager.acknowledge(player, value); }
    public void videoHealth(ServerPlayer player, VideoPackets.ClientHealth value) { if (videoManager != null) videoManager.health(player, value); }
    public String videoStatus(){return videoManager==null?"Cinemarr video is unavailable":videoManager.status();}
    public String videoDiagnostics(){return videoManager==null?"Plex=unavailable; libraries=0; sessions=0; transcodes=0":videoManager.diagnostics();}

    private void prepareAcceptanceVideo(ServerPlayer player) {
        if (!ProtocolLimits.videoProbeEnabled() || videoManager == null) return;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos controller = new BlockPos(-5, 100, 0);
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(level);
        if (screens.television(controller) == null) {
            for (int x = -4; x <= 3; x++) for (int y = 100; y <= 103; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, 0), CinemarrBlocks.SCREEN_PIXEL.get().defaultBlockState()
                        .setValue(DirectionalBlock.FACING, Direction.SOUTH));
            }
            level.setBlockAndUpdate(controller, CinemarrBlocks.TV_CONTROLLER.get().defaultBlockState());
            UUID acceptanceOwner = UUID.nameUUIDFromBytes("OfflinePlayer:CinemarrVideoA".getBytes(StandardCharsets.UTF_8));
            CinemarrWorldScreens.Activation activation = screens.activate(controller, acceptanceOwner);
            if (!activation.success()) throw new IllegalStateException("Unable to create acceptance television: " + activation.message());
            screens.updateRendition(controller, 320, 180);
            Cinemarr.LOGGER.info("Acceptance video television: controller={} dimensions=8x4 rendition=320x180 owner={}",
                    controller.asLong(), "CinemarrVideoA");
        }
        for (int x = -4; x <= 4; x++) for (int z = 1; z <= 8; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 100; y <= 103; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);
        int playerIndex = Math.max(0, eventPlayerIndex(player));
        double cameraX = (playerIndex & 1) == 0 ? -1.5 : 1.5;
        player.teleportTo(level, cameraX, 100.0, 7.5, 180.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(new BlockPos(0, 102, 0)));
        videoManager.synchronizeTrackingRadius(player);
    }

    private static int eventPlayerIndex(ServerPlayer player) {
        return player.server.getPlayerList().getPlayers().indexOf(player);
    }
}
