package stonytark.cinemarr.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.server.PlexVideoService;
import stonytark.cinemarr.core.server.SecretRedactor;
import stonytark.cinemarr.core.server.SlidingWindowRateLimiter;
import stonytark.cinemarr.core.server.VideoSessionCoordinator;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.RenditionPolicy;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.VideoPayloads;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NeoForge server boundary for video browse/control and pull-based HLS segment relay. */
public final class ServerVideoManager implements AutoCloseable {
    private static final int PAGE_SIZE = 20;
    private final MinecraftServer server;
    private final PlexVideoService plex;
    private final List<PlexVideoService.ResolvedLibrary> libraries;
    private final ExecutorService workers = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "Cinemarr video worker"); thread.setDaemon(true); return thread;
    });
    private final SlidingWindowRateLimiter browseLimiter = new SlidingWindowRateLimiter();
    private final SlidingWindowRateLimiter segmentLimiter = new SlidingWindowRateLimiter();
    private final Map<String, ActiveMedia> active = new ConcurrentHashMap<>();
    private final Map<UUID, RenditionPolicy.Dimensions> requestedRenditions = new ConcurrentHashMap<>();
    private final VideoSessionCoordinator sessions;

    public ServerVideoManager(MinecraftServer server, PlexVideoService plex, List<PlexVideoService.ResolvedLibrary> libraries) {
        this.server = server; this.plex = plex; this.libraries = Collections.unmodifiableList(new ArrayList<>(libraries));
        this.sessions = new VideoSessionCoordinator(CinemarrSettings.maximumActiveTelevisions(),
                CinemarrSettings.inactiveSessionGraceSeconds() * 1000L, this::startMedia);
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
            CinemarrWorldScreens screenData=CinemarrWorldScreens.get(player.serverLevel());
            screenData.updateSession(controller,tuned.name());
            if(command.action()==VideoPackets.SessionAction.SET_PRESENTATION)screenData.updatePresentation(controller,command.presentationMode());
            PresentationMode presentation=command.action()==VideoPackets.SessionAction.SET_PRESENTATION?command.presentationMode():television.presentationMode();
            if (command.action() == VideoPackets.SessionAction.TUNE) { sendState(player, television, tuned, presentation, "Tuned"); return; }
            if (command.expectedGeneration() != tuned.generation()) { error(player, "TV state changed; refresh before controlling it"); return; }
            switch (command.action()) {
                case PAUSE: sessions.pause(tuned.name(), System.currentTimeMillis()); sendState(player, television, sessions.snapshot(tuned.name(), System.currentTimeMillis()), command.presentationMode(), "Paused"); break;
                case RESUME: sessions.resume(tuned.name(), System.currentTimeMillis()); sendState(player, television, sessions.snapshot(tuned.name(), System.currentTimeMillis()), command.presentationMode(), "Playing"); break;
                case SEEK: asyncSeek(player, television, tuned, command); break;
                case PLAY: asyncPlay(player, television, tuned, command); break;
                case STOP: sessions.untune(television.id()); sendIdle(player, television, "Stopped"); break;
                case SET_PRESENTATION: sendState(player, television, tuned, presentation, "Presentation updated"); break;
                case SET_STREAMS: asyncPlay(player, television, tuned, command); break;
                default: error(player, "Unsupported TV action");
            }
        } catch (Exception failure) { failure(player, failure); }
    }

    private void asyncPlay(ServerPlayer player, CinemarrWorldScreens.Television television,
                           VideoSessionCoordinator.Snapshot tuned, VideoPackets.SessionCommand command) {
        PlexVideoService.ResolvedLibrary library = library(command.libraryId(), player); if (library == null) return;
        int playerPermission=permission(player);
        requestedRenditions.put(tuned.id(), RenditionPolicy.choose(television.width(), television.height(), 1920, 1080, 1920, 1080));
        CompletableFuture.supplyAsync(() -> {
            try {
                VideoMediaItem item = plex.metadata(command.itemKey());
                if (!library.rule().allows(item, playerPermission)) throw new IOException("Video item is not allowed by this library policy");
                return sessions.play(tuned.name(), item, command.seekPositionMs(), System.currentTimeMillis());
            } catch (IOException failure) { throw new WrappedFailure(failure); }
        }, workers).whenComplete((state, failure) -> server.execute(() -> {
            if (failure != null) { failure(player, failure); return; }
            sendState(player, television, state, command.presentationMode(), "Buffering");
            sendManifest(player, state);
        }));
    }

    private void asyncSeek(ServerPlayer player, CinemarrWorldScreens.Television television,
                           VideoSessionCoordinator.Snapshot tuned, VideoPackets.SessionCommand command) {
        requestedRenditions.put(tuned.id(), RenditionPolicy.choose(television.width(), television.height(), 1920, 1080, 1920, 1080));
        CompletableFuture.runAsync(() -> { try { sessions.seek(tuned.name(), command.seekPositionMs(), System.currentTimeMillis()); }
            catch (IOException failure) { throw new WrappedFailure(failure); } }, workers).whenComplete((unused, failure) -> server.execute(() -> {
            if (failure != null) { failure(player, failure); return; }
            VideoSessionCoordinator.Snapshot state=sessions.snapshot(tuned.name(),System.currentTimeMillis()); sendState(player,television,state,command.presentationMode(),"Buffering"); sendManifest(player,state);
        }));
    }

    public void segments(ServerPlayer player, VideoPackets.SegmentRequest request) {
        if (request.chunkCount() < 1 || request.chunkCount() > 8 || request.firstChunk() < 0
                || !segmentLimiter.allow(player.getUUID(), 40, System.currentTimeMillis())) { error(player, "Invalid or excessive segment request"); return; }
        ActiveMedia media=active.get(key(request.sessionId(),request.generation()));
        if (media==null || request.segmentIndex()<0 || request.segmentIndex()>=media.segments.size()) { error(player,"Video segment is no longer available"); return; }
        CompletableFuture.supplyAsync(() -> { try { return media.segment(request.segmentIndex()); } catch(IOException failure){throw new WrappedFailure(failure);} },workers)
                .whenComplete((segment,failure)->server.execute(()->{
                    if(failure!=null){failure(player,failure);return;} int total=(segment.bytes.length+ProtocolLimits.MAX_VIDEO_CHUNK_BYTES-1)/ProtocolLimits.MAX_VIDEO_CHUNK_BYTES;
                    int end=Math.min(total,request.firstChunk()+request.chunkCount());
                    for(int index=request.firstChunk();index<end;index++){int from=index*ProtocolLimits.MAX_VIDEO_CHUNK_BYTES,to=Math.min(segment.bytes.length,from+ProtocolLimits.MAX_VIDEO_CHUNK_BYTES);
                        CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SegmentChunk(new VideoPackets.SegmentChunk(request.sessionId(),request.generation(),request.requestId(),request.segmentIndex(),index,total,segment.reference.pts,true,segment.sha,Arrays.copyOfRange(segment.bytes,from,to))));}
                }));
    }

    public void acknowledge(ServerPlayer player, VideoPackets.SegmentAcknowledgement value) {
        if(value.bufferedMs()<0||value.bufferedMs()>30_000) error(player,"Invalid video buffer acknowledgement");
    }
    public void health(ServerPlayer player, VideoPackets.ClientHealth value) {
        if(Math.abs(value.driftMs())>30_000||value.bufferedMs()<0||value.bufferedMs()>60_000) error(player,"Invalid video health report");
    }
    public void tick(){try{sessions.tick(System.currentTimeMillis());}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to stop inactive Plex video session: {}", SecretRedactor.message(failure,CinemarrSettings.plexToken()));}}

    private VideoSessionCoordinator.MediaHandle startMedia(UUID sessionId,long generation,VideoMediaItem item,long offset) throws IOException {
        RenditionPolicy.Dimensions dimensions=requestedRenditions.remove(sessionId); if(dimensions==null) dimensions=RenditionPolicy.choose(1280,720,1920,1080,1920,1080);
        PlexVideoService.VideoSession plexSession=plex.start(item,dimensions,offset,null,null);
        try {
            String variant=reference(plexSession.playlist()); byte[] mediaPlaylist=plex.fetch(plexSession,variant);
            List<SegmentReference> references=parsePlaylist(new String(mediaPlaylist,StandardCharsets.UTF_8));
            ActiveMedia media=new ActiveMedia(plex,plexSession,variant,references,dimensions); String key=key(sessionId,generation); active.put(key,media);
            return ()->{active.remove(key);plex.stop(plexSession);};
        } catch(IOException|RuntimeException failure){try{plex.stop(plexSession);}catch(IOException ignored){}throw failure;}
    }
    private void sendManifest(ServerPlayer player,VideoSessionCoordinator.Snapshot state){ActiveMedia media=active.get(key(state.id(),state.generation()));if(media==null)return;List<VideoPackets.SegmentDescriptor> descriptors=new ArrayList<>();for(int i=0;i<media.segments.size()&&i<ProtocolLimits.MAX_VIDEO_SEGMENTS_PER_MANIFEST;i++){SegmentReference ref=media.segments.get(i);descriptors.add(new VideoPackets.SegmentDescriptor(i,ref.pts,ref.duration,true,0,""));}CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SegmentManifest(new VideoPackets.SegmentManifest(state.id(),state.generation(),media.dimensions.width(),media.dimensions.height(),"mpegts","h264","aac",state.item()==null?0:state.item().durationMs(),descriptors)));}
    private void sendState(ServerPlayer player,CinemarrWorldScreens.Television tv,VideoSessionCoordinator.Snapshot state,PresentationMode mode,String message){VideoPackets.SessionStatus status=state.item()==null?VideoPackets.SessionStatus.IDLE:state.paused()?VideoPackets.SessionStatus.PAUSED:state.transcoding()?VideoPackets.SessionStatus.BUFFERING:VideoPackets.SessionStatus.IDLE;CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionState(new VideoPackets.SessionState(tv.id(),state.id(),state.generation(),status,state.item(),state.positionMs(),state.item()==null?0:state.item().durationMs(),state.paused(),mode,tv.width(),tv.height(),tv.mask(),System.currentTimeMillis(),true,message)));}
    private void sendIdle(ServerPlayer player,CinemarrWorldScreens.Television tv,String message){CinemarrNetwork.sendToPlayer(player,new VideoPayloads.SessionState(new VideoPackets.SessionState(tv.id(),new UUID(0,0),0,VideoPackets.SessionStatus.IDLE,null,0,0,false,tv.presentationMode(),tv.width(),tv.height(),tv.mask(),System.currentTimeMillis(),true,message)));}
    private PlexVideoService.ResolvedLibrary library(String id,ServerPlayer player){for(PlexVideoService.ResolvedLibrary value:libraries)if(value.rule().id().equals(id)){if(permission(player)<value.rule().permissionLevel()){error(player,"Library permission denied");return null;}return value;}error(player,"Unknown video library");return null;}
    private static int permission(ServerPlayer player){for(int level=4;level>=0;level--)if(player.createCommandSourceStack().hasPermission(level))return level;return 0;}
    private void error(ServerPlayer player,String message){CinemarrNetwork.sendToPlayer(player,new CinemarrPayloads.ErrorMessage(CinemarrPayloads.ErrorCode.INVALID_REQUEST,message));}
    private void failure(ServerPlayer player,Throwable error){Throwable value=error instanceof java.util.concurrent.CompletionException&&error.getCause()!=null?error.getCause():error;if(value instanceof WrappedFailure&&value.getCause()!=null)value=value.getCause();String message=SecretRedactor.message(value,CinemarrSettings.plexToken());Cinemarr.LOGGER.warn("Cinemarr video request failed: {}",message);this.error(player,message);}
    private static String key(UUID session,long generation){return session+":"+generation;}
    private static String reference(String playlist){for(String line:playlist.split("\\r?\\n")){String value=line.trim();if(!value.isEmpty()&&!value.startsWith("#"))return value;}throw new IllegalArgumentException("Plex playlist has no media rendition");}
    private static List<SegmentReference> parsePlaylist(String playlist){List<SegmentReference> values=new ArrayList<>();long pts=0,duration=0;for(String line:playlist.split("\\r?\\n")){String value=line.trim();if(value.startsWith("#EXTINF:")){String seconds=value.substring(8).replace(",","");duration=(long)(Double.parseDouble(seconds)*1000);}else if(!value.isEmpty()&&!value.startsWith("#")){values.add(new SegmentReference(value,pts,duration));pts+=duration;duration=0;}}if(values.isEmpty())throw new IllegalArgumentException("Plex media playlist has no segments");return values;}
    @Override public void close(){try{sessions.close();}catch(IOException failure){Cinemarr.LOGGER.warn("Unable to close Plex video sessions: {}",SecretRedactor.message(failure,CinemarrSettings.plexToken()));}workers.shutdownNow();active.clear();}
    private static final class WrappedFailure extends RuntimeException{WrappedFailure(Throwable cause){super(cause);}}
    private static final class SegmentReference{final String uri;final long pts,duration;SegmentReference(String uri,long pts,long duration){this.uri=uri;this.pts=pts;this.duration=duration;}}
    private static final class SegmentData{final SegmentReference reference;final byte[] bytes;final String sha;SegmentData(SegmentReference reference,byte[] bytes){this.reference=reference;this.bytes=bytes;this.sha=Hashing.sha256(bytes);}}
    private static final class ActiveMedia{final PlexVideoService plex;final PlexVideoService.VideoSession session;final String variant;final List<SegmentReference> segments;final RenditionPolicy.Dimensions dimensions;final Map<Integer,SegmentData> cache=new LinkedHashMap<Integer,SegmentData>(16,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<Integer,SegmentData> eldest){return size()>16;}};ActiveMedia(PlexVideoService plex,PlexVideoService.VideoSession session,String variant,List<SegmentReference> segments,RenditionPolicy.Dimensions dimensions){this.plex=plex;this.session=session;this.variant=variant;this.segments=segments;this.dimensions=dimensions;}synchronized SegmentData segment(int index)throws IOException{SegmentData value=cache.get(index);if(value==null){SegmentReference ref=segments.get(index);value=new SegmentData(ref,plex.fetch(session,variant,ref.uri));cache.put(index,value);}return value;}}
}
