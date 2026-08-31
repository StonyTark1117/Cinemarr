package stonytark.cinemarr.server;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CinemarrServer {
    private static final CinemarrServer INSTANCE = new CinemarrServer();
    private PlexVideoService videoService;
    private List<PlexVideoService.ResolvedLibrary> videoLibraries = Collections.emptyList();
    private ServerVideoManager videoManager;
    private stonytark.cinemarr.core.server.PlexConnectionLifecycle plexLifecycle;
    private long ticks;

    public static CinemarrServer instance() { return INSTANCE; }
    public static void register() { MinecraftForge.EVENT_BUS.register(INSTANCE); }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            java.nio.file.Path configDirectory = FMLPaths.CONFIGDIR.get();
            java.nio.file.Path canonical = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(CanonicalConfigFiles.SERVER_FILE_NAME);
            CanonicalConfigFiles.ServerConfig config = CanonicalConfigFiles.loadServerForLoader(canonical, configDirectory, "forge");
            CinemarrSettings.installServer(config);
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) stonytark.cinemarr.screen.CinemarrWorldScreens.get(level);
            java.nio.file.Path libraryFile = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(LibraryAllowlistFiles.FILE_NAME);
            List<LibraryRule> rules = LibraryAllowlistFiles.load(libraryFile);
            if (rules.isEmpty()) Cinemarr.LOGGER.warn("Cinemarr has no allowed Plex video libraries; edit {} and restart", libraryFile);
            else if (CinemarrSettings.plexToken().isBlank()) Cinemarr.LOGGER.warn("Cinemarr video libraries are configured, but CINEMARR_PLEX_TOKEN/plexToken is empty");
            else configurePlex(event.getServer(), rules);
            ticks = 0;
        } catch (Exception error) { throw new IllegalStateException("Unable to initialize Cinemarr server", error); }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (videoManager != null) { videoManager.close(); videoManager = null; }
        if (plexLifecycle != null) { plexLifecycle.close(); plexLifecycle = null; }
        videoService = null;
        videoLibraries = Collections.emptyList();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent.Post event) {
        ticks++;
        if (plexLifecycle != null) plexLifecycle.tick(System.currentTimeMillis());
        if (videoManager != null) {
            videoManager.tick();
            if (ticks % 10 == 0) for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) videoManager.synchronizeTrackingRadius(player);
        }
    }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) { if (videoManager != null && event.getEntity() instanceof ServerPlayer player) videoManager.synchronizeTrackingRadius(player); }
    @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) { if (videoManager != null && event.getEntity() instanceof ServerPlayer player) videoManager.playerLeft(player); }

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
    public boolean retryPlex() { return plexLifecycle != null && plexLifecycle.retry(); }
    private String plexState() { return plexLifecycle == null ? "disabled" : plexLifecycle.state().name().toLowerCase(java.util.Locale.ROOT); }
    private String unavailableStatus() { return "Cinemarr video "+plexState()+"; registeredTvs="+stonytark.cinemarr.core.server.TelevisionLifecycle.count()+"; activeStreams=0/"+CinemarrSettings.maximumConcurrentStreams(); }
    private String unavailableDiagnostics() { return "Plex="+plexState()+"; retryInMs="+(plexLifecycle==null?0:plexLifecycle.retryInMs(System.currentTimeMillis()))+"; lastFailure="+(plexLifecycle==null||plexLifecycle.lastFailure().isEmpty()?"none":plexLifecycle.lastFailure()); }
    private void configurePlex(net.minecraft.server.MinecraftServer server, List<LibraryRule> rules) {
        plexLifecycle = new stonytark.cinemarr.core.server.PlexConnectionLifecycle();
        plexLifecycle.configure(CinemarrSettings.plexUrl(), CinemarrSettings.plexToken(), rules, server::execute, connection -> {
            videoService = connection.service(); videoLibraries = connection.libraries();
            videoManager = new ServerVideoManager(server, videoService, videoLibraries, CinemarrVideoSavedData.get(server));
            Cinemarr.LOGGER.info("Validated {} allowed Plex video libraries{}", videoLibraries.size(), connection.requested() ? " after manual retry" : "");
        }, (message, delay) -> Cinemarr.LOGGER.warn("Plex unavailable; Cinemarr will retry in {} seconds: {}", delay / 1000, message));
    }

    private void prepareAcceptanceVideo(ServerPlayer player) {
        if (!ProtocolLimits.videoProbeEnabled() || videoManager == null) return;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos controller = new BlockPos(-1, 100, -1);
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(level);
        if (screens.television(controller) == null && "CinemarrVideoA".equals(player.getGameProfile().getName())) {
            for (int x = -8; x <= 7; x++) for (int y = 100; y <= 108; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, 0), Blocks.AIR.defaultBlockState());
            }
            var quick = CinemarrBlocks.QUICK_TV_144P.get();
            var state = quick.defaultBlockState().setValue(stonytark.cinemarr.screen.QuickTvBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(controller, state);
            quick.setPlacedBy(level, controller, state, player, ItemStack.EMPTY);
            CinemarrWorldScreens.Television television = screens.television(controller);
            if (television == null || television.width() != 16 || television.height() != 9
                    || television.renditionWidth() != 256 || television.renditionHeight() != 144) {
                throw new IllegalStateException("Quick TV acceptance construction did not persist its geometry and rendition");
            }
            Cinemarr.LOGGER.info("Acceptance Quick TV: controller={} preset=144p dimensions=16x9 rendition=256x144 owner={}",
                    controller.asLong(), player.getGameProfile().getName());
        }
        for (int x = -9; x <= 9; x++) for (int z = 1; z <= 8; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 100; y <= 109; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);
        int playerIndex = Math.max(0, player.server.getPlayerList().getPlayers().indexOf(player));
        double cameraX = (playerIndex & 1) == 0 ? -1.5 : 1.5;
        player.teleportTo(level, cameraX, 100.0, 7.5, 180.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(new BlockPos(0, 104, 0)));
    }
}
