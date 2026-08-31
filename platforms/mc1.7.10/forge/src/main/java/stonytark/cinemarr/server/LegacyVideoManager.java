package stonytark.cinemarr.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.HlsPlaylist;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.core.server.RedstoneControlPolicy;
import stonytark.cinemarr.core.server.SecretRedactor;
import stonytark.cinemarr.core.server.SlidingWindowRateLimiter;
import stonytark.cinemarr.core.server.VideoSessionCoordinator;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.RenditionPolicy;
import stonytark.cinemarr.network.LegacyNetwork;
import stonytark.cinemarr.network.LegacyPacketTypes;
import stonytark.cinemarr.screen.LegacyBlockPos;
import stonytark.cinemarr.screen.LegacyBlocks;
import stonytark.cinemarr.screen.LegacyWorldScreens;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Authoritative Forge 1.7.10 video sessions and bounded HLS relay. */
public final class LegacyVideoManager implements AutoCloseable, LegacyNetwork.ServerListener {
    private static final int PAGE_SIZE = 20;
    private final MinecraftServer server;
    private final PlexVideoService plex;
    private final List<PlexVideoService.ResolvedLibrary> libraries;
    private final LegacyVideoSavedData saved;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Cinemarr legacy video worker"); thread.setDaemon(true); return thread;
    });
    private final Queue<Runnable> mainThreadActions = new ConcurrentLinkedQueue<Runnable>();
    private final SlidingWindowRateLimiter browseLimiter = new SlidingWindowRateLimiter();
    private final SlidingWindowRateLimiter segmentLimiter = new SlidingWindowRateLimiter();
    private final Map<String, ActiveMedia> active = new ConcurrentHashMap<String, ActiveMedia>();
    private final ThreadLocal<StartOptions> startingOptions = new ThreadLocal<StartOptions>();
    private final Map<UUID, StartOptions> playbackOptions = new ConcurrentHashMap<UUID, StartOptions>();
    private final Map<UUID, String> playbackLibraries = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, TransferGrant> transferGrants = new ConcurrentHashMap<UUID, TransferGrant>();
    private final Set<UUID> restartingSessions = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Set<UUID> advancingSessions = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, List<QueuedVideo>> queues = new ConcurrentHashMap<UUID, List<QueuedVideo>>();
    private final Map<UUID, HealthRecord> clientHealth = new ConcurrentHashMap<UUID, HealthRecord>();
    private final Map<UUID, Set<String>> viewingSessions = new HashMap<UUID, Set<String>>();
    private final Map<UUID, Map<UUID, Long>> visibleTelevisions = new HashMap<UUID, Map<UUID, Long>>();
    private final Map<UUID, Boolean> receiverPower = new HashMap<UUID, Boolean>();
    private final VideoSessionCoordinator sessions;
    private long ticks;
    private long lastCheckpointMs;

    public LegacyVideoManager(MinecraftServer server, PlexVideoService plex,
                              List<PlexVideoService.ResolvedLibrary> libraries, LegacyVideoSavedData saved) {
        this.server = server; this.plex = plex;
        this.libraries = Collections.unmodifiableList(new ArrayList<PlexVideoService.ResolvedLibrary>(libraries));
        this.saved = saved;
        sessions = new VideoSessionCoordinator(CinemarrSettings.maximumConcurrentStreams(),
                CinemarrSettings.inactiveSessionGraceSeconds() * 1000L, this::startMedia);
        restoreSessions(); initializeReceiverPower(); TelevisionLifecycle.listener(this::televisionRemoved); LegacyNetwork.setServerListener(this);
    }

    private void restoreSessions() {
        long now = System.currentTimeMillis(); Set<String> tunedNames = new LinkedHashSet<String>();
        for (WorldServer world : server.worldServers) if (world != null) for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) {
            if (!television.sessionName().trim().isEmpty()) { sessions.tune(television.id(), television.sessionName()); TelevisionLifecycle.attachment(television.id(), true); tunedNames.add(television.sessionName()); }
        }
        for (LegacyVideoSavedData.Record record : saved.records()) {
            VideoSessionCoordinator.Snapshot current = sessions.snapshotIfPresent(record.sessionName(), now);
            PlexVideoService.ResolvedLibrary library = library(record.libraryId());
            if (current == null || library == null || !library.rule().allows(record.item(), 4)) continue;
            playbackLibraries.put(current.id(), record.libraryId());
            playbackOptions.put(current.id(), new StartOptions(renditionForSession(record.sessionName()),
                    new StreamSelection(Collections.<VideoStreamOption>emptyList(), record.audioStreamId(), record.subtitleStreamId())));
            queues.put(current.id(), new ArrayList<QueuedVideo>(record.queue()));
            sessions.restore(record.sessionName(), record.item(), record.positionMs(), record.paused(), now);
        }
    }

    public void televisionActivated(WorldServer world, LegacyWorldScreens.Television television) {
        VideoSessionCoordinator.Snapshot state = sessions.tune(television.id(), television.sessionName());
        TelevisionLifecycle.attachment(television.id(), true);
        LegacyWorldScreens.get(world).updateSession(television.controllerPos(), state.name());
        refreshAllTracking();
    }

    @Override public void accept(EntityPlayerMP player, LegacyPacketTypes.Type<?> type, Object message) {
        if (type == LegacyPacketTypes.CLIENT_HELLO) { prepareAcceptanceVideo(player); synchronizeTracking(player); sendLibraries(player); }
        else if (type == LegacyPacketTypes.VIDEO_LIBRARY_LIST_REQUEST) sendLibraries(player);
        else if (type == LegacyPacketTypes.VIDEO_BROWSE_REQUEST) browse(player, (VideoPackets.BrowseRequest) message);
        else if (type == LegacyPacketTypes.VIDEO_SESSION_COMMAND) command(player, (VideoPackets.SessionCommand) message);
        else if (type == LegacyPacketTypes.VIDEO_MANIFEST_REQUEST) manifest(player, (VideoPackets.SegmentManifestRequest) message);
        else if (type == LegacyPacketTypes.VIDEO_SEGMENT_REQUEST) segments(player, (VideoPackets.SegmentRequest) message);
        else if (type == LegacyPacketTypes.VIDEO_SEGMENT_ACKNOWLEDGEMENT) acknowledge(player, (VideoPackets.SegmentAcknowledgement) message);
        else if (type == LegacyPacketTypes.VIDEO_CLIENT_HEALTH) health(player, (VideoPackets.ClientHealth) message);
    }

    public void playerLeft(EntityPlayerMP player) {
        UUID id = player.getUniqueID(); long now = System.currentTimeMillis();
        Set<String> names = viewingSessions.get(id);
        if (names != null) for (String name : names) if (sessions.snapshotIfPresent(name, now) != null) sessions.viewerLeft(name, id, now);
        viewingSessions.remove(id); visibleTelevisions.remove(id); clientHealth.remove(id); transferGrants.remove(id);
    }

    public void tick() {
        Runnable action; while ((action = mainThreadActions.poll()) != null) action.run();
        if (++ticks % 10L == 0L) for (EntityPlayerMP player : players()) synchronizeTracking(player);
        long now = System.currentTimeMillis();
        try {
            sessions.tick(now); tickRedstoneReceivers(now); Set<String> names = sessions.sessionNames();
            for (String name : names) {
                VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(name, now);
                if (state != null && state.transcoding() && !anyTelevisionLoaded(name)) {
                    state = sessions.suspend(name, now); persist(state);
                    publishSession(state, "Suspended while TV chunks are unloaded", false, null);
                }
                if (state != null && state.transcoding() && !state.paused() && state.item() != null
                        && state.item().durationMs() > 0 && state.positionMs() >= state.item().durationMs()) advance(state, null);
            }
            if (now - lastCheckpointMs >= 5_000L) {
                lastCheckpointMs = now;
                for (String name : names) persist(sessions.snapshotIfPresent(name, now));
            }
        } catch (IOException failure) {
            Cinemarr.LOGGER.warn("Unable to stop inactive Plex video session: {}", SecretRedactor.message(failure,
                    CinemarrSettings.plexToken(), CinemarrSettings.plexUrl()));
        }
    }

    private void tickRedstoneReceivers(long now) throws IOException {
        Set<UUID> present = new LinkedHashSet<UUID>();
        Set<String> risingSessions = new LinkedHashSet<String>();
        for (WorldServer world : server.worldServers) if (world != null) {
            for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) {
                present.add(television.id());
                boolean powered = receiverPowered(world, television);
                Boolean previous = receiverPower.put(television.id(), powered);
                if (television.sessionName().trim().isEmpty()) continue;
                VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(television.sessionName(), now);
                if (state != null && RedstoneControlPolicy.action(previous != null && previous.booleanValue(), powered,
                        state.item() != null, state.paused()) != RedstoneControlPolicy.Action.NONE) {
                    risingSessions.add(television.sessionName());
                }
            }
        }
        receiverPower.keySet().retainAll(present);
        for (String name : risingSessions) {
            VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(name, now);
            if (state == null || state.item() == null) continue;
            if (state.paused()) { sessions.resume(name, now); restartIfNeeded(name, sessions.snapshot(name, now)); }
            else sessions.pause(name, now);
            VideoSessionCoordinator.Snapshot changed = sessions.snapshot(name, now);
            persist(changed);
            publishSession(changed, changed.paused() ? "Paused by redstone receiver" : "Resumed by redstone receiver", false, null);
        }
    }

    private void initializeReceiverPower() {
        for (WorldServer world : server.worldServers) if (world != null) {
            for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) {
                receiverPower.put(television.id(), receiverPowered(world, television));
            }
        }
    }

    private boolean receiverPowered(WorldServer world, LegacyWorldScreens.Television television) {
        int x = LegacyBlockPos.x(television.controllerPos());
        int y = LegacyBlockPos.y(television.controllerPos());
        int z = LegacyBlockPos.z(television.controllerPos());
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            int receiverX = x + offset[0], receiverY = y + offset[1], receiverZ = z + offset[2];
            if (world.getBlock(receiverX, receiverY, receiverZ) == LegacyBlocks.REDSTONE_RECEIVER
                    && world.isBlockIndirectlyGettingPowered(receiverX, receiverY, receiverZ)) return true;
        }
        return false;
    }

    private void prepareAcceptanceVideo(EntityPlayerMP player) {
        if (!ProtocolLimits.videoProbeEnabled() || !(player.worldObj instanceof WorldServer)) return;
        WorldServer world = (WorldServer) player.worldObj;
        boolean lifecycleProbe = ProtocolLimits.lifecycleProbeEnabled();
        int controllerX = lifecycleProbe ? 2047 : -1, controllerY = 100, controllerZ = lifecycleProbe ? 2047 : -1;
        int pixelPlaneZ = controllerZ + 1;
        long controller = LegacyBlockPos.pack(controllerX, controllerY, controllerZ);
        LegacyWorldScreens screens = LegacyWorldScreens.get(world);
        if (lifecycleProbe) {
            // Establish a player ticket before the large, bounded acceptance
            // construction so 1.7.10 cannot queue an edge chunk for unloading.
            player.playerNetServerHandler.setPlayerLocation(controllerX, 100.0D, controllerZ + 8.5D, 180.0F, 0.0F);
        }
        if ("CinemarrVideoA".equals(player.getCommandSenderName())) {
            saved.remove("cinemarr-acceptance");
            // The acceptance television is deliberately built at a fixed location so
            // screenshots from both clients are comparable. A newly generated 1.7.10
            // world can spawn far enough away that its two footprint chunks have not
            // been loaded yet. Persistent remote test worlds can also retain a stale
            // controller or saved pixel-index entries after an interrupted probe. Load
            // and reset the bounded acceptance area here; production placement remains
            // unchanged and continues to require player-loaded, unobstructed chunks.
            int minimumX = lifecycleProbe ? controllerX - 70 : -9, maximumX = lifecycleProbe ? controllerX + 70 : 8;
            int maximumY = lifecycleProbe ? 172 : 109;
            world.getChunkFromChunkCoords(controllerX >> 4, controllerZ >> 4);
            for (int chunkX = minimumX >> 4; chunkX <= maximumX >> 4; chunkX++) world.getChunkFromChunkCoords(chunkX, pixelPlaneZ >> 4);
            LegacyWorldScreens.Television previous = screens.removeController(controllerX, controllerY, controllerZ);
            if (previous != null) for (Long pixel : previous.pixels()) {
                int pixelX = LegacyBlockPos.x(pixel), pixelY = LegacyBlockPos.y(pixel), pixelZ = LegacyBlockPos.z(pixel);
                if (world.getBlock(pixelX, pixelY, pixelZ) == stonytark.cinemarr.screen.LegacyBlocks.SCREEN_PIXEL) {
                    world.setBlockToAir(pixelX, pixelY, pixelZ);
                }
            }
            world.setBlockToAir(controllerX, controllerY, controllerZ);
            for (int x = minimumX; x <= maximumX; x++) for (int y = 99; y <= maximumY; y++) {
                world.setBlockToAir(x, y, pixelPlaneZ);
                screens.removePixel(x, y, pixelPlaneZ);
            }
            player.rotationYaw = 0.0F;
            net.minecraft.block.Block quick = lifecycleProbe ? LegacyBlocks.QUICK_TV_8K : LegacyBlocks.QUICK_TV_144P;
            world.setBlock(controllerX, controllerY, controllerZ, quick, 3, 3);
            ((stonytark.cinemarr.screen.LegacyQuickTvBlock) quick).onBlockPlacedBy(
                    world, controllerX, controllerY, controllerZ, player,
                    new net.minecraft.item.ItemStack(quick));
            LegacyWorldScreens.Television television = screens.television(controller);
            if (!lifecycleProbe && (television == null || television.width() != 16 || television.height() != 9
                    || television.renditionWidth() != 256 || television.renditionHeight() != 144)) {
                throw new IllegalStateException("Quick TV acceptance construction did not persist its geometry and rendition");
            }
            if (!lifecycleProbe) Cinemarr.LOGGER.info("Acceptance Quick TV: controller={} preset=144p dimensions=16x9 rendition=256x144 owner={}",
                    controller, player.getCommandSenderName());
        }
        if (lifecycleProbe) {
            return;
        }
        for (int x = -9; x <= 9; x++) for (int z = 1; z <= 8; z++) {
            world.setBlock(x, 99, z, net.minecraft.init.Blocks.stone, 0, 3);
            for (int y = 100; y <= 109; y++) world.setBlockToAir(x, y, z);
        }
        world.setWorldTime(6000L);
        int playerIndex = Math.max(0, server.getConfigurationManager().playerEntityList.indexOf(player));
        double cameraX = (playerIndex & 1) == 0 ? -1.5D : 1.5D;
        player.playerNetServerHandler.setPlayerLocation(cameraX, 100.0D, 7.5D, 180.0F, 0.0F);
    }

    private void synchronizeTracking(EntityPlayerMP player) {
        if (!LegacyNetwork.serverHandshakeComplete(player) || !(player.worldObj instanceof WorldServer)) return;
        WorldServer world = (WorldServer) player.worldObj; int radius = Math.max(2, server.getConfigurationManager().getViewDistance());
        int centerX = ((int) Math.floor(player.posX)) >> 4, centerZ = ((int) Math.floor(player.posZ)) >> 4;
        Map<UUID, LegacyWorldScreens.Television> televisions = new LinkedHashMap<UUID, LegacyWorldScreens.Television>();
        for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) for (Long pixel : television.pixels()) {
            if (Math.abs((LegacyBlockPos.x(pixel) >> 4) - centerX) <= radius && Math.abs((LegacyBlockPos.z(pixel) >> 4) - centerZ) <= radius) {
                televisions.put(television.id(), television); break;
            }
        }
        refreshTracking(player, televisions);
    }

    private void refreshTracking(EntityPlayerMP player, Map<UUID, LegacyWorldScreens.Television> televisions) {
        UUID playerId = player.getUniqueID(); long now = System.currentTimeMillis(); Set<String> nextSessions = new LinkedHashSet<String>();
        for (LegacyWorldScreens.Television television : televisions.values()) if (!television.sessionName().isEmpty()
                && sessions.snapshotIfPresent(television.sessionName(), now) != null) nextSessions.add(television.sessionName());
        Set<String> previous = viewingSessions.get(playerId);
        if (previous == null) previous = Collections.emptySet();
        for (String name : previous) if (!nextSessions.contains(name)) sessions.viewerLeft(name, playerId, now);
        for (String name : nextSessions) if (!previous.contains(name)) sessions.viewerEntered(name, playerId);
        viewingSessions.put(playerId, nextSessions);
        for (String name : nextSessions) {
            VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(name, now);
            if (state != null && state.item() != null && !state.transcoding() && !state.paused()) restartIfNeeded(name, state);
        }
        Map<UUID, Long> previousTvs = visibleTelevisions.get(playerId);
        if (previousTvs == null) previousTvs = Collections.emptyMap();
        for (Map.Entry<UUID, Long> old : previousTvs.entrySet()) if (!televisions.containsKey(old.getKey())) {
            send(player, LegacyPacketTypes.VIDEO_TELEVISION_REMOVED, new VideoPackets.TelevisionRemoved(old.getValue()));
        }
        Map<UUID, Long> nextTvs = new LinkedHashMap<UUID, Long>();
        for (LegacyWorldScreens.Television television : televisions.values()) {
            nextTvs.put(television.id(), television.controllerPos()); if (!previousTvs.containsKey(television.id())) sendCurrent(player, television, now);
        }
        visibleTelevisions.put(playerId, nextTvs);
    }

    private void refreshAllTracking() { for (EntityPlayerMP player : players()) synchronizeTracking(player); }

    private void sendCurrent(EntityPlayerMP player, LegacyWorldScreens.Television television, long now) {
        VideoSessionCoordinator.Snapshot state = television.sessionName().isEmpty() ? null : sessions.snapshotIfPresent(television.sessionName(), now);
        if (state == null) sendIdle(player, television, "TV is idle");
        else { sendState(player, television, state, television.presentationMode(), state.paused() ? "Paused" : "Playing"); sendQueue(player, state); sendManifest(player, state); }
    }

    public void sendLibraries(EntityPlayerMP player) {
        List<VideoPackets.LibrarySummary> visible = new ArrayList<VideoPackets.LibrarySummary>(); int permission = permission(player);
        for (PlexVideoService.ResolvedLibrary library : libraries) if (permission >= library.rule().permissionLevel()) {
            visible.add(new VideoPackets.LibrarySummary(library.rule().id(), library.rule().displayName(),
                    library.rule().allowMovies(), library.rule().allowShows(), library.rule().permissionLevel()));
        }
        send(player, LegacyPacketTypes.VIDEO_LIBRARY_LIST, new VideoPackets.LibraryList(visible));
    }

    public void browse(final EntityPlayerMP player, final VideoPackets.BrowseRequest request) {
        if (!browseLimiter.allow(player.getUniqueID(), 8, System.currentTimeMillis())) { error(player, "Video browse rate limit exceeded"); return; }
        final PlexVideoService.ResolvedLibrary library = library(request.libraryId(), player); if (library == null) return;
        final int playerPermission = permission(player);
        CompletableFuture.supplyAsync(() -> {
            try { return plex.browse(library, request.parentKey(), request.query(), Math.max(0, request.page()), PAGE_SIZE, playerPermission); }
            catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((page, failure) -> mainThreadActions.add(() -> {
            if (failure != null) { failure(player, failure); return; }
            send(player, LegacyPacketTypes.VIDEO_BROWSE_RESULTS, new VideoPackets.BrowseResults(request.libraryId(), request.parentKey(),
                    request.query(), Math.max(0, request.page()), page.hasMore(), page.items()));
        }));
    }

    public void command(final EntityPlayerMP player, final VideoPackets.SessionCommand command) {
        if (!(player.worldObj instanceof WorldServer)) return;
        final LegacyWorldScreens screenData = LegacyWorldScreens.get((WorldServer) player.worldObj);
        final LegacyWorldScreens.Television television = screenData.television(command.controllerPos());
        if (television == null) { error(player, "The TV Controller has no active screen"); return; }
        if (!canControl(player, television)) { error(player, "Only the TV owner or an operator can control this TV"); return; }
        try {
            String requested = command.sessionName().trim().isEmpty() ? television.sessionName() : command.sessionName();
            VideoSessionCoordinator.Snapshot selected = sessions.tune(television.id(), requested);
            TelevisionLifecycle.attachment(television.id(), true);
            if (selected.item() == null) selected = restoreDormant(selected);
            final VideoSessionCoordinator.Snapshot tuned = selected;
            screenData.updateSession(command.controllerPos(), tuned.name()); refreshAllTracking();
            if (command.action() == VideoPackets.SessionAction.SET_PRESENTATION) screenData.updatePresentation(command.controllerPos(), command.presentationMode());
            final PresentationMode presentation = command.action() == VideoPackets.SessionAction.SET_PRESENTATION
                    ? command.presentationMode() : television.presentationMode();
            if (command.action() == VideoPackets.SessionAction.TUNE) { publish(player, television, tuned, presentation, "Tuned", true); return; }
            if (command.expectedGeneration() != tuned.generation()) { error(player, "TV state changed; refresh before controlling it"); return; }
            switch (command.action()) {
                case PAUSE: sessions.pause(tuned.name(), System.currentTimeMillis()); publishSession(sessions.snapshot(tuned.name(),System.currentTimeMillis()), "Paused", false, player); break;
                case RESUME: sessions.resume(tuned.name(), System.currentTimeMillis()); VideoSessionCoordinator.Snapshot resumed=sessions.snapshot(tuned.name(),System.currentTimeMillis());persist(resumed);publishSession(resumed,"Resuming",false,player);restartIfNeeded(tuned.name(),resumed);break;
                case SEEK: asyncSeek(player, television, tuned, command, presentation); break;
                case PLAY: case SET_STREAMS: asyncPlay(player, television, tuned, command, presentation); break;
                case STOP:
                    VideoSessionCoordinator.Snapshot stopped = sessions.stop(tuned.name(), System.currentTimeMillis());
                    playbackLibraries.remove(tuned.id()); playbackOptions.remove(tuned.id()); queues.remove(tuned.id()); saved.remove(tuned.name());
                    publishSession(stopped, "Stopped", false, player); break;
                case SET_PRESENTATION: publish(player, television, tuned, presentation, "Presentation updated", false); break;
                case QUEUE: asyncQueue(player, tuned, command); break;
                case REMOVE_QUEUE: removeQueue(player, tuned, (int) command.seekPositionMs()); break;
                case CLEAR_QUEUE: queues.remove(tuned.id()); persist(tuned); publishQueue(tuned, player); break;
                case SKIP: advance(tuned, player); break;
                case CONTINUE_EPISODE: continueEpisode(player, tuned); break;
                default: error(player, "Unsupported TV action");
            }
        } catch (Exception failure) { failure(player, failure); }
    }

    private void publishSnapshot(EntityPlayerMP player, LegacyWorldScreens.Television television, String name,
                                 PresentationMode mode, String message, boolean manifest) {
        VideoSessionCoordinator.Snapshot state = sessions.snapshot(name, System.currentTimeMillis()); persist(state);
        publish(player, television, state, mode, message, manifest);
    }

    private void asyncPlay(final EntityPlayerMP player, final LegacyWorldScreens.Television television,
                           final VideoSessionCoordinator.Snapshot tuned, final VideoPackets.SessionCommand command,
                           final PresentationMode presentation) {
        String libraryId = command.action() == VideoPackets.SessionAction.SET_STREAMS ? playbackLibraries.get(tuned.id()) : command.libraryId();
        final PlexVideoService.ResolvedLibrary library = library(libraryId, player); if (library == null) return;
        final int playerPermission = permission(player);
        final RenditionPolicy.Dimensions rendition = renditionFor(television);
        CompletableFuture.supplyAsync(() -> {
            try {
                String itemKey = command.itemKey().trim().isEmpty() && tuned.item() != null ? tuned.item().key() : command.itemKey();
                PlexVideoService.PlaybackMetadata metadata = plex.metadataDetails(itemKey); VideoMediaItem item = metadata.item();
                if (!library.rule().allows(item, playerPermission)) throw new IOException("Video item is not allowed by this library policy");
                StartOptions options = new StartOptions(rendition, selection(metadata.streams(), command.audioStreamId(),
                        command.subtitleStreamId(), command.action() != VideoPackets.SessionAction.SET_STREAMS));
                startingOptions.set(options); VideoSessionCoordinator.Snapshot state;
                try { state = sessions.play(tuned.name(), item, command.action() == VideoPackets.SessionAction.SET_STREAMS
                        ? tuned.positionMs() : command.seekPositionMs(), System.currentTimeMillis(), tuned.generation()); }
                finally { startingOptions.remove(); }
                playbackOptions.put(tuned.id(), options); playbackLibraries.put(tuned.id(), library.rule().id()); return state;
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((state, failure) -> mainThreadActions.add(() -> {
            if (failure != null) { failure(player, failure); return; } if (!current(state)) return; persist(state); publish(player, television, state, presentation, "Buffering", true);
        }));
    }

    private void asyncSeek(final EntityPlayerMP player, final LegacyWorldScreens.Television television,
                           final VideoSessionCoordinator.Snapshot tuned, final VideoPackets.SessionCommand command,
                           final PresentationMode presentation) {
        final RenditionPolicy.Dimensions rendition = renditionFor(television);
        CompletableFuture.runAsync(() -> {
            try {
                StartOptions previous = playbackOptions.get(tuned.id());
                StartOptions options = new StartOptions(rendition, previous == null
                        ? new StreamSelection(Collections.<VideoStreamOption>emptyList(), -1, -1) : previous.streams);
                startingOptions.set(options); try { sessions.seek(tuned.name(), command.seekPositionMs(), System.currentTimeMillis(), tuned.generation()); playbackOptions.put(tuned.id(), options); }
                finally { startingOptions.remove(); }
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((unused, failure) -> mainThreadActions.add(() -> {
            if (failure != null) { failure(player, failure); return; }
            VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(tuned.id(), tuned.generation() + 1, System.currentTimeMillis());
            if (!current(state)) return; persist(state); publish(player, television, state, presentation, "Buffering", true);
        }));
    }

    private void asyncQueue(final EntityPlayerMP player, final VideoSessionCoordinator.Snapshot tuned,
                            final VideoPackets.SessionCommand command) {
        final PlexVideoService.ResolvedLibrary library = library(command.libraryId(), player); if (library == null) return;
        final int playerPermission = permission(player);
        CompletableFuture.supplyAsync(() -> {
            try { VideoMediaItem item = plex.metadata(command.itemKey()); if (!library.rule().allows(item, playerPermission)) throw new IOException("Video item is not allowed by this library policy"); return new QueuedVideo(library.rule().id(), item); }
            catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((entry, failure) -> mainThreadActions.add(() -> {
            if (failure != null) { failure(player, failure); return; }
            VideoSessionCoordinator.Snapshot current = sessions.snapshotIfPresent(tuned.id(), tuned.generation(), System.currentTimeMillis());
            if (current == null) { error(player, "TV state changed while queueing"); return; }
            List<QueuedVideo> queue = queues.get(tuned.id()); if (queue == null) { queue = new ArrayList<QueuedVideo>(); queues.put(tuned.id(), queue); }
            if (queue.size() >= CinemarrSettings.queueLimit()) { error(player, "Video queue is full"); return; }
            queue.add(entry); if (current.item() == null) advance(current, player); else { persist(current); publishQueue(current, player); }
        }));
    }

    private void removeQueue(EntityPlayerMP player, VideoSessionCoordinator.Snapshot state, int index) {
        List<QueuedVideo> queue = queues.get(state.id());
        if (queue == null || index < 0 || index >= queue.size()) { error(player, "Invalid video queue entry"); return; }
        queue.remove(index); if (queue.isEmpty()) queues.remove(state.id()); persist(state); publishQueue(state, player);
    }

    private void advance(final VideoSessionCoordinator.Snapshot expected, final EntityPlayerMP requester) {
        if (!advancingSessions.add(expected.id())) return;
        List<QueuedVideo> queue = queues.get(expected.id()); final QueuedVideo next = queue == null || queue.isEmpty() ? null : queue.get(0);
        if (next == null) {
            try { VideoSessionCoordinator.Snapshot stopped = sessions.stop(expected.name(), System.currentTimeMillis()); playbackLibraries.remove(expected.id());
                playbackOptions.remove(expected.id()); queues.remove(expected.id()); saved.remove(expected.name()); publishSession(stopped, "Queue finished", false, requester);
            } catch (IOException failure) { if (requester != null) failure(requester, failure); }
            finally { advancingSessions.remove(expected.id()); }
            return;
        }
        final RenditionPolicy.Dimensions rendition = renditionForSession(expected.name());
        CompletableFuture.supplyAsync(() -> {
            try {
                PlexVideoService.ResolvedLibrary library = library(next.libraryId()); if (library == null) throw new IOException("Queued video library is no longer configured");
                PlexVideoService.PlaybackMetadata metadata = plex.metadataDetails(next.item().key());
                if (!library.rule().allows(metadata.item(), 4)) throw new IOException("Queued video item is no longer allowed");
                StartOptions options = new StartOptions(rendition, selection(metadata.streams(), -1, -1, true));
                startingOptions.set(options); VideoSessionCoordinator.Snapshot state;
                try { state = sessions.play(expected.name(), metadata.item(), 0, System.currentTimeMillis(), expected.generation()); }
                finally { startingOptions.remove(); }
                playbackLibraries.put(expected.id(), next.libraryId()); playbackOptions.put(expected.id(), options); return state;
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((state, failure) -> mainThreadActions.add(() -> {
            advancingSessions.remove(expected.id());
            if (failure != null) { if (requester != null) failure(requester, failure); else Cinemarr.LOGGER.warn("Unable to advance video queue: {}", SecretRedactor.message(failure, CinemarrSettings.plexToken(), CinemarrSettings.plexUrl())); return; }
            if (!current(state)) return;
            List<QueuedVideo> current = queues.get(expected.id()); if (current != null) { current.remove(next); if (current.isEmpty()) queues.remove(expected.id()); }
            persist(state); publishSession(state, "Playing next queued video", true, requester);
        }));
    }

    private void continueEpisode(final EntityPlayerMP player, final VideoSessionCoordinator.Snapshot expected) {
        if (expected.item() == null || expected.item().kind() != MediaKind.EPISODE) { error(player, "Continue is available only for episodes"); return; }
        final PlexVideoService.ResolvedLibrary library = library(playbackLibraries.get(expected.id()));
        if (library == null) { error(player, "Playback library is no longer configured"); return; }
        final RenditionPolicy.Dimensions rendition = renditionForSession(expected.name());
        CompletableFuture.supplyAsync(() -> {
            try {
                VideoMediaItem next = plex.nextEpisode(expected.item().key()); if (next == null) throw new IOException("This is the final episode");
                if (!library.rule().allows(next, 4)) throw new IOException("Next episode is not allowed by this library policy");
                PlexVideoService.PlaybackMetadata metadata = plex.metadataDetails(next.key());
                StartOptions options = new StartOptions(rendition, selection(metadata.streams(), -1, -1, true));
                startingOptions.set(options); VideoSessionCoordinator.Snapshot state;
                try { state = sessions.play(expected.name(), metadata.item(), 0, System.currentTimeMillis(), expected.generation()); }
                finally { startingOptions.remove(); }
                playbackOptions.put(expected.id(), options); return state;
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((state, failure) -> mainThreadActions.add(() -> {
            if (failure != null) { failure(player, failure); return; } if (!current(state)) return; persist(state); publishSession(state, "Continuing with next episode", true, player);
        }));
    }

    public void segments(final EntityPlayerMP player, final VideoPackets.SegmentRequest request) {
        if (request.requestId() < 1 || request.chunkCount() < 1 || request.chunkCount() > 8 || request.firstChunk() < 0
                || request.firstChunk() > PlexVideoService.MAX_SEGMENT_BYTES / ProtocolLimits.MAX_VIDEO_CHUNK_BYTES
                || !segmentLimiter.allow(player.getUniqueID(), 40, System.currentTimeMillis())) { error(player, "Invalid or excessive segment request"); return; }
        if (!sessions.isViewer(request.sessionId(), request.generation(), player.getUniqueID())) { error(player, "Video media is available only while tracking its screen"); return; }
        final ActiveMedia media = active.get(key(request.sessionId(), request.generation()));
        VideoSessionCoordinator.Snapshot timeline = sessions.snapshotIfPresent(request.sessionId(), request.generation(), System.currentTimeMillis());
        if (media == null || timeline == null || request.segmentIndex() < 0 || request.segmentIndex() >= media.segmentCount()) { error(player, "Video segment is no longer available"); return; }
        if (media.presentationTime(request.segmentIndex()) > timeline.positionMs() + ProtocolLimits.MAX_VIDEO_SEGMENT_LEAD_MS) { error(player, "Video segment request exceeds playback lead limit"); return; }
        transferGrants.put(player.getUniqueID(), new TransferGrant(request));
        CompletableFuture.supplyAsync(() -> { try { return media.segment(request.segmentIndex()); } catch (IOException failure) { throw new WrappedFailure(failure); } }, workers)
                .whenComplete((segment, failure) -> mainThreadActions.add(() -> {
                    if (failure != null) { failure(player, failure); return; }
                    int total = (segment.bytes.length + ProtocolLimits.MAX_VIDEO_CHUNK_BYTES - 1) / ProtocolLimits.MAX_VIDEO_CHUNK_BYTES;
                    if (request.firstChunk() >= total) { transferGrants.remove(player.getUniqueID()); error(player, "Invalid video chunk window"); return; }
                    int end = Math.min(total, request.firstChunk() + request.chunkCount());
                    for (int index = request.firstChunk(); index < end; index++) {
                        int from = index * ProtocolLimits.MAX_VIDEO_CHUNK_BYTES, to = Math.min(segment.bytes.length, from + ProtocolLimits.MAX_VIDEO_CHUNK_BYTES);
                        send(player, LegacyPacketTypes.VIDEO_SEGMENT_CHUNK, new VideoPackets.SegmentChunk(request.sessionId(), request.generation(),
                                request.requestId(), request.segmentIndex(), index, total, segment.reference.pts, true, segment.sha,
                                Arrays.copyOfRange(segment.bytes, from, to)));
                    }
                }));
    }

    public void manifest(EntityPlayerMP player, VideoPackets.SegmentManifestRequest request) {
        if (request.firstSegmentIndex() < 0 || !sessions.isViewer(request.sessionId(), request.generation(), player.getUniqueID())) {
            error(player, "Video manifest is available only while tracking its screen"); return;
        }
        ActiveMedia media = active.get(key(request.sessionId(), request.generation()));
        VideoSessionCoordinator.Snapshot timeline = sessions.snapshotIfPresent(request.sessionId(), request.generation(), System.currentTimeMillis());
        if (media == null || timeline == null || request.firstSegmentIndex() >= media.segmentCount()
                || media.presentationTime(request.firstSegmentIndex()) > timeline.positionMs() + ProtocolLimits.MAX_VIDEO_SEGMENT_LEAD_MS) { error(player, "Video manifest is no longer available"); return; }
        sendManifest(player, request.sessionId(), request.generation(), media, request.firstSegmentIndex(), media.durationMs);
    }

    public void acknowledge(EntityPlayerMP player, VideoPackets.SegmentAcknowledgement value) {
        TransferGrant grant = transferGrants.remove(player.getUniqueID());
        if (value.bufferedMs() < 0 || value.bufferedMs() > 30_000 || grant == null || !grant.matches(value)) error(player, "Invalid video buffer acknowledgement");
    }
    public void health(EntityPlayerMP player, VideoPackets.ClientHealth value) {
        if (Math.abs(value.driftMs()) > 30_000 || value.bufferedMs() < 0 || value.bufferedMs() > 60_000
                || !sessions.isViewer(value.sessionId(), value.generation(), player.getUniqueID())) { error(player, "Invalid video health report"); return; }
        clientHealth.put(player.getUniqueID(), new HealthRecord(value, System.currentTimeMillis()));
    }

    public String status() { return "Cinemarr video: " + TelevisionLifecycle.count() + " registered TV(s), "
            + sessions.activeStreamCount() + "/" + CinemarrSettings.maximumConcurrentStreams() + " active stream(s), "
            + TelevisionLifecycle.attachedSessionCount() + " attached session(s), " + dormantSessions() + " dormant session(s)"; }
    public String diagnostics() {
        long now = System.currentTimeMillis(); int cached = 0, reports = 0, recoveries = 0, drops = 0, underruns = 0, queued = 0; long drift = 0;
        for (ActiveMedia media : active.values()) cached += media.cachedSegments();
        for (HealthRecord record : clientHealth.values()) if (now - record.receivedAt <= 30_000L) {
            reports++; recoveries += record.value.decoderRecoveries(); drops += record.value.videoDrops();
            underruns += record.value.audioUnderruns(); drift = Math.max(drift, Math.abs(record.value.driftMs()));
        }
        for (List<QueuedVideo> queue : queues.values()) queued += queue.size();
        return "Plex=ready; libraries=" + libraries.size() + "; registeredTvs=" + TelevisionLifecycle.count()
                + "; attachedTvs=" + TelevisionLifecycle.attachedTelevisionCount() + "; attachedSessions=" + TelevisionLifecycle.attachedSessionCount()
                + "; activeStreams=" + sessions.activeStreamCount() + "/" + CinemarrSettings.maximumConcurrentStreams() + "; dormantSessions=" + dormantSessions()
                + "; cachedSegments=" + cached + "; queued=" + queued + "; trackingClients=" + visibleTelevisions.size()
                + "; healthReports=" + reports + "; decoderRecoveries=" + recoveries + "; videoDrops=" + drops
                + "; audioUnderruns=" + underruns + "; maxDriftMs=" + drift;
    }

    private void restartIfNeeded(final String name, final VideoSessionCoordinator.Snapshot expected) {
        if (!restartingSessions.add(expected.id())) return; final LegacyVideoSavedData.Record record = saved.record(name);
        if (record == null) { restartingSessions.remove(expected.id()); return; }
        final RenditionPolicy.Dimensions rendition = renditionForSession(name);
        CompletableFuture.supplyAsync(() -> {
            try {
                PlexVideoService.ResolvedLibrary library = library(record.libraryId()); if (library == null) throw new IOException("Saved video library is no longer configured");
                PlexVideoService.PlaybackMetadata metadata = plex.metadataDetails(record.item().key());
                if (!library.rule().allows(metadata.item(), 4)) throw new IOException("Saved video item is no longer allowed");
                StreamSelection streams;
                try { streams = selection(metadata.streams(), record.audioStreamId(), record.subtitleStreamId(), false); }
                catch (IOException unavailable) { streams = selection(metadata.streams(), -1, -1, false); }
                StartOptions options = new StartOptions(rendition, streams); startingOptions.set(options); VideoSessionCoordinator.Snapshot state;
                try { state = sessions.restart(name, System.currentTimeMillis(), expected.generation()); }
                finally { startingOptions.remove(); }
                playbackOptions.put(expected.id(), options); playbackLibraries.put(expected.id(), record.libraryId()); return state;
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((state, failure) -> mainThreadActions.add(() -> {
            restartingSessions.remove(expected.id());
            if (failure != null) { Cinemarr.LOGGER.warn("Unable to resume saved video session {}: {}", name,
                    SecretRedactor.message(failure, CinemarrSettings.plexToken(), CinemarrSettings.plexUrl())); return; }
            if (!current(state)) return; persist(state); publishSession(state, state.paused() ? "Paused" : "Playing", true, null);
        }));
    }

    private void persist(VideoSessionCoordinator.Snapshot state) {
        if (state == null || state.item() == null) return;
        String library = playbackLibraries.get(state.id()); StartOptions options = playbackOptions.get(state.id());
        if (library == null || options == null) return;
        List<QueuedVideo> queue = queues.get(state.id()); if (queue == null) queue = Collections.emptyList();
        saved.put(new LegacyVideoSavedData.Record(state.name(), library, state.item(), state.positionMs(), state.paused(),
                options.streams.audioId, options.streams.subtitleId, queue));
    }
    private boolean current(VideoSessionCoordinator.Snapshot state) { return state != null && sessions.snapshotIfPresent(state.id(), state.generation(), System.currentTimeMillis()) != null; }
    private int dormantSessions() { int count = 0; long now = System.currentTimeMillis(); for (LegacyVideoSavedData.Record record : saved.records()) if (sessions.snapshotIfPresent(record.sessionName(), now) == null) count++; return count; }
    private boolean anyTelevisionLoaded(String name) { for (WorldServer world : server.worldServers) if (world != null) for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) if (name.equals(television.sessionName())) { if (!world.blockExists(stonytark.cinemarr.screen.LegacyBlockPos.x(television.controllerPos()), stonytark.cinemarr.screen.LegacyBlockPos.y(television.controllerPos()), stonytark.cinemarr.screen.LegacyBlockPos.z(television.controllerPos()))) continue; boolean loaded = true; for (Long packed : television.pixels()) if (!world.blockExists(stonytark.cinemarr.screen.LegacyBlockPos.x(packed), stonytark.cinemarr.screen.LegacyBlockPos.y(packed), stonytark.cinemarr.screen.LegacyBlockPos.z(packed))) { loaded = false; break; } if (loaded) return true; } return false; }

    private VideoSessionCoordinator.Snapshot restoreDormant(VideoSessionCoordinator.Snapshot tuned) {
        LegacyVideoSavedData.Record record = saved.record(tuned.name());
        PlexVideoService.ResolvedLibrary library = record == null ? null : library(record.libraryId());
        if (record == null || library == null || !library.rule().allows(record.item(), 4)) return tuned;
        playbackLibraries.put(tuned.id(), record.libraryId());
        playbackOptions.put(tuned.id(), new StartOptions(renditionForSession(tuned.name()),
                new StreamSelection(Collections.<VideoStreamOption>emptyList(), record.audioStreamId(), record.subtitleStreamId())));
        queues.put(tuned.id(), new ArrayList<QueuedVideo>(record.queue()));
        return sessions.restore(tuned.name(), record.item(), record.positionMs(), true, System.currentTimeMillis());
    }

    public void televisionRemoved(UUID televisionId, String sessionName) {
        if (televisionId == null) return;
        long now = System.currentTimeMillis();
        VideoSessionCoordinator.Snapshot state = sessionName == null || sessionName.trim().isEmpty()
                ? null : sessions.snapshotIfPresent(sessionName, now);
        if (state != null && state.televisions().contains(televisionId) && state.televisions().size() == 1 && state.item() != null) {
            String library = playbackLibraries.get(state.id()); StartOptions options = playbackOptions.get(state.id());
            List<QueuedVideo> queue = queues.get(state.id()); if (queue == null) queue = Collections.emptyList();
            if (library != null && options != null) saved.put(new LegacyVideoSavedData.Record(state.name(), library,
                    state.item(), state.positionMs(), true, options.streams.audioId, options.streams.subtitleId, queue));
        }
        try { sessions.untune(televisionId); }
        catch (IOException failure) { Cinemarr.LOGGER.warn("Unable to stop removed TV session: {}",
                SecretRedactor.message(failure, CinemarrSettings.plexToken(), CinemarrSettings.plexUrl())); }
        if (state != null && state.televisions().size() == 1) {
            playbackLibraries.remove(state.id()); playbackOptions.remove(state.id()); queues.remove(state.id());
            restartingSessions.remove(state.id()); advancingSessions.remove(state.id());
        }
        refreshAllTracking();
    }

    private static RenditionPolicy.Dimensions renditionFor(LegacyWorldScreens.Television television) {
        return RenditionPolicy.chooseForScreen(television.width(), television.height(), television.renditionWidth(),
                television.renditionHeight(), 1920, 1080, 1920, 1080);
    }
    private RenditionPolicy.Dimensions renditionForSession(String name) {
        int width = 0, height = 0;
        for (WorldServer world : server.worldServers) if (world != null) for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) {
            if (name.equals(television.sessionName())) { RenditionPolicy.Dimensions value = renditionFor(television); width = Math.max(width, value.width()); height = Math.max(height, value.height()); }
        }
        return width == 0 ? RenditionPolicy.choose(1280, 720, 1920, 1080, 1920, 1080)
                : RenditionPolicy.choose(width, height, width, height, width, height);
    }
    private List<LegacyWorldScreens.Television> televisionsForSession(String name) {
        List<LegacyWorldScreens.Television> values = new ArrayList<LegacyWorldScreens.Television>();
        for (WorldServer world : server.worldServers) if (world != null) for (LegacyWorldScreens.Television television : LegacyWorldScreens.get(world).televisions()) {
            if (name.equals(television.sessionName())) values.add(television);
        }
        return values;
    }

    private VideoSessionCoordinator.MediaHandle startMedia(final UUID sessionId, final long generation,
                                                           VideoMediaItem item, long offset) throws IOException {
        StartOptions options = startingOptions.get(); if (options == null) options = new StartOptions(
                RenditionPolicy.choose(1280, 720, 1920, 1080, 1920, 1080),
                new StreamSelection(Collections.<VideoStreamOption>emptyList(), -1, -1));
        final RenditionPolicy.Dimensions dimensions = options.rendition; final StreamSelection selection = options.streams;
        final PlexVideoService.VideoSession plexSession = plex.start(item, dimensions, offset,
                selection.audioId < 0 ? null : selection.audioId, selection.subtitleId < 0 ? Integer.valueOf(0) : selection.subtitleId);
        try {
            PlexVideoService.MediaPlaylist playlist = plex.mediaPlaylist(plexSession, offset);
            List<SegmentReference> references = parsePlaylist(playlist);
            final String key = key(sessionId, generation);
            active.put(key, new ActiveMedia(plex, plexSession, playlist, references, dimensions, item.durationMs(),
                    selection.options, selection.audioId, selection.subtitleId));
            return () -> { active.remove(key); plex.stop(plexSession); };
        } catch (IOException failure) { try { plex.stop(plexSession); } catch (IOException ignored) {} throw failure; }
        catch (RuntimeException failure) { try { plex.stop(plexSession); } catch (IOException ignored) {} throw failure; }
    }

    private void sendManifest(EntityPlayerMP player, VideoSessionCoordinator.Snapshot state) {
        ActiveMedia media = active.get(key(state.id(), state.generation())); if (media == null) return;
        sendManifest(player, state.id(), state.generation(), media, media.segmentAt(state.positionMs()), state.item() == null ? 0 : state.item().durationMs());
    }
    private void sendManifest(EntityPlayerMP player, UUID session, long generation, ActiveMedia media, int first, long duration) {
        List<VideoPackets.SegmentDescriptor> descriptors = media.descriptors(first, ProtocolLimits.MAX_VIDEO_SEGMENTS_PER_MANIFEST);
        if (descriptors.isEmpty()) return;
        send(player, LegacyPacketTypes.VIDEO_MANIFEST, new VideoPackets.SegmentManifest(session, generation,
                media.dimensions.width(), media.dimensions.height(), "mpegts", "h264", "aac", duration,
                first + descriptors.size() < media.segmentCount(), descriptors));
    }
    private void publish(EntityPlayerMP requester, LegacyWorldScreens.Television television, VideoSessionCoordinator.Snapshot state,
                         PresentationMode mode, String message, boolean manifest) {
        for (EntityPlayerMP recipient : recipients(television, requester)) { sendState(recipient, television, state, mode, message); sendQueue(recipient, state); if (manifest) sendManifest(recipient, state); }
    }
    private void publishSession(VideoSessionCoordinator.Snapshot state, String message, boolean manifest, EntityPlayerMP requester) {
        for (LegacyWorldScreens.Television television : televisionsForSession(state.name())) for (EntityPlayerMP recipient : recipients(television, requester)) {
            sendState(recipient, television, state, television.presentationMode(), message); sendQueue(recipient, state); if (manifest) sendManifest(recipient, state);
        }
    }
    private void publishQueue(VideoSessionCoordinator.Snapshot state, EntityPlayerMP requester) {
        Set<EntityPlayerMP> values = new LinkedHashSet<EntityPlayerMP>(); if (requester != null) values.add(requester);
        for (LegacyWorldScreens.Television television : televisionsForSession(state.name())) values.addAll(recipients(television, null));
        for (EntityPlayerMP player : values) sendQueue(player, state);
    }
    private void sendQueue(EntityPlayerMP player, VideoSessionCoordinator.Snapshot state) {
        List<QueuedVideo> queue = queues.get(state.id()); if (queue == null) queue = Collections.emptyList();
        send(player, LegacyPacketTypes.VIDEO_SESSION_QUEUE, new VideoPackets.SessionQueue(state.id(), state.generation(), queue));
    }
    private Set<EntityPlayerMP> recipients(LegacyWorldScreens.Television television, EntityPlayerMP requester) {
        Set<EntityPlayerMP> values = new LinkedHashSet<EntityPlayerMP>(); if (requester != null) values.add(requester);
        for (Map.Entry<UUID, Map<UUID, Long>> entry : visibleTelevisions.entrySet()) if (entry.getValue().containsKey(television.id())) {
            EntityPlayerMP player = player(entry.getKey()); if (player != null) values.add(player);
        }
        return values;
    }
    private void sendState(EntityPlayerMP player, LegacyWorldScreens.Television television, VideoSessionCoordinator.Snapshot state,
                           PresentationMode mode, String message) {
        VideoPackets.SessionStatus status = state.item() == null ? VideoPackets.SessionStatus.IDLE : state.paused()
                ? VideoPackets.SessionStatus.PAUSED : state.transcoding() ? VideoPackets.SessionStatus.PLAYING : VideoPackets.SessionStatus.IDLE;
        ActiveMedia media = active.get(key(state.id(), state.generation())); List<VideoStreamOption> streams = media == null ? Collections.<VideoStreamOption>emptyList() : media.options;
        send(player, LegacyPacketTypes.VIDEO_SESSION_STATE, new VideoPackets.SessionState(television.id(), television.controllerPos(),
                state.id(), state.generation(), status, state.item(), state.positionMs(), state.item() == null ? 0 : state.item().durationMs(),
                state.paused(), mode, television.width(), television.height(), television.mask(), television.facing(), television.plane(),
                television.minimumU(), television.minimumV(), streams, media == null ? -1 : media.audioId,
                media == null ? -1 : media.subtitleId, System.currentTimeMillis(), canControl(player, television), message));
    }
    private void sendIdle(EntityPlayerMP player, LegacyWorldScreens.Television television, String message) {
        send(player, LegacyPacketTypes.VIDEO_SESSION_STATE, new VideoPackets.SessionState(television.id(), television.controllerPos(),
                new UUID(0, 0), 0, VideoPackets.SessionStatus.IDLE, null, 0, 0, false, television.presentationMode(),
                television.width(), television.height(), television.mask(), television.facing(), television.plane(), television.minimumU(),
                television.minimumV(), Collections.<VideoStreamOption>emptyList(), -1, -1, System.currentTimeMillis(), canControl(player, television), message));
    }

    private boolean canControl(EntityPlayerMP player, LegacyWorldScreens.Television television) {
        return television.owner().equals(player.getUniqueID()) || permission(player) >= CinemarrSettings.operatorPermissionLevel();
    }
    private PlexVideoService.ResolvedLibrary library(String id, EntityPlayerMP player) {
        for (PlexVideoService.ResolvedLibrary value : libraries) if (value.rule().id().equals(id)) {
            if (permission(player) < value.rule().permissionLevel()) { error(player, "Library permission denied"); return null; } return value;
        }
        error(player, "Unknown video library"); return null;
    }
    private PlexVideoService.ResolvedLibrary library(String id) {
        if (id == null) return null; for (PlexVideoService.ResolvedLibrary value : libraries) if (value.rule().id().equals(id)) return value; return null;
    }
    private static int permission(EntityPlayerMP player) {
        for (int level = 4; level >= 0; level--) if (player.canCommandSenderUseCommand(level, "cinemarr")) return level; return 0;
    }
    private void error(EntityPlayerMP player, String message) { send(player, LegacyPacketTypes.ERROR,
            new LegacyPacketTypes.ErrorMessage(message)); }
    private void failure(EntityPlayerMP player, Throwable failure) {
        Throwable value = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (value instanceof WrappedFailure && value.getCause() != null) value = value.getCause();
        String message = SecretRedactor.message(value, CinemarrSettings.plexToken(), CinemarrSettings.plexUrl());
        Cinemarr.LOGGER.warn("Cinemarr video request failed: {}", message); error(player, message);
    }
    private List<EntityPlayerMP> players() {
        @SuppressWarnings("unchecked") List<EntityPlayerMP> values = new ArrayList<EntityPlayerMP>((List<EntityPlayerMP>) server.getConfigurationManager().playerEntityList);
        return values;
    }
    private EntityPlayerMP player(UUID id) { for (EntityPlayerMP value : players()) if (value.getUniqueID().equals(id)) return value; return null; }
    private static <T> void send(EntityPlayerMP player, LegacyPacketTypes.Type<T> type, T message) { LegacyNetwork.sendToPlayer(player, type, message); }

    private static String key(UUID session, long generation) { return session + ":" + generation; }
    static List<SegmentReference> parsePlaylist(PlexVideoService.MediaPlaylist playlist) {
        List<SegmentReference> values = new ArrayList<SegmentReference>();
        for (HlsPlaylist.MediaSegment value : playlist.segments()) values.add(new SegmentReference(value));
        return values;
    }
    static List<SegmentReference> parsePlaylist(String playlist, long basePts) {
        List<SegmentReference> values = new ArrayList<SegmentReference>();
        for (HlsPlaylist.MediaSegment value : HlsPlaylist.mediaSegments(playlist, basePts)) values.add(new SegmentReference(value));
        if (values.isEmpty()) throw new IllegalArgumentException("Plex media playlist has no segments at the requested offset");
        return values;
    }
    private static StreamSelection selection(List<VideoStreamOption> options, int requestedAudio, int requestedSubtitle,
                                             boolean defaults) throws IOException {
        int audio = requestedAudio, subtitle = requestedSubtitle;
        if (audio < 0) for (VideoStreamOption option : options) if (option.kind() == VideoStreamOption.Kind.AUDIO && option.selected()) { audio = option.id(); break; }
        if (defaults && subtitle < 0) for (VideoStreamOption option : options) if (option.kind() == VideoStreamOption.Kind.SUBTITLE && option.selected()) { subtitle = option.id(); break; }
        if (audio >= 0 && !contains(options, VideoStreamOption.Kind.AUDIO, audio)) throw new IOException("Requested Plex audio stream is unavailable");
        if (subtitle >= 0 && !contains(options, VideoStreamOption.Kind.SUBTITLE, subtitle)) throw new IOException("Requested Plex subtitle stream is unavailable");
        return new StreamSelection(options, audio, subtitle);
    }
    private static boolean contains(List<VideoStreamOption> values, VideoStreamOption.Kind kind, int id) {
        for (VideoStreamOption value : values) if (value.kind() == kind && value.id() == id) return true; return false;
    }

    @Override public void close() {
        TelevisionLifecycle.listener(null);
        long now = System.currentTimeMillis(); for (String name : sessions.sessionNames()) persist(sessions.snapshotIfPresent(name, now));
        try { sessions.close(); } catch (IOException failure) { Cinemarr.LOGGER.warn("Unable to close Plex video sessions: {}", SecretRedactor.message(failure, CinemarrSettings.plexToken(), CinemarrSettings.plexUrl())); }
        workers.shutdownNow(); active.clear(); startingOptions.remove(); playbackOptions.clear(); playbackLibraries.clear();
        queues.clear(); clientHealth.clear(); transferGrants.clear(); restartingSessions.clear(); advancingSessions.clear();
        viewingSessions.clear(); visibleTelevisions.clear(); mainThreadActions.clear(); LegacyNetwork.setServerListener(null);
    }

    static final class SegmentReference { final HlsPlaylist.MediaSegment source; final String uri; final long pts, duration; SegmentReference(HlsPlaylist.MediaSegment source) { this.source = source; this.uri = source.uri(); this.pts = source.presentationTimeMs(); this.duration = source.durationMs(); } }
    private static final class SegmentData { final SegmentReference reference; final byte[] bytes; final String sha; SegmentData(SegmentReference reference, byte[] bytes) { this.reference = reference; this.bytes = bytes; sha = Hashing.sha256(bytes); } }
    private static final class ActiveMedia {
        final PlexVideoService plex; final PlexVideoService.VideoSession session; final PlexVideoService.MediaPlaylist playlist; final List<SegmentReference> segments;
        final RenditionPolicy.Dimensions dimensions; final long durationMs; final List<VideoStreamOption> options; final int audioId, subtitleId;
        final Map<Integer, SegmentData> cache = new LinkedHashMap<Integer, SegmentData>(16, 0.75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, SegmentData> eldest) { return size() > 16; }
        };
        ActiveMedia(PlexVideoService plex, PlexVideoService.VideoSession session, PlexVideoService.MediaPlaylist playlist, List<SegmentReference> segments,
                    RenditionPolicy.Dimensions dimensions, long durationMs, List<VideoStreamOption> options, int audioId, int subtitleId) {
            this.plex = plex; this.session = session; this.playlist = playlist; this.segments = segments; this.dimensions = dimensions;
            this.durationMs = durationMs; this.options = Collections.unmodifiableList(new ArrayList<VideoStreamOption>(options));
            this.audioId = audioId; this.subtitleId = subtitleId;
        }
        synchronized int segmentCount() { return segments.size(); }
        synchronized int cachedSegments() { return cache.size(); }
        synchronized long presentationTime(int index) { return segments.get(index).pts; }
        synchronized int segmentAt(long position) { int first = 0; for (int index = 0; index < segments.size(); index++) { if (segments.get(index).pts > position) break; first = index; } return first; }
        synchronized List<VideoPackets.SegmentDescriptor> descriptors(int first, int maximum) {
            if (first < 0 || first >= segments.size()) return Collections.emptyList(); int end = Math.min(segments.size(), first + maximum);
            List<VideoPackets.SegmentDescriptor> values = new ArrayList<VideoPackets.SegmentDescriptor>();
            for (int index = first; index < end; index++) { SegmentReference reference = segments.get(index); values.add(new VideoPackets.SegmentDescriptor(index, reference.pts, reference.duration, true, 0, "")); }
            return values;
        }
        synchronized SegmentData segment(int index) throws IOException {
            SegmentData value = cache.get(index); if (value == null) { SegmentReference reference = segments.get(index);
                value = new SegmentData(reference, plex.fetch(session, playlist, reference.source)); cache.put(index, value); } return value;
        }
    }
    private static final class TransferGrant {
        final UUID session; final long generation, requestId; final int segment;
        TransferGrant(VideoPackets.SegmentRequest value) { session = value.sessionId(); generation = value.generation(); requestId = value.requestId(); segment = value.segmentIndex(); }
        boolean matches(VideoPackets.SegmentAcknowledgement value) { return session.equals(value.sessionId()) && generation == value.generation()
                && requestId == value.requestId() && segment == value.segmentIndex() && value.receivedThroughChunk() >= 0; }
    }
    private static final class StreamSelection {
        final List<VideoStreamOption> options; final int audioId, subtitleId;
        StreamSelection(List<VideoStreamOption> options, int audioId, int subtitleId) { this.options = Collections.unmodifiableList(new ArrayList<VideoStreamOption>(options)); this.audioId = audioId; this.subtitleId = subtitleId; }
    }
    private static final class StartOptions { final RenditionPolicy.Dimensions rendition; final StreamSelection streams; StartOptions(RenditionPolicy.Dimensions rendition, StreamSelection streams) { this.rendition = rendition; this.streams = streams; } }
    private static final class HealthRecord { final VideoPackets.ClientHealth value; final long receivedAt; HealthRecord(VideoPackets.ClientHealth value, long receivedAt) { this.value = value; this.receivedAt = receivedAt; } }
    private static final class WrappedFailure extends RuntimeException { WrappedFailure(Throwable cause) { super(cause); } }
}
