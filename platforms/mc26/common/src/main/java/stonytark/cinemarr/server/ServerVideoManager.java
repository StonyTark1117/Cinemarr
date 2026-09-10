package stonytark.cinemarr.server;

import stonytark.cinemarr.core.server.ActiveVideoMedia;
import stonytark.cinemarr.core.server.ActiveVideoMedia.SegmentReference;
import stonytark.cinemarr.core.server.VideoHealthRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import stonytark.cinemarr.core.server.HlsPlaylist;
import stonytark.cinemarr.core.server.VideoWorkQueues;
import stonytark.cinemarr.core.server.FairEgressScheduler;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.core.server.RedstoneControlPolicy;
import stonytark.cinemarr.core.server.SecretRedactor;
import stonytark.cinemarr.core.server.SlidingWindowRateLimiter;
import stonytark.cinemarr.core.server.VideoSessionCoordinator;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.server.TransferGrantRegistry;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.TvDisplaySettings;
import stonytark.cinemarr.core.video.ResolutionChoice;
import stonytark.cinemarr.core.server.TelevisionStreamPool;
import stonytark.cinemarr.core.video.RenditionPolicy;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;
import stonytark.cinemarr.screen.CinemarrWorldScreens;
import stonytark.cinemarr.registry.CinemarrBlocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Loader-neutral server boundary for video browse/control and pull-based HLS segment relay. */
public final class ServerVideoManager implements AutoCloseable {
    private static final int PAGE_SIZE = 20;
    private final MinecraftServer server;
    private final java.util.function.Predicate<UUID> handshakeComplete;
    private final CinemarrVideoSavedData saved;
    private final PlexVideoService plex;
    private final List<PlexVideoService.ResolvedLibrary> libraries;
    private final VideoWorkQueues workers = new VideoWorkQueues("Cinemarr video worker ");
    private final FairEgressScheduler<UUID, ServerPlayer, CinemarrMessage> egress =
            new FairEgressScheduler<>(64, 2L * 1024L * 1024L, 256, 8L * 1024L * 1024L, 1024, 16L * 1024L * 1024L);
    private final SlidingWindowRateLimiter browseLimiter = new SlidingWindowRateLimiter();
    private final SlidingWindowRateLimiter segmentLimiter = new SlidingWindowRateLimiter();
    private final Map<String, ActiveVideoMedia> active = new ConcurrentHashMap<>();
    private final ThreadLocal<StartOptions> startingOptions = new ThreadLocal<>();
    private final Map<UUID, StartOptions> playbackOptions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playbackLibraries = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playbackMetadataGenerations = new ConcurrentHashMap<>();
    private final TransferGrantRegistry transferGrants = new TransferGrantRegistry(30_000L, CinemarrSettings.maximumConcurrentStreams());
    private final Set<UUID> restartingSessions=ConcurrentHashMap.newKeySet();
    private final Set<UUID> advancingSessions=ConcurrentHashMap.newKeySet();
    private final Map<UUID,List<QueuedVideo>> queues=new ConcurrentHashMap<>();
    private final VideoHealthRegistry clientHealth = new VideoHealthRegistry(30_000L, CinemarrSettings.maximumConcurrentStreams());
    private final Map<UUID,Long> browseGenerations=new ConcurrentHashMap<>();
    private final Map<UUID, Set<TrackedChunk>> trackedChunks = new HashMap<>();
    private final Map<UUID, Set<String>> viewingSessions = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> visibleTelevisions = new HashMap<>();
    private final Map<UUID, Boolean> receiverPower = new HashMap<>();
    private final VideoSessionCoordinator sessions;
    private final TelevisionStreamPool tvStreams;
    private long publishedStreamChanges = -1;
    private long lastCheckpointMs;
    private volatile boolean closed;

    public ServerVideoManager(MinecraftServer server, PlexVideoService plex, List<PlexVideoService.ResolvedLibrary> libraries,
                              CinemarrVideoSavedData saved, java.util.function.Predicate<UUID> handshakeComplete) {
        this.server = server; this.plex = plex; this.libraries = Collections.unmodifiableList(new ArrayList<>(libraries));
        this.saved=saved;
        this.handshakeComplete=java.util.Objects.requireNonNull(handshakeComplete,"handshakeComplete");
        this.sessions = new VideoSessionCoordinator(1_000_000,
                CinemarrSettings.inactiveSessionGraceSeconds() * 1000L, (id,generation,item,offset)->()->{}, true);
        tvStreams=new TelevisionStreamPool(CinemarrSettings.maximumConcurrentStreams(),CinemarrSettings.inactiveSessionGraceSeconds()*1000L,this::startTelevisionMedia,operation->workers.supply(operation));
        restoreSessions(); initializeReceiverPower(); TelevisionLifecycle.listener(this::televisionRemoved);
        // Plex can become ready after players have already completed their hello.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) synchronizeTrackingRadius(player);
    }

    private void restoreSessions(){
        long now=System.currentTimeMillis();Set<String> tunedNames=new LinkedHashSet<>();
        for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(!tv.sessionName().isBlank()){sessions.tune(tv.id(),tv.sessionName());TelevisionLifecycle.attachment(tv.id(),true);tunedNames.add(tv.sessionName());}
        for(CinemarrVideoSavedData.Record record:saved.records()){
            VideoSessionCoordinator.Snapshot current=sessions.snapshotIfPresent(record.sessionName(),now);PlexVideoService.ResolvedLibrary library=library(record.libraryId());
            if(current==null||library==null||!library.rule().allows(record.item(),4))continue;
            playbackLibraries.put(current.id(),record.libraryId());playbackOptions.put(current.id(),new StartOptions(renditionForSession(record.sessionName()),new StreamSelection(Collections.emptyList(),record.audioStreamId(),record.subtitleStreamId())));
            queues.put(current.id(),new ArrayList<>(record.queue()));
            VideoSessionCoordinator.Snapshot restored = sessions.restore(record.sessionName(), record.item(), record.positionMs(), record.paused(), now);
            playbackMetadataGenerations.put(restored.id(), restored.playbackGeneration());
        }
    }

    public void chunkSent(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        trackedChunks.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(new TrackedChunk(level, chunk.pack()));
        refreshTracking(player);
    }

    public void chunkUnwatched(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        Set<TrackedChunk> values = trackedChunks.get(player.getUUID());
        if (values != null) values.remove(new TrackedChunk(level, chunk.pack()));
        refreshTracking(player);
    }

    /** Fabric fallback for loaders without per-player chunk-watch callbacks. */
    public void synchronizeTrackingRadius(ServerPlayer player) {
        ServerLevel level = player.level();
        ChunkPos center = player.chunkPosition();
        int radius = Math.max(2, server.getPlayerList().getViewDistance());
        Set<TrackedChunk> visible = new HashSet<>();
        for (CinemarrWorldScreens.Television television : CinemarrWorldScreens.get(level).televisions()) {
            for (Long packed : television.pixels()) {
                BlockPos pixel = BlockPos.of(packed);
                int x = pixel.getX() >> 4, z = pixel.getZ() >> 4;
                if (Math.abs(x - center.x()) <= radius && Math.abs(z - center.z()) <= radius) {
                    visible.add(new TrackedChunk(level, ChunkPos.pack(x, z)));
                }
            }
        }
        trackedChunks.put(player.getUUID(), visible);
        refreshTracking(player);
    }

    public void playerLeft(ServerPlayer player) {
        UUID id = player.getUUID(); long now = System.currentTimeMillis();
        for (String name : viewingSessions.getOrDefault(id, Collections.emptySet())) {
            VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(name, now);
            if (state != null) sessions.viewerLeft(name, id, now);
        }
        trackedChunks.remove(id); viewingSessions.remove(id); visibleTelevisions.remove(id);clientHealth.remove(id);transferGrants.remove(id);browseGenerations.remove(id);egress.remove(id);
        browseLimiter.remove(id); segmentLimiter.remove(id);
    }

    private void refreshAllTracking() {
        for (UUID id : new ArrayList<>(trackedChunks.keySet())) {
            // A television may be built inside chunks the player was already
            // watching, so no new chunk-watch event will populate the cached
            // set. Re-scan the radius before broadcasting the new state.
            ServerPlayer player = server.getPlayerList().getPlayer(id); if (player != null) synchronizeTrackingRadius(player);
        }
    }

    private void refreshTracking(ServerPlayer player) {
        // Do not cache visibility or publish media before JOIN reset/hello has completed.
        // Otherwise the early snapshot can be erased and suppress the first accepted one.
        if (closed || !handshakeComplete.test(player.getUUID())) return;
        UUID playerId = player.getUUID(); long now = System.currentTimeMillis();
        Map<UUID, CinemarrWorldScreens.Television> televisions = new LinkedHashMap<>();
        for (TrackedChunk tracked : trackedChunks.getOrDefault(playerId, Collections.emptySet())) {
            for (CinemarrWorldScreens.Television tv : CinemarrWorldScreens.get(tracked.level).televisionsForChunk(ChunkPos.unpack(tracked.chunk))) {
                televisions.put(tv.id(), tv);
            }
        }
        Set<String> nextSessions = new LinkedHashSet<>();
        for (CinemarrWorldScreens.Television tv : televisions.values()) if (!tv.sessionName().isBlank()
                && sessions.snapshotIfPresent(tv.sessionName(), now) != null) nextSessions.add(tv.sessionName());
        Set<String> previousSessions = viewingSessions.getOrDefault(playerId, Collections.emptySet());
        for (String name : previousSessions) if (!nextSessions.contains(name)) sessions.viewerLeft(name, playerId, now);
        for (String name : nextSessions) if (!previousSessions.contains(name)) sessions.viewerEntered(name, playerId);
        viewingSessions.put(playerId, nextSessions);
        Map<UUID,Long> previousTvs = visibleTelevisions.getOrDefault(playerId, Collections.emptyMap());
        Set<VideoStreamIdentity> trackedStreams = new HashSet<>();
        for (CinemarrWorldScreens.Television television : televisions.values()) {
            VideoSessionCoordinator.Snapshot state = sessions.snapshotIfPresent(television.sessionName(), now);
            VideoSessionCoordinator.Snapshot stream=tvStreams.snapshot(television.id(),now);
            if (stream != null && state != null && previousTvs.containsKey(television.id())) {
                VideoStreamIdentity identity=tvStreams.identity(stream.id(), stream.generation());
                if (identity != null && identity.timelineId().equals(state.id()) && identity.timelineGeneration()==state.generation())
                    trackedStreams.add(identity);
            }
        }
        clientHealth.retain(playerId, trackedStreams);
        // Screen departure abandons the client's assembler and its ACK.
        for (TransferGrantRegistry.Window window : transferGrants.releaseExcept(playerId, trackedStreams))
            removeStreamEgress(window.client(), window.identity());
        for(String name:nextSessions){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null&&state.item()!=null&&!state.transcoding()&&!state.paused())restartIfNeeded(name,state);}

        for(Map.Entry<UUID,Long> previous:previousTvs.entrySet())if(!televisions.containsKey(previous.getKey()))CinemarrNetwork.sendToPlayer(player,new VideoPayloads.TelevisionRemoved(new VideoPackets.TelevisionRemoved(previous.getValue())));
        Map<UUID,Long> nextTvs=new LinkedHashMap<>();
        for (CinemarrWorldScreens.Television tv : televisions.values()){nextTvs.put(tv.id(),tv.controllerPos());if(!previousTvs.containsKey(tv.id()))sendCurrent(player,tv,now);}
        visibleTelevisions.put(playerId,nextTvs);
    }

    private void sendCurrent(ServerPlayer player, CinemarrWorldScreens.Television tv, long now) {
        VideoSessionCoordinator.Snapshot state = tv.sessionName().isBlank() ? null : sessions.snapshotIfPresent(tv.sessionName(), now);
        if (state == null) sendIdle(player, tv, "TV is idle");
        else { sendState(player, tv, state, tv.presentationMode(), state.playbackMessage());sendQueue(player,state);sendManifest(player, tv, state); }
    }

    public void sendLibraries(ServerPlayer player) {
        List<VideoPackets.LibrarySummary> visible = new ArrayList<>();
        int permission = permission(player);
        for (PlexVideoService.ResolvedLibrary library : libraries) if (permission >= library.rule().permissionLevel()) {
            visible.add(new VideoPackets.LibrarySummary(library.rule().id(), library.rule().displayName(),
                    library.rule().allowMovies(), library.rule().allowShows(), library.rule().permissionLevel()));
        }
        CinemarrNetwork.sendToPlayer(player, new VideoPayloads.LibraryList(new VideoPackets.LibraryList(visible)));
    }

    public void browse(ServerPlayer player, VideoPackets.BrowseRequest request) {
        if (closed) return;
        if (!browseLimiter.allow(player.getUUID(), 8, System.currentTimeMillis())) { error(player, "Video browse rate limit exceeded"); return; }
        UUID playerId=player.getUUID();long browseGeneration=browseGenerations.merge(playerId,1L,(previous,one)->previous+1L);
        PlexVideoService.ResolvedLibrary library = library(request.libraryId(), player);
        if (library == null) return;
        int playerPermission=permission(player);
        workers.browse(() -> {
            try { return plex.browse(library, request.parentKey(), request.query(), Math.max(0, request.page()), PAGE_SIZE, playerPermission); }
            catch (IOException failure) { throw new WrappedFailure(failure); }
        }).whenComplete((page, failure) -> scheduleMain(() -> {
            if(closed||!Long.valueOf(browseGeneration).equals(browseGenerations.get(playerId)))return;
            if (failure != null) { failure(player, failure); return; }
            CinemarrNetwork.sendToPlayer(player, new VideoPayloads.BrowseResults(new VideoPackets.BrowseResults(
                    request.libraryId(), request.parentKey(), request.query(), Math.max(0, request.page()), page.hasMore(), page.items())));
        }));
    }

    public void command(ServerPlayer player, VideoPackets.SessionCommand command) {
        BlockPos controller = BlockPos.of(command.controllerPos());
        CinemarrWorldScreens.Television television = CinemarrWorldScreens.get(player.level()).television(controller);
        if (television == null) { error(player, "The TV Controller has no active screen"); return; }
        if (!television.owner().equals(player.getUUID()) && permission(player) < CinemarrSettings.operatorPermissionLevel()) {
            error(player, "Only the TV owner or an operator can control this TV"); return;
        }
        try {
            if (command.action() == VideoPackets.SessionAction.SET_DISPLAY) {
                if(command.displaySettings()==null)throw new IllegalArgumentException("Missing display settings");
                CinemarrWorldScreens.get(player.level()).updateDisplay(controller,command.displaySettings());
                sendCurrent(player,television,System.currentTimeMillis());
                return;
            }
            String requestedSession=command.sessionName().isBlank()?television.sessionName():command.sessionName();
            VideoSessionCoordinator.Snapshot tuned = sessions.tune(television.id(), requestedSession);
            TelevisionLifecycle.attachment(television.id(),true);
            if (tuned.item() == null) tuned = restoreDormant(tuned);
            CinemarrWorldScreens screenData=CinemarrWorldScreens.get(player.level());
            screenData.updateSession(controller,tuned.name());
            refreshAllTracking();
            PresentationMode presentation=command.action()==VideoPackets.SessionAction.SET_PRESENTATION?command.presentationMode():television.presentationMode();
            if (command.action() == VideoPackets.SessionAction.TUNE) { publish(player, television, tuned, presentation, "Tuned", true); return; }
            if (command.expectedGeneration() != tuned.generation()) { error(player, "TV state changed; refresh before controlling it"); return; }
            switch (command.action()) {
                case PAUSE: sessions.pause(tuned.name(), System.currentTimeMillis());VideoSessionCoordinator.Snapshot paused=sessions.snapshot(tuned.name(),System.currentTimeMillis());publishSession(paused,"Paused",false,player);persist(paused);break;
                case RESUME: sessions.resume(tuned.name(), System.currentTimeMillis());VideoSessionCoordinator.Snapshot resumed=sessions.snapshot(tuned.name(),System.currentTimeMillis());persist(resumed);publishSession(resumed,"Resuming",false,player);restartIfNeeded(tuned.name(),resumed);break;
                case SEEK: asyncSeek(player, television, tuned, command, presentation); break;
                case PLAY: asyncPlay(player, television, tuned, command, presentation); break;
                case STOP: VideoSessionCoordinator.Snapshot stopped=sessions.stop(tuned.name(),System.currentTimeMillis());playbackLibraries.remove(tuned.id());playbackOptions.remove(tuned.id());playbackMetadataGenerations.remove(tuned.id());queues.remove(tuned.id());saved.remove(tuned.name());publishSession(stopped,"Stopped",false,player);break;
                case SET_PRESENTATION: screenData.updatePresentation(controller,command.presentationMode()); publish(player, television, tuned, presentation, "Presentation updated", false); break;
                case SET_STREAMS: asyncPlay(player, television, tuned, command, presentation); break;
                case QUEUE: asyncQueue(player,tuned,command);break;
                case REMOVE_QUEUE: removeQueue(player,tuned,(int)command.seekPositionMs());break;
                case CLEAR_QUEUE: queues.remove(tuned.id());persist(tuned);publishQueue(tuned,player);break;
                case SKIP: advance(tuned,player);break;
                case CONTINUE_EPISODE: continueEpisode(player,tuned);break;
                default: error(player, "Unsupported TV action");
            }
        } catch (Exception failure) { failure(player, failure); }
    }

    private void asyncPlay(ServerPlayer player, CinemarrWorldScreens.Television television,
                           VideoSessionCoordinator.Snapshot tuned, VideoPackets.SessionCommand command,
                           PresentationMode presentation) {
        String libraryId = command.action() == VideoPackets.SessionAction.SET_STREAMS
                ? playbackLibraries.get(tuned.id()) : command.libraryId();
        PlexVideoService.ResolvedLibrary library = library(libraryId, player); if (library == null) return;
        int playerPermission=permission(player);
        workers.supply(() -> {
            try {
                String itemKey=command.itemKey().isBlank()&&tuned.item()!=null?tuned.item().key():command.itemKey();
                PlexVideoService.PlaybackMetadata metadata = plex.metadataDetails(itemKey);VideoMediaItem item=metadata.item();
                if (!library.rule().allows(item, playerPermission)) throw new IOException("Video item is not allowed by this library policy");
                StartOptions options=new StartOptions(renditionFor(television,metadata),selection(metadata.streams(),command.audioStreamId(),command.subtitleStreamId(),command.action()!=VideoPackets.SessionAction.SET_STREAMS));
                startingOptions.set(options);VideoSessionCoordinator.Snapshot state;
                try{state=command.action()==VideoPackets.SessionAction.SET_STREAMS?sessions.reconfigure(tuned.name(),System.currentTimeMillis(),tuned.generation()):sessions.play(tuned.name(),item,command.seekPositionMs(),System.currentTimeMillis(),tuned.generation());}
                finally{startingOptions.remove();}
                return new PreparedPlayback(state, options, library.rule().id());
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }).whenComplete((prepared, failure) -> scheduleMain(() -> {VideoSessionCoordinator.Snapshot state=prepared==null?null:prepared.state;
            if (failure != null) { failure(player, failure); return; }
            state=recordPlayback(prepared);if(state==null)return;
            persist(state);publish(player, television, state, presentation, state.playbackMessage(), true);
        }));
    }

    private void asyncSeek(ServerPlayer player, CinemarrWorldScreens.Television television,
                           VideoSessionCoordinator.Snapshot tuned, VideoPackets.SessionCommand command,
                           PresentationMode presentation) {
        RenditionPolicy.Dimensions rendition=renditionFor(television);
        workers.supply(() -> { try {StartOptions previous=playbackOptions.get(tuned.id());StartOptions options=new StartOptions(rendition,previous==null?new StreamSelection(Collections.emptyList(),-1,-1):previous.streams);startingOptions.set(options);try{VideoSessionCoordinator.Snapshot state=sessions.seek(tuned.name(),command.seekPositionMs(),System.currentTimeMillis(),tuned.generation());return new PreparedPlayback(state, options, null);}finally{startingOptions.remove();} }
            catch (IOException failure) { throw new WrappedFailure(failure); } }).whenComplete((prepared, failure) -> scheduleMain(() -> {VideoSessionCoordinator.Snapshot state=prepared==null?null:prepared.state;
            if (failure != null) { failure(player, failure); return; }
            state=recordPlayback(prepared);if(state==null)return;persist(state);publish(player,television,state,presentation,state.playbackMessage(),true);
        }));
    }

    private void asyncQueue(ServerPlayer player,VideoSessionCoordinator.Snapshot tuned,VideoPackets.SessionCommand command){
        PlexVideoService.ResolvedLibrary library=library(command.libraryId(),player);if(library==null)return;int playerPermission=permission(player);
        workers.supply(()->{try{VideoMediaItem item=plex.metadata(command.itemKey());if(!library.rule().allows(item,playerPermission))throw new IOException("Video item is not allowed by this library policy");return new QueuedVideo(library.rule().id(),item);}catch(IOException failure){throw new WrappedFailure(failure);}}).whenComplete((entry,failure)->scheduleMain(()->{
            if(failure!=null){failure(player,failure);return;}VideoSessionCoordinator.Snapshot current=sessions.snapshotIfPresent(tuned.id(),tuned.generation(),System.currentTimeMillis());if(current==null){error(player,"TV state changed while queueing");return;}
            List<QueuedVideo> queue=queues.computeIfAbsent(tuned.id(),ignored->new ArrayList<>());if(queue.size()>=CinemarrSettings.queueLimit()){error(player,"Video queue is full");return;}queue.add(entry);if(current.item()==null)advance(current,player);else{persist(current);publishQueue(current,player);}
        }));
    }

    private void removeQueue(ServerPlayer player,VideoSessionCoordinator.Snapshot state,int index){List<QueuedVideo> queue=queues.get(state.id());if(queue==null||index<0||index>=queue.size()){error(player,"Invalid video queue entry");return;}queue.remove(index);if(queue.isEmpty())queues.remove(state.id());persist(state);publishQueue(state,player);}

    private void advance(VideoSessionCoordinator.Snapshot expected,ServerPlayer requester){
        if(!advancingSessions.add(expected.id()))return;List<QueuedVideo> queue=queues.get(expected.id());QueuedVideo next=queue==null||queue.isEmpty()?null:queue.get(0);
        if(next==null){try{VideoSessionCoordinator.Snapshot stopped=sessions.stop(expected.name(),System.currentTimeMillis());playbackLibraries.remove(expected.id());playbackOptions.remove(expected.id());playbackMetadataGenerations.remove(expected.id());queues.remove(expected.id());saved.remove(expected.name());publishSession(stopped,"Queue finished",false,requester);}catch(IOException failure){if(requester!=null)failure(requester,failure);}finally{advancingSessions.remove(expected.id());}return;}
        RenditionPolicy.Dimensions rendition=renditionForSession(expected.name());
        workers.supply(()->{try{PlexVideoService.ResolvedLibrary library=library(next.libraryId());if(library==null)throw new IOException("Queued video library is no longer configured");PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(next.item().key());if(!library.rule().allows(metadata.item(),4))throw new IOException("Queued video item is no longer allowed");StreamSelection streams=selection(metadata.streams(),-1,-1,true);StartOptions options=new StartOptions(rendition,streams);startingOptions.set(options);VideoSessionCoordinator.Snapshot state;try{state=sessions.play(expected.name(),metadata.item(),0,System.currentTimeMillis(),expected.generation());}finally{startingOptions.remove();}return new PreparedPlayback(state, options, next.libraryId());}catch(IOException failure){throw new WrappedFailure(failure);}}).whenComplete((prepared, failure)->scheduleMain(()->{VideoSessionCoordinator.Snapshot state=prepared==null?null:prepared.state;advancingSessions.remove(expected.id());if(failure!=null){if(requester!=null)failure(requester,failure);else Cinemarr.LOGGER.warn("Unable to advance video queue: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));return;}state=recordPlayback(prepared);if(state==null)return;List<QueuedVideo> current=queues.get(expected.id());if(current!=null){current.remove(next);if(current.isEmpty())queues.remove(expected.id());}persist(state);publishSession(state,state.playbackMessage("Playing next queued video"),true,requester);}));
    }

    private void continueEpisode(ServerPlayer player,VideoSessionCoordinator.Snapshot expected){
        if(expected.item()==null||expected.item().kind()!=stonytark.cinemarr.core.library.MediaKind.EPISODE){error(player,"Continue is available only for episodes");return;}String libraryId=playbackLibraries.get(expected.id());PlexVideoService.ResolvedLibrary library=library(libraryId);if(library==null){error(player,"Playback library is no longer configured");return;}
        RenditionPolicy.Dimensions rendition=renditionForSession(expected.name());
        workers.supply(()->{try{VideoMediaItem next=plex.nextEpisode(expected.item().key());if(next==null)throw new IOException("This is the final episode");if(!library.rule().allows(next,4))throw new IOException("Next episode is not allowed by this library policy");PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(next.key());StreamSelection streams=selection(metadata.streams(),-1,-1,true);StartOptions options=new StartOptions(rendition,streams);startingOptions.set(options);VideoSessionCoordinator.Snapshot state;try{state=sessions.play(expected.name(),metadata.item(),0,System.currentTimeMillis(),expected.generation());}finally{startingOptions.remove();}return new PreparedPlayback(state, options, null);}catch(IOException failure){throw new WrappedFailure(failure);}}).whenComplete((prepared, failure)->scheduleMain(()->{VideoSessionCoordinator.Snapshot state=prepared==null?null:prepared.state;if(failure!=null){failure(player,failure);return;}state=recordPlayback(prepared);if(state==null)return;persist(state);publishSession(state,state.playbackMessage("Continuing with next episode"),true,player);}));
    }

    public void segments(ServerPlayer player, VideoPackets.SegmentRequest request) {
        if (request.sessionId() == null || request.generation() < 0 || request.segmentIndex() < 0 || request.requestId()<1 || request.chunkCount() < 1 || request.chunkCount() > 8 || request.firstChunk() < 0
                || request.firstChunk()>PlexVideoService.MAX_SEGMENT_BYTES/ProtocolLimits.MAX_VIDEO_CHUNK_BYTES
                || !segmentLimiter.allow(player.getUUID(), 40, System.currentTimeMillis())) { error(player, "Invalid or excessive segment request"); return; }
        // Requests already in flight can arrive after a pause, retune, or
        // viewer detach. The ownership check must still prevent any data
        // access, but reporting that expected retirement as a user-visible
        // controller error only creates a false failure on the owner UI.
        if (!isCurrentViewer(request.identity(), player.getUUID())) return;
        ActiveVideoMedia media=active.get(key(request.sessionId(),request.generation()));
        VideoSessionCoordinator.Snapshot timeline=tvStreams.snapshotIfPresent(request.sessionId(),request.generation(),System.currentTimeMillis());
        if (media==null || timeline==null || request.segmentIndex()<0 || request.segmentIndex()>=media.segmentCount()) { transportError(player, request.sessionId(), request.generation(),"Video segment is no longer available"); return; }
        if (media.presentationTime(request.segmentIndex())>timeline.positionMs()+ProtocolLimits.MAX_VIDEO_SEGMENT_LEAD_MS) { transportError(player, request.sessionId(), request.generation(),"Video segment request exceeds playback lead limit"); return; }
        long requestedAt=System.currentTimeMillis();
        TransferGrantRegistry.RequestDecision decision=transferGrants.request(player.getUUID(),request,requestedAt);
        if(decision==TransferGrantRegistry.RequestDecision.REJECT){transportError(player,request.sessionId(),request.generation(),"A video transfer window is already awaiting acknowledgement");return;}
        if(decision==TransferGrantRegistry.RequestDecision.REPLAY){
            ActiveVideoMedia.SegmentData cached=media.cachedSegment(request.segmentIndex());
            if(cached!=null)sendSegmentWindow(player,request,cached);
            return;
        }
        workers.supply(() -> { try { return media.segment(request.segmentIndex()); } catch(IOException failure){throw new WrappedFailure(failure);} })
                .whenComplete((segment,failure)->scheduleMain(()->{
                    if (!isCurrentViewer(request.identity(), player.getUUID())) { transferGrants.release(player.getUUID(), request); return; }
                    if(failure!=null){transferGrants.release(player.getUUID(),request);failure(player,failure);return;}
                    sendSegmentWindow(player,request,segment);
                }));
    }
    private void sendSegmentWindow(ServerPlayer player,VideoPackets.SegmentRequest request,ActiveVideoMedia.SegmentData segment) {
        if(!transferGrants.owns(player.getUUID(),request,System.currentTimeMillis())||!isCurrentViewer(request.identity(), player.getUUID()))return;
        int total=(segment.bytes.length+ProtocolLimits.MAX_VIDEO_CHUNK_BYTES-1)/ProtocolLimits.MAX_VIDEO_CHUNK_BYTES;
        if(request.firstChunk()>=total){transferGrants.release(player.getUUID(),request);transportError(player, request.sessionId(), request.generation(),"Invalid video chunk window");return;}
        int end=Math.min(total,request.firstChunk()+request.chunkCount());
        List<FairEgressScheduler.Item<CinemarrMessage>> batch=new ArrayList<>();
        for(int index=request.firstChunk();index<end;index++){int from=index*ProtocolLimits.MAX_VIDEO_CHUNK_BYTES,to=Math.min(segment.bytes.length,from+ProtocolLimits.MAX_VIDEO_CHUNK_BYTES);
            byte[] bytes=Arrays.copyOfRange(segment.bytes,from,to);CinemarrMessage message=new VideoPayloads.SegmentChunk(new VideoPackets.SegmentChunk(request.identity(),request.requestId(),request.segmentIndex(),index,total,segment.reference.pts,true,segment.sha,bytes));
            batch.add(new FairEgressScheduler.Item<>(message,bytes.length+256));}
        if(!egress.enqueueBatch(player.getUUID(),request.sessionId(),player,batch)){transferGrants.release(player.getUUID(),request);transportError(player, request.sessionId(), request.generation(),"Video transfer queue is full; retry");}
    }
    public void manifest(ServerPlayer player,VideoPackets.SegmentManifestRequest request){
        if (request.sessionId() == null || request.generation() < 0 || request.firstSegmentIndex() < 0) {
            error(player, "Invalid video manifest request"); return;
        }
        if (!isCurrentViewer(request.identity(), player.getUUID())) return;
        ActiveVideoMedia media=active.get(key(request.sessionId(),request.generation()));if(media==null){transportError(player, request.sessionId(), request.generation(),"Video manifest is no longer available");return;}
        VideoSessionCoordinator.Snapshot timeline=tvStreams.snapshotIfPresent(request.sessionId(),request.generation(),System.currentTimeMillis());
        if(timeline==null||request.firstSegmentIndex()>=media.segmentCount()||media.presentationTime(request.firstSegmentIndex())>timeline.positionMs()+ProtocolLimits.MAX_VIDEO_SEGMENT_LEAD_MS){transportError(player, request.sessionId(), request.generation(),"Video manifest request exceeds playback lead limit");return;}
        // Recovery abandons the old assembler after bounded client retries.
        // Retire its grant and queued bytes before publishing the new page.
        if (transferGrants.restartManifest(player.getUUID(), request.identity())) removeStreamEgress(player.getUUID(), request.identity());
        sendManifest(player, request.identity(),media,request.firstSegmentIndex(),media.durationMs);
    }

    public void acknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) {
        if (value.sessionId() == null || value.generation() < 0 || value.requestId() < 1
                || value.segmentIndex() < 0 || value.receivedThroughChunk() < 0
                || value.receivedThroughChunk() >= PlexVideoService.MAX_SEGMENT_BYTES / ProtocolLimits.MAX_VIDEO_CHUNK_BYTES
                || value.bufferedMs() < 0 || value.bufferedMs() > 30_000) {
            error(player, "Invalid video buffer acknowledgement"); return;
        }
        if (!isCurrentViewer(value.identity(), player.getUUID())) return;
        if (!transferGrants.acknowledge(player.getUUID(), value, System.currentTimeMillis()))
            transportError(player, value.sessionId(), value.generation(), "Invalid video buffer acknowledgement");
    }
    private void pruneRetiredTransfers() {
        for (TransferGrantRegistry.Window window : transferGrants.windows()) {
            if (!isCurrentViewer(window.identity(), window.client()) && transferGrants.restartManifest(window.client(), window.identity()))
                removeStreamEgress(window.client(), window.identity());
        }
    }
    private void removeWindowEgress(TransferGrantRegistry.Window window) {
        egress.removeMatching(window.client(), message -> message instanceof VideoPayloads.SegmentChunk chunk
                && window.matches(chunk.value()));
    }
    private void removeStreamEgress(UUID client, VideoStreamIdentity identity) {
        egress.removeMatching(client, message -> message instanceof VideoPayloads.SegmentChunk chunk
                && identity.equals(chunk.value().identity()));
    }
    private void sendCurrentSegment(ServerPlayer player, CinemarrMessage message) {
        if (message instanceof VideoPayloads.SegmentChunk chunk && isCurrentViewer(chunk.value().identity(), player.getUUID()))
            CinemarrNetwork.sendToPlayer(player, message);
    }
    private boolean isCurrentViewer(VideoStreamIdentity identity, UUID viewer) {
        return identity != null
                && sessions.snapshotIfPresent(identity.timelineId(), identity.timelineGeneration(), System.currentTimeMillis()) != null
                && tvStreams.isViewer(identity, viewer);
    }
    private void transportError(ServerPlayer player, UUID session, long generation, String message) {
        if (!tvStreams.isSupersededViewer(session, generation, player.getUUID())) error(player, message);
    }
    public void health(ServerPlayer player, VideoPackets.ClientHealth value) {
        VideoHealthRegistry.Result result = clientHealth.record(player.getUUID(), value,
                System.currentTimeMillis(), this::isCurrentViewer);
        if (result == VideoHealthRegistry.Result.INVALID) error(player, "Invalid video health report");
    }
    public String status(){return "Cinemarr video: "+TelevisionLifecycle.count()+" registered TV(s), "+tvStreams.activeStreamCount()+"/"+CinemarrSettings.maximumConcurrentStreams()+" active stream(s), "+TelevisionLifecycle.attachedSessionCount()+" attached session(s), "+dormantSessions()+" dormant session(s)";}
    private String transferOwnershipDiagnostics() {
        Set<UUID> connected = new HashSet<UUID>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) connected.add(player.getUUID());
        return "; orphanedTransferGrants=" + transferGrants.countOutside(connected)
                + "; orphanedEgressItems=" + egress.backlogItemsOutside(connected)
                + "; orphanedEgressBytes=" + egress.backlogBytesOutside(connected);
    }

    public String diagnostics(){long now=System.currentTimeMillis();int cached=0;long cachedBytes=0,fetchRetries=0,fetchFailures=0;StringBuilder renditions=new StringBuilder();for(ActiveVideoMedia media:active.values()){cached+=media.cachedSegments();cachedBytes+=media.cachedBytes();fetchRetries+=media.fetchRetries();fetchFailures+=media.fetchFailures();if(renditions.length()>0)renditions.append(',');renditions.append(media.dimensions.width()).append('x').append(media.dimensions.height());}int reports=0;long recoveries=0,drops=0,underruns=0;long maximumDrift=0;for(VideoHealthRegistry.Report record:clientHealth.currentReports(now,this::isCurrentViewer)){reports++;recoveries+=record.value().decoderRecoveries();drops+=record.value().videoDrops();underruns+=record.value().audioUnderruns();maximumDrift=Math.max(maximumDrift,Math.abs(record.value().driftMs()));}int queued=0;for(List<QueuedVideo> queue:queues.values())queued+=queue.size();return "Plex=ready; libraries="+libraries.size()+"; registeredTvs="+TelevisionLifecycle.count()+"; attachedTvs="+TelevisionLifecycle.attachedTelevisionCount()+"; attachedSessions="+TelevisionLifecycle.attachedSessionCount()+"; activeStreams="+tvStreams.activeStreamCount()+"/"+CinemarrSettings.maximumConcurrentStreams()+"; renditions="+(renditions.length()==0?"none":renditions)+"; cachedSegments="+cached+"; cachedBytes="+cachedBytes+"; fetchRetries="+fetchRetries+"; fetchFailures="+fetchFailures+"; queued="+queued+"; trackingClients="+visibleTelevisions.size()+"; workQueued="+workers.queuedTasks()+"; workActive="+workers.activeTasks()+"; workRejected="+workers.rejectedTasks() + workers.browseDiagnostics()+"; browseRateClients="+browseLimiter.trackedSubjects()+"; segmentRateClients="+segmentLimiter.trackedSubjects()+"; transferGrants="+transferGrants.size()+"; egressItems="+egress.backlogItems()+"; egressBytes="+egress.backlogBytes()+"; egressRejected="+egress.rejectedBatches()+"; mediaStarts="+tvStreams.pendingStarts()+"; mediaRetiring="+tvStreams.retiringMedia()+"; mediaCloseFailures="+tvStreams.closeFailures()+transferOwnershipDiagnostics()+"; healthReports="+reports+"; decoderRecoveries="+recoveries+"; videoDrops="+drops+"; audioUnderruns="+underruns+"; maxDriftMs="+maximumDrift;}
    public void tick(){long now=System.currentTimeMillis();clientHealth.prune(now,this::isCurrentViewer);for(TransferGrantRegistry.Window window:transferGrants.expireWindows(now))removeWindowEgress(window);egress.drain(64,1024L*1024L,this::sendCurrentSegment);try{sessions.tick(now);tickTelevisionStreams(now);pruneRetiredTransfers();tickRedstoneReceivers(now);Set<String> names=sessions.sessionNames();for(String name:names){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null&&state.transcoding()&&!anyTelevisionLoaded(name)){state=sessions.suspend(name,now);persist(state);publishSession(state,"Suspended while TV chunks are unloaded",false,null);}if(state!=null&&state.transcoding()){ActiveVideoMedia media=active.get(key(state.id(),state.generation()));if(media!=null)media.updateTimeline(now,state.positionMs(),state.paused(),workers,failure->{if(!closed)Cinemarr.LOGGER.warn("Unable to report Plex video timeline: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));});}if(state!=null&&state.transcoding()&&!state.paused()&&state.item()!=null&&state.item().durationMs()>0&&state.positionMs()>=state.item().durationMs())advance(state,null);}if(now-lastCheckpointMs>=5_000){lastCheckpointMs=now;for(String name:names){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null)persist(state);}}}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to stop inactive Plex video session: {}", SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));}}

    private int dormantSessions(){int count=0;long now=System.currentTimeMillis();for(CinemarrVideoSavedData.Record record:saved.records())if(sessions.snapshotIfPresent(record.sessionName(),now)==null)count++;return count;}
    private boolean anyTelevisionLoaded(String name){for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(name.equals(tv.sessionName())){if(!level.hasChunkAt(BlockPos.of(tv.controllerPos())))continue;boolean loaded=true;for(Long packed:tv.pixels())if(!level.hasChunkAt(BlockPos.of(packed))){loaded=false;break;}if(loaded)return true;}return false;}

    private void initializeReceiverPower(){for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())receiverPower.put(tv.id(),receiverPowered(level,tv));}
    private boolean receiverPowered(ServerLevel level,CinemarrWorldScreens.Television tv){BlockPos controller=BlockPos.of(tv.controllerPos());for(Direction direction:Direction.values()){BlockPos receiver=controller.relative(direction);if(level.getBlockState(receiver).is(CinemarrBlocks.redstoneReceiver())&&level.hasNeighborSignal(receiver))return true;}return false;}
    private void tickRedstoneReceivers(long now)throws IOException{Set<UUID> present=new HashSet<>();Set<String> risingSessions=new LinkedHashSet<>();for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions()){present.add(tv.id());boolean powered=receiverPowered(level,tv);Boolean previous=receiverPower.put(tv.id(),powered);if(tv.sessionName().isBlank())continue;VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(tv.sessionName(),now);if(state!=null&&RedstoneControlPolicy.action(previous!=null&&previous,powered,state.item()!=null,state.paused())!=RedstoneControlPolicy.Action.NONE)risingSessions.add(tv.sessionName());}receiverPower.keySet().retainAll(present);for(String name:risingSessions){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state==null||state.item()==null)continue;if(state.paused()){sessions.resume(name,now);VideoSessionCoordinator.Snapshot resumed=sessions.snapshot(name,now);restartIfNeeded(name,resumed);}else sessions.pause(name,now);VideoSessionCoordinator.Snapshot changed=sessions.snapshot(name,now);persist(changed);publishSession(changed,changed.paused()?"Paused by redstone receiver":"Resuming by redstone receiver",false,null);}}

    private void restartIfNeeded(String name,VideoSessionCoordinator.Snapshot expected){
        if(!restartingSessions.add(expected.id()))return;CinemarrVideoSavedData.Record record=saved.record(name);if(record==null){restartingSessions.remove(expected.id());return;}
        RenditionPolicy.Dimensions rendition=renditionForSession(name);
        workers.supply(()->{try{
            PlexVideoService.ResolvedLibrary library=library(record.libraryId());if(library==null)throw new IOException("Saved video library is no longer configured");
            PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(record.item().key());if(!library.rule().allows(metadata.item(),4))throw new IOException("Saved video item is no longer allowed");
            StreamSelection streams;try{streams=selection(metadata.streams(),record.audioStreamId(),record.subtitleStreamId(),false);}catch(IOException unavailable){streams=selection(metadata.streams(),-1,-1,false);}
            StartOptions options=new StartOptions(rendition,streams);startingOptions.set(options);VideoSessionCoordinator.Snapshot state;
            try{state=sessions.restart(name,System.currentTimeMillis(),expected.generation());}finally{startingOptions.remove();}
            return new PreparedPlayback(state, options, record.libraryId());
        }catch(IOException failure){throw new WrappedFailure(failure);}}).whenComplete((prepared, failure)->scheduleMain(()->{VideoSessionCoordinator.Snapshot state=prepared==null?null:prepared.state;restartingSessions.remove(expected.id());if(failure!=null){Cinemarr.LOGGER.warn("Unable to resume saved video session {}: {}",name,SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));return;}state=recordPlayback(prepared);if(state==null)return;persist(state);publishSession(state,state.playbackMessage(),true,null);}));
    }

    private void persist(VideoSessionCoordinator.Snapshot state){
        if(state==null||state.item()==null||!metadataMatches(state))return;String library=playbackLibraries.get(state.id());StartOptions options=playbackOptions.get(state.id());if(library==null||options==null)return;
        saved.put(new CinemarrVideoSavedData.Record(state.name(),library,state.item(),state.positionMs(),state.paused(),options.streams.audioId,options.streams.subtitleId,queues.getOrDefault(state.id(),Collections.emptyList())));
    }
    private void scheduleMain(Runnable action) {
        if (!closed) server.execute(() -> { if (!closed) action.run(); });
    }

    private VideoSessionCoordinator.Snapshot recordPlayback(PreparedPlayback prepared) {
        if (closed) return null;
        return sessions.applyPlaybackMetadataIfCurrent(prepared.state, System.currentTimeMillis(), () -> {
            playbackOptions.put(prepared.state.id(), prepared.options);
            if (prepared.libraryId != null) playbackLibraries.put(prepared.state.id(), prepared.libraryId);
            playbackMetadataGenerations.put(prepared.state.id(), prepared.state.playbackGeneration());
        });
    }
    private boolean metadataMatches(VideoSessionCoordinator.Snapshot state) {
        return state != null && Long.valueOf(state.playbackGeneration()).equals(playbackMetadataGenerations.get(state.id()));
    }

    private boolean current(VideoSessionCoordinator.Snapshot state){return !closed&&state!=null&&sessions.snapshotIfPresent(state.id(),state.generation(),System.currentTimeMillis())!=null;}

    private VideoSessionCoordinator.Snapshot restoreDormant(VideoSessionCoordinator.Snapshot tuned) {
        CinemarrVideoSavedData.Record record=saved.record(tuned.name());PlexVideoService.ResolvedLibrary library=record==null?null:library(record.libraryId());
        if(record==null||library==null||!library.rule().allows(record.item(),4))return tuned;
        playbackLibraries.put(tuned.id(),record.libraryId());playbackOptions.put(tuned.id(),new StartOptions(renditionForSession(tuned.name()),new StreamSelection(Collections.emptyList(),record.audioStreamId(),record.subtitleStreamId())));queues.put(tuned.id(),new ArrayList<>(record.queue()));
        VideoSessionCoordinator.Snapshot restored = sessions.restore(tuned.name(), record.item(), record.positionMs(), true, System.currentTimeMillis());
        playbackMetadataGenerations.put(restored.id(), restored.playbackGeneration());
        return restored;
    }

    public void televisionRemoved(UUID televisionId,String sessionName) {
        if(televisionId==null)return;long now=System.currentTimeMillis();VideoSessionCoordinator.Snapshot state=sessionName==null||sessionName.isBlank()?null:sessions.snapshotIfPresent(sessionName,now);
        if(state!=null&&state.televisions().contains(televisionId)&&state.televisions().size()==1&&state.item()!=null){String library=playbackLibraries.get(state.id());StartOptions options=playbackOptions.get(state.id());if(library!=null&&options!=null&&metadataMatches(state))saved.put(new CinemarrVideoSavedData.Record(state.name(),library,state.item(),state.positionMs(),true,options.streams.audioId,options.streams.subtitleId,queues.getOrDefault(state.id(),Collections.emptyList())));}
        try{sessions.untune(televisionId);}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to stop removed TV session: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));}
        if(state!=null&&state.televisions().size()==1){playbackLibraries.remove(state.id());playbackOptions.remove(state.id());playbackMetadataGenerations.remove(state.id());queues.remove(state.id());restartingSessions.remove(state.id());advancingSessions.remove(state.id());}refreshAllTracking();
    }

    private static RenditionPolicy.Dimensions renditionFor(CinemarrWorldScreens.Television tv){return renditionFor(tv,null);}
    private static RenditionPolicy.Dimensions renditionFor(CinemarrWorldScreens.Television tv,PlexVideoService.PlaybackMetadata metadata){int sourceWidth=metadata==null?1920:metadata.sourceWidth(),sourceHeight=metadata==null?1080:metadata.sourceHeight();return RenditionPolicy.chooseForScreen(tv.width(),tv.height(),tv.renditionWidth(),tv.renditionHeight(),sourceWidth,sourceHeight,CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight());}
    private RenditionPolicy.Dimensions renditionForSession(String name){int width=0,height=0;for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(name.equals(tv.sessionName())){RenditionPolicy.Dimensions value=renditionFor(tv);width=Math.max(width,value.width());height=Math.max(height,value.height());}return width==0?RenditionPolicy.choose(1280,720,1920,1080,CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight()):RenditionPolicy.choose(width,height,width,height,width,height);}

    private List<CinemarrWorldScreens.Television> televisionsForSession(String name){List<CinemarrWorldScreens.Television> values=new ArrayList<>();for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(name.equals(tv.sessionName()))values.add(tv);return values;}

    private VideoSessionCoordinator.MediaHandle startTelevisionMedia(TelevisionStreamPool.Request request, UUID streamId, long generation) throws IOException {
        StartOptions configured=playbackOptions.get(request.timeline.id());
        if(configured==null)throw new IOException("TV playback metadata is not ready");
        PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(request.timeline.item().key());
        RenditionPolicy.Dimensions dimensions=request.display.origin()==TvDisplaySettings.Origin.QUICK
                ? RenditionPolicy.chooseForScreen(request.width,request.height,request.display.resolution().width(),request.display.resolution().height(),metadata.sourceWidth(),metadata.sourceHeight(),CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight())
                : RenditionPolicy.chooseForDisplay(request.width,request.height,request.display.resolution(),metadata.sourceWidth(),metadata.sourceHeight(),CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight());
        startingOptions.set(new StartOptions(dimensions,configured.streams));
        try{return startMedia(streamId,generation,metadata.item(),request.timeline.positionMs());}finally{startingOptions.remove();}
    }
    private void tickTelevisionStreams(long now) throws IOException {
        Set<UUID> retained=new HashSet<UUID>();
        for(ServerLevel world:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(world).televisions()) {
            VideoSessionCoordinator.Snapshot timeline=sessions.snapshotIfPresent(tv.sessionName(),now);
            if(timeline==null)continue;
            retained.add(tv.id());Set<UUID> viewers=new HashSet<UUID>();
            for(Map.Entry<UUID,Map<UUID,Long>> entry:visibleTelevisions.entrySet())if(entry.getValue().containsKey(tv.id()))viewers.add(entry.getKey());
            tvStreams.update(new TelevisionStreamPool.Request(tv.id(),timeline,tv.displaySettings(),tv.width(),tv.height(),viewers),now);
            VideoSessionCoordinator.Snapshot stream=tvStreams.snapshot(tv.id(),now);
            ActiveVideoMedia media=stream==null?null:active.get(key(stream.id(),stream.generation()));
            if(media!=null)media.updateTimeline(now,timeline.positionMs(),timeline.paused(),workers,failure->{if(!closed)Cinemarr.LOGGER.warn("Unable to report Plex video timeline: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));});
        }
        tvStreams.retain(retained);tvStreams.tick(now);
        long revision=tvStreams.changes();
        if(revision!=publishedStreamChanges){publishedStreamChanges=revision;for(String name:sessions.sessionNames()){VideoSessionCoordinator.Snapshot timeline=sessions.snapshotIfPresent(name,now);if(timeline!=null)publishSession(timeline,timeline.playbackMessage(),true,null);}}
    }

    private VideoSessionCoordinator.MediaHandle startMedia(UUID sessionId,long generation,VideoMediaItem item,long offset) throws IOException {
        StartOptions options=startingOptions.get();if(options==null)options=new StartOptions(RenditionPolicy.choose(1280,720,1920,1080,CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight()),new StreamSelection(Collections.emptyList(),-1,-1));
        RenditionPolicy.Dimensions dimensions=options.rendition;StreamSelection selection=options.streams;
        PlexVideoService.VideoSession plexSession=plex.start(item,dimensions,offset,selection.audioId<0?null:selection.audioId,selection.subtitleId<0?Integer.valueOf(0):selection.subtitleId);
        try {
            if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Video server is stopping");
            PlexVideoService.MediaPlaylist playlist=plex.mediaPlaylist(plexSession,offset);
            if (closed || Thread.currentThread().isInterrupted()) throw new IOException("Video server is stopping");
            List<SegmentReference> references=parsePlaylist(playlist);
            ActiveVideoMedia media=new ActiveVideoMedia(plex,plexSession,playlist,references,dimensions,item.durationMs(),selection.options,selection.audioId,selection.subtitleId); String key=key(sessionId,generation); active.put(key,media);
            return ()->{active.remove(key);media.close();};
        } catch(IOException|RuntimeException failure){try{plex.stop(plexSession);}catch(IOException ignored){}throw failure;}
    }
    private void sendManifest(ServerPlayer player,CinemarrWorldScreens.Television tv,VideoSessionCoordinator.Snapshot timeline){VideoSessionCoordinator.Snapshot state=tvStreams.snapshot(tv.id(),System.currentTimeMillis());if(state==null)return;ActiveVideoMedia media=active.get(key(state.id(),state.generation()));if(media==null)return;int first=media.segmentAt(state.positionMs());sendManifest(player, tvStreams.identity(state.id(), state.generation()),media,first,state.item()==null?0:state.item().durationMs());}
    private void sendManifest(ServerPlayer player,VideoStreamIdentity identity,ActiveVideoMedia media,int first,long duration){if(!isCurrentViewer(identity,player.getUUID()))return;List<VideoPackets.SegmentDescriptor> descriptors=media.descriptors(first,ProtocolLimits.MAX_VIDEO_SEGMENTS_PER_MANIFEST);if(descriptors.isEmpty())return;int end=first+descriptors.size();CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SegmentManifest(new VideoPackets.SegmentManifest(identity,media.dimensions.width(),media.dimensions.height(),"mpegts","h264","aac",duration,end<media.segmentCount(),descriptors)));}
    private void publish(ServerPlayer requester,CinemarrWorldScreens.Television tv,VideoSessionCoordinator.Snapshot state,PresentationMode mode,String message,boolean includeManifest){for(ServerPlayer recipient:recipients(tv,requester)){sendState(recipient,tv,state,mode,message);sendQueue(recipient,state);if(includeManifest)sendManifest(recipient,tv,state);}}
    private void publishIdle(ServerPlayer requester,CinemarrWorldScreens.Television tv,String message){for(ServerPlayer recipient:recipients(tv,requester))sendIdle(recipient,tv,message);}
    private void publishSession(VideoSessionCoordinator.Snapshot state,String message,boolean includeManifest,ServerPlayer requester){for(CinemarrWorldScreens.Television tv:televisionsForSession(state.name()))for(ServerPlayer recipient:recipients(tv,requester)){sendState(recipient,tv,state,tv.presentationMode(),message);sendQueue(recipient,state);if(includeManifest)sendManifest(recipient,tv,state);}}
    private void publishQueue(VideoSessionCoordinator.Snapshot state,ServerPlayer requester){Set<ServerPlayer> recipients=new LinkedHashSet<>();if(requester!=null)recipients.add(requester);for(CinemarrWorldScreens.Television tv:televisionsForSession(state.name()))recipients.addAll(recipients(tv,null));for(ServerPlayer recipient:recipients)sendQueue(recipient,state);}
    private void sendQueue(ServerPlayer player,VideoSessionCoordinator.Snapshot state){CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionQueue(new VideoPackets.SessionQueue(state.id(),state.generation(),queues.getOrDefault(state.id(),Collections.emptyList()))));}
    private Set<ServerPlayer> recipients(CinemarrWorldScreens.Television tv,ServerPlayer requester){Set<ServerPlayer> recipients=new LinkedHashSet<>();if(requester!=null)recipients.add(requester);for(Map.Entry<UUID,Map<UUID,Long>> entry:visibleTelevisions.entrySet())if(entry.getValue().containsKey(tv.id())){ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());if(player!=null)recipients.add(player);}return recipients;}
    private boolean canControl(ServerPlayer player,CinemarrWorldScreens.Television tv){return tv.owner().equals(player.getUUID())||permission(player)>=CinemarrSettings.operatorPermissionLevel();}
    private void sendState(ServerPlayer player,CinemarrWorldScreens.Television tv,VideoSessionCoordinator.Snapshot state,PresentationMode mode,String message){VideoSessionCoordinator.Snapshot stream=tvStreams.snapshot(tv.id(),System.currentTimeMillis());
        if(!tvStreams.message(tv.id()).isEmpty())message=tvStreams.message(tv.id());
        VideoPackets.SessionStatus status=state.item()==null?VideoPackets.SessionStatus.IDLE:state.paused()?VideoPackets.SessionStatus.PAUSED:stream!=null&&stream.transcoding()?VideoPackets.SessionStatus.PLAYING:VideoPackets.SessionStatus.BUFFERING;ActiveVideoMedia media=stream==null?null:active.get(key(stream.id(),stream.generation()));StartOptions configured=playbackOptions.get(state.id());StreamSelection selected=configured==null?new StreamSelection(Collections.emptyList(),-1,-1):configured.streams;List<VideoStreamOption> streams=media==null?selected.options:media.options;int audio=media==null?selected.audioId:media.audioId,subtitle=media==null?selected.subtitleId:media.subtitleId;CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionState(new VideoPackets.SessionState(tv.id(),tv.controllerPos(),stream==null?tv.id():stream.id(),stream==null?0:stream.generation(),status,state.item(),state.positionMs(),state.item()==null?0:state.item().durationMs(),state.paused(),mode,tv.width(),tv.height(),tv.mask(),tv.facing(),tv.plane(),tv.minimumU(),tv.minimumV(),streams,audio,subtitle,state.serverEpochMs(),canControl(player,tv),message).withDisplay(tv.displaySettings(),media==null?0:media.dimensions.width(),media==null?0:media.dimensions.height()).withTimeline(state.id(),state.generation())));}
    private void sendIdle(ServerPlayer player,CinemarrWorldScreens.Television tv,String message){CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionState(new VideoPackets.SessionState(tv.id(),tv.controllerPos(),new UUID(0,0),0,VideoPackets.SessionStatus.IDLE,null,0,0,false,tv.presentationMode(),tv.width(),tv.height(),tv.mask(),tv.facing(),tv.plane(),tv.minimumU(),tv.minimumV(),Collections.emptyList(),-1,-1,System.currentTimeMillis(),canControl(player,tv),message).withDisplay(tv.displaySettings(),0,0)));}
    private PlexVideoService.ResolvedLibrary library(String id,ServerPlayer player){for(PlexVideoService.ResolvedLibrary value:libraries)if(value.rule().id().equals(id)){if(permission(player)<value.rule().permissionLevel()){error(player,"Library permission denied");return null;}return value;}error(player,"Unknown video library");return null;}
    private PlexVideoService.ResolvedLibrary library(String id){for(PlexVideoService.ResolvedLibrary value:libraries)if(value.rule().id().equals(id))return value;return null;}
    private static int permission(ServerPlayer player){for(int level=4;level>=0;level--)if(CinemarrPermissions.has(player,level))return level;return 0;}
    private void error(ServerPlayer player,String message){CinemarrNetwork.sendToPlayer(player,new CinemarrPayloads.ErrorMessage(message));}
    private void failure(ServerPlayer player,Throwable error){Throwable value=error instanceof java.util.concurrent.CompletionException&&error.getCause()!=null?error.getCause():error;if(value instanceof WrappedFailure&&value.getCause()!=null)value=value.getCause();String message=SecretRedactor.message(value,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl());Cinemarr.LOGGER.warn("Cinemarr video request failed: {}",message);this.error(player,message);}
    private static String key(UUID session,long generation){return session+":"+generation;}
    private static List<SegmentReference> parsePlaylist(PlexVideoService.MediaPlaylist playlist){List<SegmentReference> values=new ArrayList<>();for(HlsPlaylist.MediaSegment value:playlist.segments())values.add(new SegmentReference(value));return values;}
    private static StreamSelection selection(List<VideoStreamOption> options,int requestedAudio,int requestedSubtitle,boolean defaults)throws IOException{int audio=requestedAudio,subtitle=requestedSubtitle;if(audio<0)for(VideoStreamOption option:options)if(option.kind()==VideoStreamOption.Kind.AUDIO&&option.selected()){audio=option.id();break;}if(defaults&&subtitle<0)for(VideoStreamOption option:options)if(option.kind()==VideoStreamOption.Kind.SUBTITLE&&option.selected()){subtitle=option.id();break;}if(audio>=0&&!contains(options,VideoStreamOption.Kind.AUDIO,audio))throw new IOException("Requested Plex audio stream is unavailable");if(subtitle>=0&&!contains(options,VideoStreamOption.Kind.SUBTITLE,subtitle))throw new IOException("Requested Plex subtitle stream is unavailable");return new StreamSelection(options,audio,subtitle);}
    private static boolean contains(List<VideoStreamOption> options,VideoStreamOption.Kind kind,int id){for(VideoStreamOption option:options)if(option.kind()==kind&&option.id()==id)return true;return false;}
    @Override public void close(){if(closed)return;closed=true;browseLimiter.clear();segmentLimiter.clear();TelevisionLifecycle.listener(null);long now=System.currentTimeMillis();for(String name:sessions.sessionNames()){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null)persist(state);}workers.close();try{tvStreams.close();sessions.close();}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to close Plex video sessions: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));}egress.clear();active.clear();startingOptions.remove();playbackOptions.clear();playbackMetadataGenerations.clear();playbackLibraries.clear();queues.clear();clientHealth.clear();browseGenerations.clear();transferGrants.clear();restartingSessions.clear();advancingSessions.clear();trackedChunks.clear();viewingSessions.clear();visibleTelevisions.clear();}
    private static final class WrappedFailure extends RuntimeException{WrappedFailure(Throwable cause){super(cause);}}
    private static final class PreparedPlayback {
        final VideoSessionCoordinator.Snapshot state;
        final StartOptions options;
        final String libraryId;
        PreparedPlayback(VideoSessionCoordinator.Snapshot state, StartOptions options, String libraryId) {
            this.state = state; this.options = options; this.libraryId = libraryId;
        }
    }
    private static final class StreamSelection{final List<VideoStreamOption> options;final int audioId,subtitleId;StreamSelection(List<VideoStreamOption> options,int audioId,int subtitleId){this.options=Collections.unmodifiableList(new ArrayList<>(options));this.audioId=audioId;this.subtitleId=subtitleId;}}
    private static final class StartOptions{final RenditionPolicy.Dimensions rendition;final StreamSelection streams;StartOptions(RenditionPolicy.Dimensions rendition,StreamSelection streams){this.rendition=rendition;this.streams=streams;}}
    private static final class TrackedChunk{final ServerLevel level;final long chunk;TrackedChunk(ServerLevel level,long chunk){this.level=level;this.chunk=chunk;}@Override public boolean equals(Object value){if(this==value)return true;if(!(value instanceof TrackedChunk))return false;TrackedChunk other=(TrackedChunk)value;return level==other.level&&chunk==other.chunk;}@Override public int hashCode(){return 31*System.identityHashCode(level)+Long.hashCode(chunk);}}
}
