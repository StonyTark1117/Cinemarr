package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
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
import stonytark.cinemarr.core.server.PlexConnectionLifecycle;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class CinemarrServer {
    private static volatile CinemarrServer INSTANCE;
    private PlexVideoService videoService;
    private List<PlexVideoService.ResolvedLibrary> videoLibraries = Collections.emptyList();
    private ServerVideoManager videoManager;
    private MinecraftServer server;
    private PlexConnectionLifecycle plexLifecycle;

    public CinemarrServer() { INSTANCE = this; }
    public static CinemarrServer instance() { return INSTANCE; }

    @SubscribeEvent public void started(ServerStartedEvent event) {
        try {
            server=event.getServer();
            java.nio.file.Path configDirectory = FMLPaths.CONFIGDIR.get();
            java.nio.file.Path canonical = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(CanonicalConfigFiles.SERVER_FILE_NAME);
            CanonicalConfigFiles.ServerConfig config = CanonicalConfigFiles.loadServerForLoader(
                    canonical, configDirectory, "neoforge");
            CinemarrSettings.installServer(config);
            TelevisionLifecycle.reset(null);
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) CinemarrWorldScreens.get(level);
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
                plexLifecycle=new PlexConnectionLifecycle();
                plexLifecycle.configure(CinemarrSettings.plexUrl(),CinemarrSettings.plexToken(),libraryRules,
                        server::execute,this::installPlex,(message,delay)->Cinemarr.LOGGER.warn(
                                "Plex unavailable; Cinemarr will retry in {} seconds: {}",delay/1000,message));
            }
        }
        catch (Exception error) { throw new IllegalStateException("Unable to initialize Cinemarr server", error); }
    }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (videoManager != null) { videoManager.close(); videoManager = null; }
        if(plexLifecycle!=null){plexLifecycle.close();plexLifecycle=null;}TelevisionLifecycle.reset(null); videoService = null; videoLibraries = Collections.emptyList();server=null;
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) { if(plexLifecycle!=null)plexLifecycle.tick(System.currentTimeMillis());if (videoManager != null) videoManager.tick(); }
    @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            prepareAcceptanceVideo(serverPlayer);
        }
    }
    @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer serverPlayer && videoManager!=null)videoManager.playerLeft(serverPlayer); }
    @SubscribeEvent public void chunkSent(ChunkWatchEvent.Sent event) { if(videoManager!=null)videoManager.chunkSent(event.getPlayer(),event.getLevel(),event.getPos()); }
    @SubscribeEvent public void chunkUnwatched(ChunkWatchEvent.UnWatch event) { if(videoManager!=null)videoManager.chunkUnwatched(event.getPlayer(),event.getLevel(),event.getPos()); }

    public void hello(ServerPlayer sender) {
        CinemarrNetwork.sendToPlayer(sender, new CinemarrPayloads.ServerHello(
                ProtocolLimits.VERSION, System.currentTimeMillis()));
    }
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
    public String videoStatus(){return videoManager==null?"Cinemarr video "+plexState()+"; registeredTvs="+TelevisionLifecycle.count()+"; activeStreams=0/"+CinemarrSettings.maximumConcurrentStreams()+"; attachedSessions=0; dormantSessions=0":videoManager.status();}
    public String videoDiagnostics(){return videoManager==null?"Plex="+plexState()+"; retryInMs="+(plexLifecycle==null?0:plexLifecycle.retryInMs(System.currentTimeMillis()))+"; lastFailure="+(plexLifecycle==null||plexLifecycle.lastFailure().isBlank()?"none":plexLifecycle.lastFailure())+"; registeredTvs="+TelevisionLifecycle.count()+"; libraries=0; activeStreams=0/"+CinemarrSettings.maximumConcurrentStreams():videoManager.diagnostics();}
    public boolean retryPlex(){return plexLifecycle!=null&&plexLifecycle.retry();}
    private String plexState(){return plexLifecycle==null?"disabled":plexLifecycle.state().name().toLowerCase(java.util.Locale.ROOT);}
    private void installPlex(PlexConnectionLifecycle.Connection connection){videoService=connection.service();videoLibraries=connection.libraries();videoManager=new ServerVideoManager(server,videoService,videoLibraries,CinemarrVideoSavedData.get(server));Cinemarr.LOGGER.info("Validated {} allowed Plex video libraries{}",videoLibraries.size(),connection.requested()?" after manual retry":"");}

    private void prepareAcceptanceVideo(ServerPlayer player) {
        if (!ProtocolLimits.videoProbeEnabled() || videoManager == null) return;
        boolean lifecycleProbe = ProtocolLimits.lifecycleProbeEnabled();
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos controller = lifecycleProbe ? new BlockPos(2047, 100, 2047) : new BlockPos(-1, 100, -1);
        int pixelPlaneZ = controller.getZ() + 1;
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(level);
        if (lifecycleProbe) {
            // Move first so the acceptance footprint has ordinary player chunk
            // residency in addition to the gate's bounded construction ticket.
            player.teleportTo(level, controller.getX(), 100.0, controller.getZ() + 8.5, 180.0F, 0.0F);
        }
        if ("CinemarrVideoA".equals(player.getGameProfile().getName())) {
            videoManager.resetAcceptanceSession();
        }
        if (screens.television(controller) == null && "CinemarrVideoA".equals(player.getGameProfile().getName())) {
            int minimumX = lifecycleProbe ? controller.getX() - 70 : -8, maximumX = lifecycleProbe ? controller.getX() + 70 : 7;
            int maximumY = lifecycleProbe ? 172 : 108;
            level.getChunk(controller.getX() >> 4, controller.getZ() >> 4);
            for (int chunkX = minimumX >> 4; chunkX <= maximumX >> 4; chunkX++) level.getChunk(chunkX, pixelPlaneZ >> 4);
            for (int x = minimumX; x <= maximumX; x++) for (int y = 100; y <= maximumY; y++) {
                level.setBlockAndUpdate(new BlockPos(x, y, pixelPlaneZ), Blocks.AIR.defaultBlockState());
                screens.removePixel(new BlockPos(x, y, pixelPlaneZ));
            }
            var quick = lifecycleProbe ? CinemarrBlocks.QUICK_TV_8K.get() : CinemarrBlocks.QUICK_TV_144P.get();
            var state = quick.defaultBlockState().setValue(stonytark.cinemarr.screen.QuickTvBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(controller, state);
            quick.setPlacedBy(level, controller, state, player, ItemStack.EMPTY);
            CinemarrWorldScreens.Television television = screens.television(controller);
            if (!lifecycleProbe && (television == null || television.width() != 16 || television.height() != 9
                    || television.renditionWidth() != 256 || television.renditionHeight() != 144)) {
                throw new IllegalStateException("Quick TV acceptance construction did not persist its geometry and rendition");
            }
            if (!lifecycleProbe) Cinemarr.LOGGER.info("Acceptance Quick TV: controller={} preset=144p dimensions=16x9 rendition=256x144 owner={}",
                    controller.asLong(), player.getGameProfile().getName());
        }
        if (lifecycleProbe) {
            return;
        }
        for (int x = -9; x <= 9; x++) for (int z = 1; z <= 8; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 100; y <= 109; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setDayTime(6000);
        int playerIndex = Math.max(0, eventPlayerIndex(player));
        double cameraX = (playerIndex & 1) == 0 ? -1.5 : 1.5;
        player.teleportTo(level, cameraX, 100.0, 7.5, 180.0F, 0.0F);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(new BlockPos(0, 104, 0)));
        videoManager.synchronizeTrackingRadius(player);
    }

    private static int eventPlayerIndex(ServerPlayer player) {
        return player.server.getPlayerList().getPlayers().indexOf(player);
    }
}
