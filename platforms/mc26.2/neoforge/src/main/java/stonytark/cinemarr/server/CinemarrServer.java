package stonytark.cinemarr.server;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
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
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) stonytark.cinemarr.screen.CinemarrWorldScreens.get(level);
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
        prepareAcceptanceVideo(sender);
        if (videoManager != null) videoManager.synchronizeTrackingRadius(sender);
    }
    public void videoLibraries(ServerPlayer player) { if (videoManager != null) videoManager.sendLibraries(player); else CinemarrNetwork.sendToPlayer(player, new VideoPayloads.LibraryList(new VideoPackets.LibraryList(List.of()))); }
    public void videoBrowse(ServerPlayer player, VideoPackets.BrowseRequest request) { if (videoManager != null) videoManager.browse(player, request); }
    public void videoCommand(ServerPlayer player, VideoPackets.SessionCommand command) { if (videoManager != null) videoManager.command(player, command); }
    public void videoSegments(ServerPlayer player, VideoPackets.SegmentRequest request) { if (videoManager != null) videoManager.segments(player, request); }
    public void videoManifest(ServerPlayer player, VideoPackets.SegmentManifestRequest request) { if (videoManager != null) videoManager.manifest(player, request); }
    public void videoAcknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) { if (videoManager != null) videoManager.acknowledge(player, value); }
    public void videoHealth(ServerPlayer player, VideoPackets.ClientHealth value) { if (videoManager != null) videoManager.health(player, value); }
    public String videoStatus() { return videoManager == null ? unavailableStatus() : videoManager.status(); }
    public String videoDiagnostics() { return videoManager == null ? unavailableDiagnostics() : videoManager.diagnostics(); }
    private static String unavailableStatus() { return "Cinemarr video unavailable; registeredTvs="+stonytark.cinemarr.core.server.TelevisionLifecycle.count()+"; activeStreams=0/"+CinemarrSettings.maximumConcurrentStreams()+"; attachedSessions=0; dormantSessions=0"; }
    private static String unavailableDiagnostics() { return "Plex=unavailable; registeredTvs="+stonytark.cinemarr.core.server.TelevisionLifecycle.count()+"; libraries=0; activeStreams=0/"+CinemarrSettings.maximumConcurrentStreams()+"; attachedSessions=0; dormantSessions=0"; }

    private void prepareAcceptanceVideo(ServerPlayer player) {
        if (!ProtocolLimits.videoProbeEnabled() || videoManager == null) return;
        ServerLevel level = player.level();
        BlockPos controller = new BlockPos(-1, 100, -1);
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(level);
        if (screens.television(controller) == null && "CinemarrVideoA".equals(player.getGameProfile().name())) {
            for (int x = -8; x <= 7; x++) for (int y = 100; y <= 108; y++) level.setBlockAndUpdate(new BlockPos(x, y, 0), Blocks.AIR.defaultBlockState());
            var quick = CinemarrBlocks.QUICK_TV_144P.get();
            var state = quick.defaultBlockState().setValue(stonytark.cinemarr.screen.QuickTvBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(controller, state); quick.setPlacedBy(level, controller, state, player, net.minecraft.world.item.ItemStack.EMPTY);
            CinemarrWorldScreens.Television television = screens.television(controller);
            if (television == null || television.width() != 16 || television.height() != 9
                    || television.renditionWidth() != 256 || television.renditionHeight() != 144) throw new IllegalStateException("Quick TV acceptance construction did not persist its geometry and rendition");
            Cinemarr.LOGGER.info("Acceptance Quick TV: controller={} preset=144p dimensions=16x9 rendition=256x144 owner={}", controller.asLong(), player.getGameProfile().name());
        }
        for (int x = -9; x <= 9; x++) for (int z = 1; z <= 8; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 100; y <= 109; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        int playerIndex = Math.max(0, level.getServer().getPlayerList().getPlayers().indexOf(player));
        double cameraX = (playerIndex & 1) == 0 ? -1.5 : 1.5;
        player.teleportTo(level, cameraX, 100.0, 7.5, java.util.Set.of(), 180.0F, 0.0F, false);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(new BlockPos(0, 104, 0)));
    }
}
