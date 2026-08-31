package stonytark.cinemarr.server;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loader-neutral server boundary for video browse/control and pull-based HLS segment relay. */
public final class ServerVideoManager implements AutoCloseable {
    private static final int PAGE_SIZE = 20;
    private final MinecraftServer server;
    private final CinemarrVideoSavedData saved;
    private final PlexVideoService plex;
    private final List<PlexVideoService.ResolvedLibrary> libraries;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Cinemarr video worker"); thread.setDaemon(true); return thread;
    });
    private final SlidingWindowRateLimiter browseLimiter = new SlidingWindowRateLimiter();
    private final SlidingWindowRateLimiter segmentLimiter = new SlidingWindowRateLimiter();
    private final Map<String, ActiveMedia> active = new ConcurrentHashMap<>();
    private final ThreadLocal<StartOptions> startingOptions = new ThreadLocal<>();
    private final Map<UUID, StartOptions> playbackOptions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playbackLibraries = new ConcurrentHashMap<>();
    private final Map<UUID, TransferGrant> transferGrants = new ConcurrentHashMap<>();
    private final Set<UUID> restartingSessions=ConcurrentHashMap.newKeySet();
    private final Set<UUID> advancingSessions=ConcurrentHashMap.newKeySet();
    private final Map<UUID,List<QueuedVideo>> queues=new ConcurrentHashMap<>();
    private final Map<UUID,HealthRecord> clientHealth=new ConcurrentHashMap<>();
    private final Map<UUID, Set<TrackedChunk>> trackedChunks = new HashMap<>();
    private final Map<UUID, Set<String>> viewingSessions = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> visibleTelevisions = new HashMap<>();
    private final Map<UUID, Boolean> receiverPower = new HashMap<>();
    private final VideoSessionCoordinator sessions;
    private long lastCheckpointMs;

    public ServerVideoManager(MinecraftServer server, PlexVideoService plex, List<PlexVideoService.ResolvedLibrary> libraries,
                              CinemarrVideoSavedData saved) {
        this.server = server; this.plex = plex; this.libraries = Collections.unmodifiableList(new ArrayList<>(libraries));
        this.saved=saved;
        this.sessions = new VideoSessionCoordinator(CinemarrSettings.maximumConcurrentStreams(),
                CinemarrSettings.inactiveSessionGraceSeconds() * 1000L, this::startMedia);
        restoreSessions(); initializeReceiverPower(); TelevisionLifecycle.listener(this::televisionRemoved);
    }

    public void resetAcceptanceSession() { saved.remove("cinemarr-acceptance"); }

    private void restoreSessions(){
        long now=System.currentTimeMillis();Set<String> tunedNames=new LinkedHashSet<>();
        for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(!tv.sessionName().isBlank()){sessions.tune(tv.id(),tv.sessionName());TelevisionLifecycle.attachment(tv.id(),true);tunedNames.add(tv.sessionName());}
        for(CinemarrVideoSavedData.Record record:saved.records()){
            VideoSessionCoordinator.Snapshot current=sessions.snapshotIfPresent(record.sessionName(),now);PlexVideoService.ResolvedLibrary library=library(record.libraryId());
            if(current==null||library==null||!library.rule().allows(record.item(),4))continue;
            playbackLibraries.put(current.id(),record.libraryId());playbackOptions.put(current.id(),new StartOptions(renditionForSession(record.sessionName()),new StreamSelection(Collections.emptyList(),record.audioStreamId(),record.subtitleStreamId())));
            queues.put(current.id(),new ArrayList<>(record.queue()));
            sessions.restore(record.sessionName(),record.item(),record.positionMs(),record.paused(),now);
        }
    }

    public void chunkSent(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        trackedChunks.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(new TrackedChunk(level, chunk.toLong()));
        refreshTracking(player);
    }

    public void chunkUnwatched(ServerPlayer player, ServerLevel level, ChunkPos chunk) {
        Set<TrackedChunk> values = trackedChunks.get(player.getUUID());
        if (values != null) values.remove(new TrackedChunk(level, chunk.toLong()));
        refreshTracking(player);
    }

    /** Fabric fallback for loaders without per-player chunk-watch callbacks. */
    public void synchronizeTrackingRadius(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos center = player.chunkPosition();
        int radius = Math.max(2, server.getPlayerList().getViewDistance());
        Set<TrackedChunk> visible = new HashSet<>();
        for (CinemarrWorldScreens.Television television : CinemarrWorldScreens.get(level).televisions()) {
            for (Long packed : television.pixels()) {
                BlockPos pixel = BlockPos.of(packed);
                int x = pixel.getX() >> 4, z = pixel.getZ() >> 4;
                if (Math.abs(x - center.x) <= radius && Math.abs(z - center.z) <= radius) {
                    visible.add(new TrackedChunk(level, ChunkPos.asLong(x, z)));
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
        trackedChunks.remove(id); viewingSessions.remove(id); visibleTelevisions.remove(id);clientHealth.remove(id);transferGrants.remove(id);
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
        UUID playerId = player.getUUID(); long now = System.currentTimeMillis();
        Map<UUID, CinemarrWorldScreens.Television> televisions = new LinkedHashMap<>();
        for (TrackedChunk tracked : trackedChunks.getOrDefault(playerId, Collections.emptySet())) {
            for (CinemarrWorldScreens.Television tv : CinemarrWorldScreens.get(tracked.level).televisionsForChunk(new ChunkPos(tracked.chunk))) {
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
        for(String name:nextSessions){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null&&state.item()!=null&&!state.transcoding()&&!state.paused())restartIfNeeded(name,state);}

        Map<UUID,Long> previousTvs = visibleTelevisions.getOrDefault(playerId, Collections.emptyMap());
        for(Map.Entry<UUID,Long> previous:previousTvs.entrySet())if(!televisions.containsKey(previous.getKey()))CinemarrNetwork.sendToPlayer(player,new VideoPayloads.TelevisionRemoved(new VideoPackets.TelevisionRemoved(previous.getValue())));
        Map<UUID,Long> nextTvs=new LinkedHashMap<>();
        for (CinemarrWorldScreens.Television tv : televisions.values()){nextTvs.put(tv.id(),tv.controllerPos());if(!previousTvs.containsKey(tv.id()))sendCurrent(player,tv,now);}
        visibleTelevisions.put(playerId,nextTvs);
    }

    private void sendCurrent(ServerPlayer player, CinemarrWorldScreens.Television tv, long now) {
        VideoSessionCoordinator.Snapshot state = tv.sessionName().isBlank() ? null : sessions.snapshotIfPresent(tv.sessionName(), now);
        if (state == null) sendIdle(player, tv, "TV is idle");
        else { sendState(player, tv, state, tv.presentationMode(), state.paused() ? "Paused" : "Playing");sendQueue(player,state);sendManifest(player, state); }
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
        if (!browseLimiter.allow(player.getUUID(), 8, System.currentTimeMillis())) { error(player, "Video browse rate limit exceeded"); return; }
        PlexVideoService.ResolvedLibrary library = library(request.libraryId(), player);
        if (library == null) return;
        int playerPermission=permission(player);
        CompletableFuture.supplyAsync(() -> {
            try { return plex.browse(library, request.parentKey(), request.query(), Math.max(0, request.page()), PAGE_SIZE, playerPermission); }
            catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((page, failure) -> server.execute(() -> {
            if (failure != null) { failure(player, failure); return; }
            CinemarrNetwork.sendToPlayer(player, new VideoPayloads.BrowseResults(new VideoPackets.BrowseResults(
                    request.libraryId(), request.parentKey(), request.query(), Math.max(0, request.page()), page.hasMore(), page.items())));
        }));
    }

    public void command(ServerPlayer player, VideoPackets.SessionCommand command) {
        BlockPos controller = BlockPos.of(command.controllerPos());
        CinemarrWorldScreens.Television television = CinemarrWorldScreens.get(player.serverLevel()).television(controller);
        if (television == null) { error(player, "The TV Controller has no active screen"); return; }
        if (!television.owner().equals(player.getUUID()) && permission(player) < CinemarrSettings.operatorPermissionLevel()) {
            error(player, "Only the TV owner or an operator can control this TV"); return;
        }
        try {
            String requestedSession=command.sessionName().isBlank()?television.sessionName():command.sessionName();
            VideoSessionCoordinator.Snapshot tuned = sessions.tune(television.id(), requestedSession);
            TelevisionLifecycle.attachment(television.id(),true);
            if (tuned.item() == null) tuned = restoreDormant(tuned);
            CinemarrWorldScreens screenData=CinemarrWorldScreens.get(player.serverLevel());
            screenData.updateSession(controller,tuned.name());
            refreshAllTracking();
            if(command.action()==VideoPackets.SessionAction.SET_PRESENTATION)screenData.updatePresentation(controller,command.presentationMode());
            PresentationMode presentation=command.action()==VideoPackets.SessionAction.SET_PRESENTATION?command.presentationMode():television.presentationMode();
            if (command.action() == VideoPackets.SessionAction.TUNE) { publish(player, television, tuned, presentation, "Tuned", true); return; }
            if (command.expectedGeneration() != tuned.generation()) { error(player, "TV state changed; refresh before controlling it"); return; }
            switch (command.action()) {
                case PAUSE: sessions.pause(tuned.name(), System.currentTimeMillis());VideoSessionCoordinator.Snapshot paused=sessions.snapshot(tuned.name(),System.currentTimeMillis());publishSession(paused,"Paused",false,player);persist(paused);break;
                case RESUME: sessions.resume(tuned.name(), System.currentTimeMillis());VideoSessionCoordinator.Snapshot resumed=sessions.snapshot(tuned.name(),System.currentTimeMillis());persist(resumed);publishSession(resumed,"Resuming",false,player);restartIfNeeded(tuned.name(),resumed);break;
                case SEEK: asyncSeek(player, television, tuned, command, presentation); break;
                case PLAY: asyncPlay(player, television, tuned, command, presentation); break;
                case STOP: VideoSessionCoordinator.Snapshot stopped=sessions.stop(tuned.name(),System.currentTimeMillis());playbackLibraries.remove(tuned.id());playbackOptions.remove(tuned.id());queues.remove(tuned.id());saved.remove(tuned.name());publishSession(stopped,"Stopped",false,player);break;
                case SET_PRESENTATION: publish(player, television, tuned, presentation, "Presentation updated", false); break;
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
        CompletableFuture.supplyAsync(() -> {
            try {
                String itemKey=command.itemKey().isBlank()&&tuned.item()!=null?tuned.item().key():command.itemKey();
                PlexVideoService.PlaybackMetadata metadata = plex.metadataDetails(itemKey);VideoMediaItem item=metadata.item();
                if (!library.rule().allows(item, playerPermission)) throw new IOException("Video item is not allowed by this library policy");
                StartOptions options=new StartOptions(renditionFor(television,metadata),selection(metadata.streams(),command.audioStreamId(),command.subtitleStreamId(),command.action()!=VideoPackets.SessionAction.SET_STREAMS));
                startingOptions.set(options);VideoSessionCoordinator.Snapshot state;
                try{state=sessions.play(tuned.name(),item,command.action()==VideoPackets.SessionAction.SET_STREAMS?tuned.positionMs():command.seekPositionMs(),System.currentTimeMillis(),tuned.generation());}
                finally{startingOptions.remove();}
                playbackOptions.put(tuned.id(),options);
                playbackLibraries.put(tuned.id(),library.rule().id());
                return state;
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((state, failure) -> server.execute(() -> {
            if (failure != null) { failure(player, failure); return; }
            if(!current(state))return;
            persist(state);publish(player, television, state, presentation, "Buffering", true);
        }));
    }

    private void asyncSeek(ServerPlayer player, CinemarrWorldScreens.Television television,
                           VideoSessionCoordinator.Snapshot tuned, VideoPackets.SessionCommand command,
                           PresentationMode presentation) {
        RenditionPolicy.Dimensions rendition=renditionFor(television);
        CompletableFuture.runAsync(() -> { try {StartOptions previous=playbackOptions.get(tuned.id());StartOptions options=new StartOptions(rendition,previous==null?new StreamSelection(Collections.emptyList(),-1,-1):previous.streams);startingOptions.set(options);try{sessions.seek(tuned.name(),command.seekPositionMs(),System.currentTimeMillis(),tuned.generation());playbackOptions.put(tuned.id(),options);}finally{startingOptions.remove();} }
            catch (IOException failure) { throw new WrappedFailure(failure); } }, workers).whenComplete((unused, failure) -> server.execute(() -> {
            if (failure != null) { failure(player, failure); return; }
            VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(tuned.id(),tuned.generation()+1,System.currentTimeMillis());if(!current(state))return;persist(state);publish(player,television,state,presentation,"Buffering",true);
        }));
    }

    private void asyncQueue(ServerPlayer player,VideoSessionCoordinator.Snapshot tuned,VideoPackets.SessionCommand command){
        PlexVideoService.ResolvedLibrary library=library(command.libraryId(),player);if(library==null)return;int playerPermission=permission(player);
        CompletableFuture.supplyAsync(()->{try{VideoMediaItem item=plex.metadata(command.itemKey());if(!library.rule().allows(item,playerPermission))throw new IOException("Video item is not allowed by this library policy");return new QueuedVideo(library.rule().id(),item);}catch(IOException failure){throw new WrappedFailure(failure);}},workers).whenComplete((entry,failure)->server.execute(()->{
            if(failure!=null){failure(player,failure);return;}VideoSessionCoordinator.Snapshot current=sessions.snapshotIfPresent(tuned.id(),tuned.generation(),System.currentTimeMillis());if(current==null){error(player,"TV state changed while queueing");return;}
            List<QueuedVideo> queue=queues.computeIfAbsent(tuned.id(),ignored->new ArrayList<>());if(queue.size()>=CinemarrSettings.queueLimit()){error(player,"Video queue is full");return;}queue.add(entry);if(current.item()==null)advance(current,player);else{persist(current);publishQueue(current,player);}
        }));
    }

    private void removeQueue(ServerPlayer player,VideoSessionCoordinator.Snapshot state,int index){List<QueuedVideo> queue=queues.get(state.id());if(queue==null||index<0||index>=queue.size()){error(player,"Invalid video queue entry");return;}queue.remove(index);if(queue.isEmpty())queues.remove(state.id());persist(state);publishQueue(state,player);}

    private void advance(VideoSessionCoordinator.Snapshot expected,ServerPlayer requester){
        if(!advancingSessions.add(expected.id()))return;List<QueuedVideo> queue=queues.get(expected.id());QueuedVideo next=queue==null||queue.isEmpty()?null:queue.get(0);
        if(next==null){try{VideoSessionCoordinator.Snapshot stopped=sessions.stop(expected.name(),System.currentTimeMillis());playbackLibraries.remove(expected.id());playbackOptions.remove(expected.id());queues.remove(expected.id());saved.remove(expected.name());publishSession(stopped,"Queue finished",false,requester);}catch(IOException failure){if(requester!=null)failure(requester,failure);}finally{advancingSessions.remove(expected.id());}return;}
        RenditionPolicy.Dimensions rendition=renditionForSession(expected.name());
        CompletableFuture.supplyAsync(()->{try{PlexVideoService.ResolvedLibrary library=library(next.libraryId());if(library==null)throw new IOException("Queued video library is no longer configured");PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(next.item().key());if(!library.rule().allows(metadata.item(),4))throw new IOException("Queued video item is no longer allowed");StreamSelection streams=selection(metadata.streams(),-1,-1,true);StartOptions options=new StartOptions(rendition,streams);startingOptions.set(options);VideoSessionCoordinator.Snapshot state;try{state=sessions.play(expected.name(),metadata.item(),0,System.currentTimeMillis(),expected.generation());}finally{startingOptions.remove();}playbackLibraries.put(expected.id(),next.libraryId());playbackOptions.put(expected.id(),options);return state;}catch(IOException failure){throw new WrappedFailure(failure);}},workers).whenComplete((state,failure)->server.execute(()->{advancingSessions.remove(expected.id());if(failure!=null){if(requester!=null)failure(requester,failure);else Cinemarr.LOGGER.warn("Unable to advance video queue: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));return;}if(!current(state))return;List<QueuedVideo> current=queues.get(expected.id());if(current!=null){current.remove(next);if(current.isEmpty())queues.remove(expected.id());}persist(state);publishSession(state,"Playing next queued video",true,requester);}));
    }

    private void continueEpisode(ServerPlayer player,VideoSessionCoordinator.Snapshot expected){
        if(expected.item()==null||expected.item().kind()!=stonytark.cinemarr.core.library.MediaKind.EPISODE){error(player,"Continue is available only for episodes");return;}String libraryId=playbackLibraries.get(expected.id());PlexVideoService.ResolvedLibrary library=library(libraryId);if(library==null){error(player,"Playback library is no longer configured");return;}
        RenditionPolicy.Dimensions rendition=renditionForSession(expected.name());
        CompletableFuture.supplyAsync(()->{try{VideoMediaItem next=plex.nextEpisode(expected.item().key());if(next==null)throw new IOException("This is the final episode");if(!library.rule().allows(next,4))throw new IOException("Next episode is not allowed by this library policy");PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(next.key());StreamSelection streams=selection(metadata.streams(),-1,-1,true);StartOptions options=new StartOptions(rendition,streams);startingOptions.set(options);VideoSessionCoordinator.Snapshot state;try{state=sessions.play(expected.name(),metadata.item(),0,System.currentTimeMillis(),expected.generation());}finally{startingOptions.remove();}playbackOptions.put(expected.id(),options);return state;}catch(IOException failure){throw new WrappedFailure(failure);}},workers).whenComplete((state,failure)->server.execute(()->{if(failure!=null){failure(player,failure);return;}if(!current(state))return;persist(state);publishSession(state,"Continuing with next episode",true,player);}));
    }

    public void segments(ServerPlayer player, VideoPackets.SegmentRequest request) {
        if (request.requestId()<1 || request.chunkCount() < 1 || request.chunkCount() > 8 || request.firstChunk() < 0
                || request.firstChunk()>PlexVideoService.MAX_SEGMENT_BYTES/ProtocolLimits.MAX_VIDEO_CHUNK_BYTES
                || !segmentLimiter.allow(player.getUUID(), 40, System.currentTimeMillis())) { error(player, "Invalid or excessive segment request"); return; }
        if (!sessions.isViewer(request.sessionId(), request.generation(), player.getUUID())) { error(player,"Video media is available only while tracking its screen"); return; }
        ActiveMedia media=active.get(key(request.sessionId(),request.generation()));
        VideoSessionCoordinator.Snapshot timeline=sessions.snapshotIfPresent(request.sessionId(),request.generation(),System.currentTimeMillis());
        if (media==null || timeline==null || request.segmentIndex()<0 || request.segmentIndex()>=media.segmentCount()) { error(player,"Video segment is no longer available"); return; }
        if (media.presentationTime(request.segmentIndex())>timeline.positionMs()+ProtocolLimits.MAX_VIDEO_SEGMENT_LEAD_MS) { error(player,"Video segment request exceeds playback lead limit"); return; }
        transferGrants.put(player.getUUID(),new TransferGrant(request));
        CompletableFuture.supplyAsync(() -> { try { return media.segment(request.segmentIndex()); } catch(IOException failure){throw new WrappedFailure(failure);} },workers)
                .whenComplete((segment,failure)->server.execute(()->{
                    if(failure!=null){transferGrants.remove(player.getUUID());failure(player,failure);return;} int total=(segment.bytes.length+ProtocolLimits.MAX_VIDEO_CHUNK_BYTES-1)/ProtocolLimits.MAX_VIDEO_CHUNK_BYTES;
                    if(request.firstChunk()>=total){transferGrants.remove(player.getUUID());error(player,"Invalid video chunk window");return;}
                    int end=Math.min(total,request.firstChunk()+request.chunkCount());
                    for(int index=request.firstChunk();index<end;index++){int from=index*ProtocolLimits.MAX_VIDEO_CHUNK_BYTES,to=Math.min(segment.bytes.length,from+ProtocolLimits.MAX_VIDEO_CHUNK_BYTES);
                        CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SegmentChunk(new VideoPackets.SegmentChunk(request.sessionId(),request.generation(),request.requestId(),request.segmentIndex(),index,total,segment.reference.pts,true,segment.sha,Arrays.copyOfRange(segment.bytes,from,to))));}
                }));
    }
    public void manifest(ServerPlayer player,VideoPackets.SegmentManifestRequest request){
        if(request.firstSegmentIndex()<0||!sessions.isViewer(request.sessionId(),request.generation(),player.getUUID())){error(player,"Video manifest is available only while tracking its screen");return;}
        ActiveMedia media=active.get(key(request.sessionId(),request.generation()));if(media==null){error(player,"Video manifest is no longer available");return;}
        VideoSessionCoordinator.Snapshot timeline=sessions.snapshotIfPresent(request.sessionId(),request.generation(),System.currentTimeMillis());
        if(timeline==null||request.firstSegmentIndex()>=media.segmentCount()||media.presentationTime(request.firstSegmentIndex())>timeline.positionMs()+ProtocolLimits.MAX_VIDEO_SEGMENT_LEAD_MS){error(player,"Video manifest request exceeds playback lead limit");return;}
        sendManifest(player,request.sessionId(),request.generation(),media,request.firstSegmentIndex(),media.durationMs);
    }

    public void acknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) {
        TransferGrant grant=transferGrants.remove(player.getUUID());
        if(value.bufferedMs()<0||value.bufferedMs()>30_000||grant==null||!grant.matches(value)) error(player,"Invalid video buffer acknowledgement");
    }
    public void health(ServerPlayer player, VideoPackets.ClientHealth value) {
        if(Math.abs(value.driftMs())>30_000||value.bufferedMs()<0||value.bufferedMs()>60_000||!sessions.isViewer(value.sessionId(),value.generation(),player.getUUID())){error(player,"Invalid video health report");return;}clientHealth.put(player.getUUID(),new HealthRecord(value,System.currentTimeMillis()));
    }
    public String status(){return "Cinemarr video: "+TelevisionLifecycle.count()+" registered TV(s), "+sessions.activeStreamCount()+"/"+CinemarrSettings.maximumConcurrentStreams()+" active stream(s), "+TelevisionLifecycle.attachedSessionCount()+" attached session(s), "+dormantSessions()+" dormant session(s)";}
    public String diagnostics(){long now=System.currentTimeMillis();int cached=0;long fetchRetries=0,fetchFailures=0;StringBuilder renditions=new StringBuilder();for(ActiveMedia media:active.values()){cached+=media.cachedSegments();fetchRetries+=media.fetchRetries;fetchFailures+=media.fetchFailures;if(renditions.length()>0)renditions.append(',');renditions.append(media.dimensions.width()).append('x').append(media.dimensions.height());}int reports=0,recoveries=0,drops=0,underruns=0;long maximumDrift=0;for(HealthRecord record:clientHealth.values())if(now-record.receivedAt<=30_000){reports++;recoveries+=record.value.decoderRecoveries();drops+=record.value.videoDrops();underruns+=record.value.audioUnderruns();maximumDrift=Math.max(maximumDrift,Math.abs(record.value.driftMs()));}int queued=0;for(List<QueuedVideo> queue:queues.values())queued+=queue.size();return "Plex=ready; libraries="+libraries.size()+"; registeredTvs="+TelevisionLifecycle.count()+"; attachedTvs="+TelevisionLifecycle.attachedTelevisionCount()+"; attachedSessions="+TelevisionLifecycle.attachedSessionCount()+"; activeStreams="+sessions.activeStreamCount()+"/"+CinemarrSettings.maximumConcurrentStreams()+"; renditions="+(renditions.length()==0?"none":renditions)+"; cachedSegments="+cached+"; fetchRetries="+fetchRetries+"; fetchFailures="+fetchFailures+"; queued="+queued+"; trackingClients="+visibleTelevisions.size()+"; healthReports="+reports+"; decoderRecoveries="+recoveries+"; videoDrops="+drops+"; audioUnderruns="+underruns+"; maxDriftMs="+maximumDrift;}
    public void tick(){long now=System.currentTimeMillis();try{sessions.tick(now);tickRedstoneReceivers(now);Set<String> names=sessions.sessionNames();for(String name:names){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null&&state.transcoding()&&!anyTelevisionLoaded(name)){state=sessions.suspend(name,now);persist(state);publishSession(state,"Suspended while TV chunks are unloaded",false,null);}if(state!=null&&state.transcoding()&&!state.paused()&&state.item()!=null&&state.item().durationMs()>0&&state.positionMs()>=state.item().durationMs())advance(state,null);}if(now-lastCheckpointMs>=5_000){lastCheckpointMs=now;for(String name:names){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null)persist(state);}}}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to stop inactive Plex video session: {}", SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));}}

    private int dormantSessions(){int count=0;long now=System.currentTimeMillis();for(CinemarrVideoSavedData.Record record:saved.records())if(sessions.snapshotIfPresent(record.sessionName(),now)==null)count++;return count;}
    private boolean anyTelevisionLoaded(String name){for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(name.equals(tv.sessionName())){if(!level.hasChunkAt(BlockPos.of(tv.controllerPos())))continue;boolean loaded=true;for(Long packed:tv.pixels())if(!level.hasChunkAt(BlockPos.of(packed))){loaded=false;break;}if(loaded)return true;}return false;}

    private void initializeReceiverPower(){for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())receiverPower.put(tv.id(),receiverPowered(level,tv));}
    private boolean receiverPowered(ServerLevel level,CinemarrWorldScreens.Television tv){BlockPos controller=BlockPos.of(tv.controllerPos());for(Direction direction:Direction.values()){BlockPos receiver=controller.relative(direction);if(level.getBlockState(receiver).is(CinemarrBlocks.redstoneReceiver())&&level.hasNeighborSignal(receiver))return true;}return false;}
    private void tickRedstoneReceivers(long now)throws IOException{Set<UUID> present=new HashSet<>();Set<String> risingSessions=new LinkedHashSet<>();for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions()){present.add(tv.id());boolean powered=receiverPowered(level,tv);Boolean previous=receiverPower.put(tv.id(),powered);if(tv.sessionName().isBlank())continue;VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(tv.sessionName(),now);if(state!=null&&RedstoneControlPolicy.action(previous!=null&&previous,powered,state.item()!=null,state.paused())!=RedstoneControlPolicy.Action.NONE)risingSessions.add(tv.sessionName());}receiverPower.keySet().retainAll(present);for(String name:risingSessions){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state==null||state.item()==null)continue;if(state.paused()){sessions.resume(name,now);VideoSessionCoordinator.Snapshot resumed=sessions.snapshot(name,now);restartIfNeeded(name,resumed);}else sessions.pause(name,now);VideoSessionCoordinator.Snapshot changed=sessions.snapshot(name,now);persist(changed);publishSession(changed,changed.paused()?"Paused by redstone receiver":"Resuming by redstone receiver",false,null);}}

    private void restartIfNeeded(String name,VideoSessionCoordinator.Snapshot expected){
        if(!restartingSessions.add(expected.id()))return;CinemarrVideoSavedData.Record record=saved.record(name);if(record==null){restartingSessions.remove(expected.id());return;}
        RenditionPolicy.Dimensions rendition=renditionForSession(name);
        CompletableFuture.supplyAsync(()->{try{
            PlexVideoService.ResolvedLibrary library=library(record.libraryId());if(library==null)throw new IOException("Saved video library is no longer configured");
            PlexVideoService.PlaybackMetadata metadata=plex.metadataDetails(record.item().key());if(!library.rule().allows(metadata.item(),4))throw new IOException("Saved video item is no longer allowed");
            StreamSelection streams;try{streams=selection(metadata.streams(),record.audioStreamId(),record.subtitleStreamId(),false);}catch(IOException unavailable){streams=selection(metadata.streams(),-1,-1,false);}
            StartOptions options=new StartOptions(rendition,streams);startingOptions.set(options);VideoSessionCoordinator.Snapshot state;
            try{state=sessions.restart(name,System.currentTimeMillis(),expected.generation());}finally{startingOptions.remove();}
            playbackOptions.put(expected.id(),options);playbackLibraries.put(expected.id(),record.libraryId());return state;
        }catch(IOException failure){throw new WrappedFailure(failure);}},workers).whenComplete((state,failure)->server.execute(()->{restartingSessions.remove(expected.id());if(failure!=null){Cinemarr.LOGGER.warn("Unable to resume saved video session {}: {}",name,SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));return;}if(!current(state))return;persist(state);publishSession(state,state.paused()?"Paused":"Playing",true,null);}));
    }

    private void persist(VideoSessionCoordinator.Snapshot state){
        if(state==null||state.item()==null)return;String library=playbackLibraries.get(state.id());StartOptions options=playbackOptions.get(state.id());if(library==null||options==null)return;
        saved.put(new CinemarrVideoSavedData.Record(state.name(),library,state.item(),state.positionMs(),state.paused(),options.streams.audioId,options.streams.subtitleId,queues.getOrDefault(state.id(),Collections.emptyList())));
    }
    private boolean current(VideoSessionCoordinator.Snapshot state){return state!=null&&sessions.snapshotIfPresent(state.id(),state.generation(),System.currentTimeMillis())!=null;}

    private VideoSessionCoordinator.Snapshot restoreDormant(VideoSessionCoordinator.Snapshot tuned) {
        CinemarrVideoSavedData.Record record=saved.record(tuned.name());
        PlexVideoService.ResolvedLibrary library=record==null?null:library(record.libraryId());
        if(record==null||library==null||!library.rule().allows(record.item(),4))return tuned;
        playbackLibraries.put(tuned.id(),record.libraryId());
        playbackOptions.put(tuned.id(),new StartOptions(renditionForSession(tuned.name()),new StreamSelection(Collections.emptyList(),record.audioStreamId(),record.subtitleStreamId())));
        queues.put(tuned.id(),new ArrayList<>(record.queue()));
        return sessions.restore(tuned.name(),record.item(),record.positionMs(),true,System.currentTimeMillis());
    }

    public void televisionRemoved(UUID televisionId,String sessionName) {
        if(televisionId==null)return;
        long now=System.currentTimeMillis();
        VideoSessionCoordinator.Snapshot state=sessionName==null||sessionName.isBlank()?null:sessions.snapshotIfPresent(sessionName,now);
        if(state!=null&&state.televisions().contains(televisionId)&&state.televisions().size()==1&&state.item()!=null){
            String library=playbackLibraries.get(state.id());StartOptions options=playbackOptions.get(state.id());
            if(library!=null&&options!=null)saved.put(new CinemarrVideoSavedData.Record(state.name(),library,state.item(),state.positionMs(),true,options.streams.audioId,options.streams.subtitleId,queues.getOrDefault(state.id(),Collections.emptyList())));
        }
        try{sessions.untune(televisionId);}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to stop removed TV session: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));}
        if(state!=null&&state.televisions().size()==1){playbackLibraries.remove(state.id());playbackOptions.remove(state.id());queues.remove(state.id());restartingSessions.remove(state.id());advancingSessions.remove(state.id());}
        refreshAllTracking();
    }

    private static RenditionPolicy.Dimensions renditionFor(CinemarrWorldScreens.Television tv){return renditionFor(tv,null);}
    private static RenditionPolicy.Dimensions renditionFor(CinemarrWorldScreens.Television tv,PlexVideoService.PlaybackMetadata metadata){int sourceWidth=metadata==null?1920:metadata.sourceWidth(),sourceHeight=metadata==null?1080:metadata.sourceHeight();return RenditionPolicy.chooseForScreen(tv.width(),tv.height(),tv.renditionWidth(),tv.renditionHeight(),sourceWidth,sourceHeight,CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight());}
    private RenditionPolicy.Dimensions renditionForSession(String name){int width=0,height=0;for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(name.equals(tv.sessionName())){RenditionPolicy.Dimensions value=renditionFor(tv);width=Math.max(width,value.width());height=Math.max(height,value.height());}return width==0?RenditionPolicy.choose(1280,720,1920,1080,CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight()):RenditionPolicy.choose(width,height,width,height,width,height);}

    private List<CinemarrWorldScreens.Television> televisionsForSession(String name){List<CinemarrWorldScreens.Television> values=new ArrayList<>();for(ServerLevel level:server.getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(name.equals(tv.sessionName()))values.add(tv);return values;}

    private VideoSessionCoordinator.MediaHandle startMedia(UUID sessionId,long generation,VideoMediaItem item,long offset) throws IOException {
        StartOptions options=startingOptions.get();if(options==null)options=new StartOptions(RenditionPolicy.choose(1280,720,1920,1080,CinemarrSettings.maximumVideoWidth(),CinemarrSettings.maximumVideoHeight()),new StreamSelection(Collections.emptyList(),-1,-1));
        RenditionPolicy.Dimensions dimensions=options.rendition;StreamSelection selection=options.streams;
        PlexVideoService.VideoSession plexSession=plex.start(item,dimensions,offset,selection.audioId<0?null:selection.audioId,selection.subtitleId<0?Integer.valueOf(0):selection.subtitleId);
        try {
            PlexVideoService.MediaPlaylist playlist=plex.mediaPlaylist(plexSession,offset);
            List<SegmentReference> references=parsePlaylist(playlist);
            ActiveMedia media=new ActiveMedia(plex,plexSession,playlist,references,dimensions,item.durationMs(),selection.options,selection.audioId,selection.subtitleId); String key=key(sessionId,generation); active.put(key,media);
            return ()->{active.remove(key);plex.stop(plexSession);};
        } catch(IOException|RuntimeException failure){try{plex.stop(plexSession);}catch(IOException ignored){}throw failure;}
    }
    private void sendManifest(ServerPlayer player,VideoSessionCoordinator.Snapshot state){ActiveMedia media=active.get(key(state.id(),state.generation()));if(media==null)return;int first=media.segmentAt(state.positionMs());sendManifest(player,state.id(),state.generation(),media,first,state.item()==null?0:state.item().durationMs());}
    private void sendManifest(ServerPlayer player,UUID session,long generation,ActiveMedia media,int first,long duration){List<VideoPackets.SegmentDescriptor> descriptors=media.descriptors(first,ProtocolLimits.MAX_VIDEO_SEGMENTS_PER_MANIFEST);if(descriptors.isEmpty())return;int end=first+descriptors.size();CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SegmentManifest(new VideoPackets.SegmentManifest(session,generation,media.dimensions.width(),media.dimensions.height(),"mpegts","h264","aac",duration,end<media.segmentCount(),descriptors)));}
    private void publish(ServerPlayer requester,CinemarrWorldScreens.Television tv,VideoSessionCoordinator.Snapshot state,PresentationMode mode,String message,boolean includeManifest){for(ServerPlayer recipient:recipients(tv,requester)){sendState(recipient,tv,state,mode,message);sendQueue(recipient,state);if(includeManifest)sendManifest(recipient,state);}}
    private void publishIdle(ServerPlayer requester,CinemarrWorldScreens.Television tv,String message){for(ServerPlayer recipient:recipients(tv,requester))sendIdle(recipient,tv,message);}
    private void publishSession(VideoSessionCoordinator.Snapshot state,String message,boolean includeManifest,ServerPlayer requester){for(CinemarrWorldScreens.Television tv:televisionsForSession(state.name()))for(ServerPlayer recipient:recipients(tv,requester)){sendState(recipient,tv,state,tv.presentationMode(),message);sendQueue(recipient,state);if(includeManifest)sendManifest(recipient,state);}}
    private void publishQueue(VideoSessionCoordinator.Snapshot state,ServerPlayer requester){Set<ServerPlayer> recipients=new LinkedHashSet<>();if(requester!=null)recipients.add(requester);for(CinemarrWorldScreens.Television tv:televisionsForSession(state.name()))recipients.addAll(recipients(tv,null));for(ServerPlayer recipient:recipients)sendQueue(recipient,state);}
    private void sendQueue(ServerPlayer player,VideoSessionCoordinator.Snapshot state){CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionQueue(new VideoPackets.SessionQueue(state.id(),state.generation(),queues.getOrDefault(state.id(),Collections.emptyList()))));}
    private Set<ServerPlayer> recipients(CinemarrWorldScreens.Television tv,ServerPlayer requester){Set<ServerPlayer> recipients=new LinkedHashSet<>();if(requester!=null)recipients.add(requester);for(Map.Entry<UUID,Map<UUID,Long>> entry:visibleTelevisions.entrySet())if(entry.getValue().containsKey(tv.id())){ServerPlayer player=server.getPlayerList().getPlayer(entry.getKey());if(player!=null)recipients.add(player);}return recipients;}
    private boolean canControl(ServerPlayer player,CinemarrWorldScreens.Television tv){return tv.owner().equals(player.getUUID())||permission(player)>=CinemarrSettings.operatorPermissionLevel();}
    private void sendState(ServerPlayer player,CinemarrWorldScreens.Television tv,VideoSessionCoordinator.Snapshot state,PresentationMode mode,String message){VideoPackets.SessionStatus status=state.item()==null?VideoPackets.SessionStatus.IDLE:state.paused()?VideoPackets.SessionStatus.PAUSED:state.transcoding()?VideoPackets.SessionStatus.PLAYING:VideoPackets.SessionStatus.IDLE;ActiveMedia media=active.get(key(state.id(),state.generation()));List<VideoStreamOption> streams=media==null?Collections.emptyList():media.options;int audio=media==null?-1:media.audioId,subtitle=media==null?-1:media.subtitleId;CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionState(new VideoPackets.SessionState(tv.id(),tv.controllerPos(),state.id(),state.generation(),status,state.item(),state.positionMs(),state.item()==null?0:state.item().durationMs(),state.paused(),mode,tv.width(),tv.height(),tv.mask(),tv.facing(),tv.plane(),tv.minimumU(),tv.minimumV(),streams,audio,subtitle,System.currentTimeMillis(),canControl(player,tv),message)));}
    private void sendIdle(ServerPlayer player,CinemarrWorldScreens.Television tv,String message){CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionState(new VideoPackets.SessionState(tv.id(),tv.controllerPos(),new UUID(0,0),0,VideoPackets.SessionStatus.IDLE,null,0,0,false,tv.presentationMode(),tv.width(),tv.height(),tv.mask(),tv.facing(),tv.plane(),tv.minimumU(),tv.minimumV(),Collections.emptyList(),-1,-1,System.currentTimeMillis(),canControl(player,tv),message)));}
    private PlexVideoService.ResolvedLibrary library(String id,ServerPlayer player){for(PlexVideoService.ResolvedLibrary value:libraries)if(value.rule().id().equals(id)){if(permission(player)<value.rule().permissionLevel()){error(player,"Library permission denied");return null;}return value;}error(player,"Unknown video library");return null;}
    private PlexVideoService.ResolvedLibrary library(String id){for(PlexVideoService.ResolvedLibrary value:libraries)if(value.rule().id().equals(id))return value;return null;}
    private static int permission(ServerPlayer player){for(int level=4;level>=0;level--)if(player.createCommandSourceStack().hasPermission(level))return level;return 0;}
    private void error(ServerPlayer player,String message){CinemarrNetwork.sendToPlayer(player,new CinemarrPayloads.ErrorMessage(message));}
    private void failure(ServerPlayer player,Throwable error){Throwable value=error instanceof java.util.concurrent.CompletionException&&error.getCause()!=null?error.getCause():error;if(value instanceof WrappedFailure&&value.getCause()!=null)value=value.getCause();String message=SecretRedactor.message(value,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl());Cinemarr.LOGGER.warn("Cinemarr video request failed: {}",message);this.error(player,message);}
    private static String key(UUID session,long generation){return session+":"+generation;}
    private static List<SegmentReference> parsePlaylist(PlexVideoService.MediaPlaylist playlist){List<SegmentReference> values=new ArrayList<>();for(HlsPlaylist.MediaSegment value:playlist.segments())values.add(new SegmentReference(value));return values;}
    private static StreamSelection selection(List<VideoStreamOption> options,int requestedAudio,int requestedSubtitle,boolean defaults)throws IOException{int audio=requestedAudio,subtitle=requestedSubtitle;if(audio<0)for(VideoStreamOption option:options)if(option.kind()==VideoStreamOption.Kind.AUDIO&&option.selected()){audio=option.id();break;}if(defaults&&subtitle<0)for(VideoStreamOption option:options)if(option.kind()==VideoStreamOption.Kind.SUBTITLE&&option.selected()){subtitle=option.id();break;}if(audio>=0&&!contains(options,VideoStreamOption.Kind.AUDIO,audio))throw new IOException("Requested Plex audio stream is unavailable");if(subtitle>=0&&!contains(options,VideoStreamOption.Kind.SUBTITLE,subtitle))throw new IOException("Requested Plex subtitle stream is unavailable");return new StreamSelection(options,audio,subtitle);}
    private static boolean contains(List<VideoStreamOption> options,VideoStreamOption.Kind kind,int id){for(VideoStreamOption option:options)if(option.kind()==kind&&option.id()==id)return true;return false;}
    @Override public void close(){TelevisionLifecycle.listener(null);long now=System.currentTimeMillis();for(String name:sessions.sessionNames()){VideoSessionCoordinator.Snapshot state=sessions.snapshotIfPresent(name,now);if(state!=null)persist(state);}try{sessions.close();}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to close Plex video sessions: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken(),CinemarrSettings.plexUrl()));}workers.shutdownNow();active.clear();startingOptions.remove();playbackOptions.clear();playbackLibraries.clear();queues.clear();clientHealth.clear();transferGrants.clear();restartingSessions.clear();advancingSessions.clear();trackedChunks.clear();viewingSessions.clear();visibleTelevisions.clear();}
    private static final class WrappedFailure extends RuntimeException{WrappedFailure(Throwable cause){super(cause);}}
    private static final class SegmentReference{final HlsPlaylist.MediaSegment source;final long pts,duration;SegmentReference(HlsPlaylist.MediaSegment source){this.source=source;this.pts=source.presentationTimeMs();this.duration=source.durationMs();}}
    private static final class SegmentData{final SegmentReference reference;final byte[] bytes;final String sha;SegmentData(SegmentReference reference,byte[] bytes){this.reference=reference;this.bytes=bytes;this.sha=Hashing.sha256(bytes);}}
    private static final class ActiveMedia{final PlexVideoService plex;final PlexVideoService.VideoSession session;final PlexVideoService.MediaPlaylist playlist;final List<SegmentReference> segments;final RenditionPolicy.Dimensions dimensions;final long durationMs;final List<VideoStreamOption> options;final int audioId,subtitleId;long fetchRetries,fetchFailures;final Map<Integer,SegmentData> cache=new LinkedHashMap<Integer,SegmentData>(16,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Integer,SegmentData> eldest){return size()>16;}};ActiveMedia(PlexVideoService plex,PlexVideoService.VideoSession session,PlexVideoService.MediaPlaylist playlist,List<SegmentReference> segments,RenditionPolicy.Dimensions dimensions,long durationMs,List<VideoStreamOption> options,int audioId,int subtitleId){this.plex=plex;this.session=session;this.playlist=playlist;this.segments=segments;this.dimensions=dimensions;this.durationMs=durationMs;this.options=Collections.unmodifiableList(new ArrayList<>(options));this.audioId=audioId;this.subtitleId=subtitleId;}synchronized int segmentCount(){return segments.size();}synchronized int cachedSegments(){return cache.size();}synchronized long presentationTime(int index){return segments.get(index).pts;}synchronized int segmentAt(long position){int first=0;for(int index=0;index<segments.size();index++){if(segments.get(index).pts>position)break;first=index;}return first;}synchronized List<VideoPackets.SegmentDescriptor> descriptors(int first,int maximum){if(first<0||first>=segments.size())return Collections.emptyList();int end=Math.min(segments.size(),first+maximum);List<VideoPackets.SegmentDescriptor> values=new ArrayList<>();for(int i=first;i<end;i++){SegmentReference ref=segments.get(i);values.add(new VideoPackets.SegmentDescriptor(i,ref.pts,ref.duration,true,0,""));}return values;}synchronized SegmentData segment(int index)throws IOException{SegmentData value=cache.get(index);if(value!=null)return value;SegmentReference ref=segments.get(index);IOException last=null;for(int attempt=0;attempt<3;attempt++){try{value=new SegmentData(ref,plex.fetch(session,playlist,ref.source));cache.put(index,value);return value;}catch(IOException failure){last=failure;if(attempt<2){fetchRetries++;try{Thread.sleep(attempt==0?100L:250L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Interrupted while retrying Plex video segment",interrupted);}}}}fetchFailures++;throw last==null?new IOException("Unable to fetch Plex video segment"):last;}}
    private static final class TransferGrant{final UUID session;final long generation,requestId;final int segment;TransferGrant(VideoPackets.SegmentRequest value){session=value.sessionId();generation=value.generation();requestId=value.requestId();segment=value.segmentIndex();}boolean matches(VideoPackets.SegmentAcknowledgement value){return session.equals(value.sessionId())&&generation==value.generation()&&requestId==value.requestId()&&segment==value.segmentIndex()&&value.receivedThroughChunk()>=0;}}
    private static final class StreamSelection{final List<VideoStreamOption> options;final int audioId,subtitleId;StreamSelection(List<VideoStreamOption> options,int audioId,int subtitleId){this.options=Collections.unmodifiableList(new ArrayList<>(options));this.audioId=audioId;this.subtitleId=subtitleId;}}
    private static final class StartOptions{final RenditionPolicy.Dimensions rendition;final StreamSelection streams;StartOptions(RenditionPolicy.Dimensions rendition,StreamSelection streams){this.rendition=rendition;this.streams=streams;}}
    private static final class HealthRecord{final VideoPackets.ClientHealth value;final long receivedAt;HealthRecord(VideoPackets.ClientHealth value,long receivedAt){this.value=value;this.receivedAt=receivedAt;}}
    private static final class TrackedChunk{final ServerLevel level;final long chunk;TrackedChunk(ServerLevel level,long chunk){this.level=level;this.chunk=chunk;}@Override public boolean equals(Object value){if(this==value)return true;if(!(value instanceof TrackedChunk))return false;TrackedChunk other=(TrackedChunk)value;return level==other.level&&chunk==other.chunk;}@Override public int hashCode(){return 31*System.identityHashCode(level)+Long.hashCode(chunk);}}
}
