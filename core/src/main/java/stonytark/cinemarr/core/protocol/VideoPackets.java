package stonytark.cinemarr.core.protocol;

import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Java-8-compatible protocol-10 models for Cinemarr video browsing, control, queueing, and media relay. */
public final class VideoPackets {
    public enum SessionAction { TUNE, PLAY, PAUSE, RESUME, SEEK, STOP, SET_PRESENTATION, SET_STREAMS, QUEUE, REMOVE_QUEUE, CLEAR_QUEUE, SKIP, CONTINUE_EPISODE }
    public enum SessionStatus { IDLE, PREPARING, BUFFERING, PLAYING, PAUSED, PLEX_OFFLINE, ERROR }

    public static final WireCodec<LibrarySummary> LIBRARY_SUMMARY = new WireCodec<LibrarySummary>() {
        @Override public LibrarySummary decode(WireInput in) {
            return new LibrarySummary(in.readUtf(64), in.readUtf(128), in.readBoolean(), in.readBoolean(), in.readVarInt());
        }
        @Override public void encode(WireOutput out, LibrarySummary value) {
            out.writeUtf(value.id(), 64); out.writeUtf(value.displayName(), 128); out.writeBoolean(value.allowMovies());
            out.writeBoolean(value.allowShows()); out.writeVarInt(value.permissionLevel());
        }
    };

    public static final WireCodec<VideoMediaItem> MEDIA_ITEM = new WireCodec<VideoMediaItem>() {
        @Override public VideoMediaItem decode(WireInput in) { return readItem(in); }
        @Override public void encode(WireOutput out, VideoMediaItem value) { writeItem(out, value); }
    };
    public static final WireCodec<VideoStreamOption> VIDEO_STREAM_OPTION=new WireCodec<VideoStreamOption>(){
        @Override public VideoStreamOption decode(WireInput in){return new VideoStreamOption(readEnum(in,VideoStreamOption.Kind.class),in.readVarInt(),in.readUtf(128),in.readUtf(32),in.readUtf(32),in.readBoolean());}
        @Override public void encode(WireOutput out,VideoStreamOption value){writeEnum(out,value.kind());out.writeVarInt(value.id());out.writeUtf(value.label(),128);out.writeUtf(value.language(),32);out.writeUtf(value.codec(),32);out.writeBoolean(value.selected());}
    };

    public static final WireCodec<LibraryList> LIBRARY_LIST = new WireCodec<LibraryList>() {
        @Override public LibraryList decode(WireInput in) {
            int count = count(in, ProtocolLimits.MAX_VIDEO_LIBRARIES, "video libraries");
            List<LibrarySummary> values = new ArrayList<LibrarySummary>(count);
            for (int index = 0; index < count; index++) values.add(LIBRARY_SUMMARY.decode(in));
            return new LibraryList(values);
        }
        @Override public void encode(WireOutput out, LibraryList value) {
            int count = Math.min(value.libraries().size(), ProtocolLimits.MAX_VIDEO_LIBRARIES);
            out.writeVarInt(count); for (int index = 0; index < count; index++) LIBRARY_SUMMARY.encode(out, value.libraries().get(index));
        }
    };

    public static final WireCodec<BrowseRequest> BROWSE_REQUEST = new WireCodec<BrowseRequest>() {
        @Override public BrowseRequest decode(WireInput in) {
            return new BrowseRequest(in.readUtf(64), in.readUtf(256), in.readUtf(128), in.readVarInt());
        }
        @Override public void encode(WireOutput out, BrowseRequest value) {
            out.writeUtf(value.libraryId(), 64); out.writeUtf(value.parentKey(), 256); out.writeUtf(value.query(), 128); out.writeVarInt(value.page());
        }
    };

    public static final WireCodec<BrowseResults> BROWSE_RESULTS = new WireCodec<BrowseResults>() {
        @Override public BrowseResults decode(WireInput in) {
            String library = in.readUtf(64), parent = in.readUtf(256), query = in.readUtf(128); int page = in.readVarInt();
            boolean more = in.readBoolean(); int count = count(in, ProtocolLimits.MAX_BROWSE_RESULTS, "video results");
            List<VideoMediaItem> values = new ArrayList<VideoMediaItem>(count);
            for (int index = 0; index < count; index++) values.add(readItem(in));
            return new BrowseResults(library, parent, query, page, more, values);
        }
        @Override public void encode(WireOutput out, BrowseResults value) {
            out.writeUtf(value.libraryId(), 64); out.writeUtf(value.parentKey(), 256); out.writeUtf(value.query(), 128);
            out.writeVarInt(value.page()); out.writeBoolean(value.hasMore());
            int count = Math.min(value.items().size(), ProtocolLimits.MAX_BROWSE_RESULTS); out.writeVarInt(count);
            for (int index = 0; index < count; index++) writeItem(out, value.items().get(index));
        }
    };

    public static final WireCodec<SessionCommand> SESSION_COMMAND = new WireCodec<SessionCommand>() {
        @Override public SessionCommand decode(WireInput in) {
            return new SessionCommand(readEnum(in, SessionAction.class), in.readLong(), in.readUtf(64), in.readUtf(256),
                    in.readUtf(64), readEnum(in, PresentationMode.class), in.readVarLong(), in.readVarLong(),
                    in.readVarInt(), in.readVarInt());
        }
        @Override public void encode(WireOutput out, SessionCommand value) {
            writeEnum(out, value.action()); out.writeLong(value.controllerPos()); out.writeUtf(value.libraryId(), 64);
            out.writeUtf(value.itemKey(), 256); out.writeUtf(value.sessionName(), 64); writeEnum(out, value.presentationMode());
            out.writeVarLong(value.expectedGeneration()); out.writeVarLong(value.seekPositionMs());
            out.writeVarInt(value.audioStreamId()); out.writeVarInt(value.subtitleStreamId());
        }
    };

    public static final WireCodec<SessionState> SESSION_STATE = new WireCodec<SessionState>() {
        @Override public SessionState decode(WireInput in) {
            UUID tv = in.readUuid(); long controllerPos = in.readLong(); UUID session = in.readUuid(); long generation = in.readVarLong(); SessionStatus status = readEnum(in, SessionStatus.class);
            boolean hasItem = in.readBoolean(); VideoMediaItem item = hasItem ? readItem(in) : null;
            long position = in.readVarLong(), duration = in.readVarLong(); boolean paused = in.readBoolean();
            PresentationMode mode = readEnum(in, PresentationMode.class); int width = in.readVarInt(), height = in.readVarInt();
            byte[] mask = in.readByteArray(ProtocolLimits.MAX_SCREEN_MASK_BYTES);
            ScreenFacing facing = readEnum(in, ScreenFacing.class); int plane = in.readVarInt(), minimumU = in.readVarInt(), minimumV = in.readVarInt();
            int streamCount=count(in,ProtocolLimits.MAX_VIDEO_STREAM_OPTIONS,"video streams");List<VideoStreamOption> streams=new ArrayList<VideoStreamOption>(streamCount);for(int index=0;index<streamCount;index++)streams.add(VIDEO_STREAM_OPTION.decode(in));
            int selectedAudio=in.readVarInt(),selectedSubtitle=in.readVarInt();
            long epoch = in.readLong(); boolean control = in.readBoolean();
            String message = in.readUtf(256);
            return new SessionState(tv, controllerPos, session, generation, status, item, position, duration, paused, mode, width, height, mask,
                    facing, plane, minimumU, minimumV, streams, selectedAudio, selectedSubtitle, epoch, control, message);
        }
        @Override public void encode(WireOutput out, SessionState value) {
            out.writeUuid(value.televisionId()); out.writeLong(value.controllerPos()); out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); writeEnum(out, value.status());
            out.writeBoolean(value.item() != null); if (value.item() != null) writeItem(out, value.item());
            out.writeVarLong(value.positionMs()); out.writeVarLong(value.durationMs()); out.writeBoolean(value.paused());
            writeEnum(out, value.presentationMode()); out.writeVarInt(value.screenWidth()); out.writeVarInt(value.screenHeight());
            out.writeByteArray(value.visibilityMask(), ProtocolLimits.MAX_SCREEN_MASK_BYTES); writeEnum(out, value.screenFacing());
            out.writeVarInt(value.screenPlane()); out.writeVarInt(value.minimumU()); out.writeVarInt(value.minimumV());int streamCount=Math.min(value.streams().size(),ProtocolLimits.MAX_VIDEO_STREAM_OPTIONS);out.writeVarInt(streamCount);for(int index=0;index<streamCount;index++)VIDEO_STREAM_OPTION.encode(out,value.streams().get(index));out.writeVarInt(value.selectedAudioStreamId());out.writeVarInt(value.selectedSubtitleStreamId());out.writeLong(value.serverEpochMs());
            out.writeBoolean(value.canControl()); out.writeUtf(value.message(), 256);
        }
    };

    public static final WireCodec<TelevisionRemoved> TELEVISION_REMOVED = new WireCodec<TelevisionRemoved>() {
        @Override public TelevisionRemoved decode(WireInput in) { return new TelevisionRemoved(in.readLong()); }
        @Override public void encode(WireOutput out, TelevisionRemoved value) { out.writeLong(value.controllerPos()); }
    };

    public static final WireCodec<SessionQueue> SESSION_QUEUE=new WireCodec<SessionQueue>(){
        @Override public SessionQueue decode(WireInput in){UUID session=in.readUuid();long generation=in.readVarLong();int count=count(in,ProtocolLimits.MAX_VIDEO_QUEUE_ENTRIES,"video queue");List<QueuedVideo> entries=new ArrayList<QueuedVideo>(count);for(int index=0;index<count;index++)entries.add(new QueuedVideo(in.readUtf(64),readItem(in)));return new SessionQueue(session,generation,entries);}
        @Override public void encode(WireOutput out,SessionQueue value){out.writeUuid(value.sessionId());out.writeVarLong(value.generation());int count=Math.min(value.entries().size(),ProtocolLimits.MAX_VIDEO_QUEUE_ENTRIES);out.writeVarInt(count);for(int index=0;index<count;index++){QueuedVideo entry=value.entries().get(index);out.writeUtf(entry.libraryId(),64);writeItem(out,entry.item());}}
    };

    public static final WireCodec<SegmentManifest> SEGMENT_MANIFEST = new WireCodec<SegmentManifest>() {
        @Override public SegmentManifest decode(WireInput in) {
            UUID session = in.readUuid(); long generation = in.readVarLong(); int width = in.readVarInt(), height = in.readVarInt();
            String container = in.readUtf(32), video = in.readUtf(32), audio = in.readUtf(32); long duration = in.readVarLong();
            boolean hasMore = in.readBoolean(); int count = count(in, ProtocolLimits.MAX_VIDEO_SEGMENTS_PER_MANIFEST, "video segments");
            List<SegmentDescriptor> segments = new ArrayList<SegmentDescriptor>(count);
            for (int index = 0; index < count; index++) segments.add(readDescriptor(in));
            return new SegmentManifest(session, generation, width, height, container, video, audio, duration, hasMore, segments);
        }
        @Override public void encode(WireOutput out, SegmentManifest value) {
            out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); out.writeVarInt(value.width()); out.writeVarInt(value.height());
            out.writeUtf(value.container(), 32); out.writeUtf(value.videoCodec(), 32); out.writeUtf(value.audioCodec(), 32); out.writeVarLong(value.durationMs()); out.writeBoolean(value.hasMore());
            int count = Math.min(value.segments().size(), ProtocolLimits.MAX_VIDEO_SEGMENTS_PER_MANIFEST); out.writeVarInt(count);
            for (int index = 0; index < count; index++) writeDescriptor(out, value.segments().get(index));
        }
    };

    public static final WireCodec<SegmentManifestRequest> SEGMENT_MANIFEST_REQUEST = new WireCodec<SegmentManifestRequest>() {
        @Override public SegmentManifestRequest decode(WireInput in) { return new SegmentManifestRequest(in.readUuid(), in.readVarLong(), in.readVarInt()); }
        @Override public void encode(WireOutput out, SegmentManifestRequest value) { out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); out.writeVarInt(value.firstSegmentIndex()); }
    };

    public static final WireCodec<SegmentRequest> SEGMENT_REQUEST = new WireCodec<SegmentRequest>() {
        @Override public SegmentRequest decode(WireInput in) { return new SegmentRequest(in.readUuid(), in.readVarLong(), in.readVarLong(), in.readVarInt(), in.readVarInt(), in.readVarInt()); }
        @Override public void encode(WireOutput out, SegmentRequest value) {
            out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); out.writeVarLong(value.requestId());
            out.writeVarInt(value.segmentIndex()); out.writeVarInt(value.firstChunk()); out.writeVarInt(value.chunkCount());
        }
    };

    public static final WireCodec<SegmentChunk> SEGMENT_CHUNK = new WireCodec<SegmentChunk>() {
        @Override public SegmentChunk decode(WireInput in) {
            return new SegmentChunk(in.readUuid(), in.readVarLong(), in.readVarLong(), in.readVarInt(), in.readVarInt(),
                    in.readVarInt(), in.readVarLong(), in.readBoolean(), in.readUtf(64), in.readByteArray(ProtocolLimits.MAX_VIDEO_CHUNK_BYTES));
        }
        @Override public void encode(WireOutput out, SegmentChunk value) {
            out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); out.writeVarLong(value.requestId());
            out.writeVarInt(value.segmentIndex()); out.writeVarInt(value.chunkIndex()); out.writeVarInt(value.totalChunks());
            out.writeVarLong(value.presentationTimeMs()); out.writeBoolean(value.keyframe()); out.writeUtf(value.segmentSha256(), 64);
            out.writeByteArray(value.data(), ProtocolLimits.MAX_VIDEO_CHUNK_BYTES);
        }
    };

    public static final WireCodec<SegmentAcknowledgement> SEGMENT_ACKNOWLEDGEMENT = new WireCodec<SegmentAcknowledgement>() {
        @Override public SegmentAcknowledgement decode(WireInput in) {
            return new SegmentAcknowledgement(in.readUuid(), in.readVarLong(), in.readVarLong(), in.readVarInt(), in.readVarInt(), in.readVarLong());
        }
        @Override public void encode(WireOutput out, SegmentAcknowledgement value) {
            out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); out.writeVarLong(value.requestId());
            out.writeVarInt(value.segmentIndex()); out.writeVarInt(value.receivedThroughChunk()); out.writeVarLong(value.bufferedMs());
        }
    };

    public static final WireCodec<ClientHealth> CLIENT_HEALTH = new WireCodec<ClientHealth>() {
        @Override public ClientHealth decode(WireInput in) {
            return new ClientHealth(in.readUuid(), in.readVarLong(), in.readUtf(32), in.readVarInt(), in.readVarInt(), in.readVarInt(), in.readVarLong(), in.readVarLong());
        }
        @Override public void encode(WireOutput out, ClientHealth value) {
            out.writeUuid(value.sessionId()); out.writeVarLong(value.generation()); out.writeUtf(value.state(), 32);
            out.writeVarInt(value.decoderRecoveries()); out.writeVarInt(value.videoDrops()); out.writeVarInt(value.audioUnderruns());
            out.writeVarLong(value.bufferedMs()); out.writeVarLong(value.driftMs());
        }
    };

    private static VideoMediaItem readItem(WireInput in) {
        return new VideoMediaItem(readEnum(in, MediaKind.class), in.readUtf(256), in.readUtf(256), in.readUtf(256),
                in.readUtf(32), in.readVarInt(), in.readVarLong(),in.readUtf(256),in.readVarInt());
    }
    private static void writeItem(WireOutput out, VideoMediaItem value) {
        writeEnum(out, value.kind()); out.writeUtf(value.key(), 256); out.writeUtf(value.title(), 256);
        out.writeUtf(value.parentTitle(), 256); out.writeUtf(value.contentRating(), 32); out.writeVarInt(value.index()); out.writeVarLong(value.durationMs());out.writeUtf(value.seriesKey(),256);out.writeVarInt(value.parentIndex());
    }
    private static SegmentDescriptor readDescriptor(WireInput in) {
        return new SegmentDescriptor(in.readVarInt(), in.readVarLong(), in.readVarLong(), in.readBoolean(), in.readVarInt(), in.readUtf(64));
    }
    private static void writeDescriptor(WireOutput out, SegmentDescriptor value) {
        out.writeVarInt(value.index()); out.writeVarLong(value.presentationTimeMs()); out.writeVarLong(value.durationMs());
        out.writeBoolean(value.keyframe()); out.writeVarInt(value.byteLength()); out.writeUtf(value.sha256(), 64);
    }
    private static int count(WireInput in, int maximum, String label) {
        int value = in.readVarInt(); if (value < 0 || value > maximum) throw new ProtocolException("Invalid " + label + " count: " + value); return value;
    }
    private static <T extends Enum<T>> T readEnum(WireInput in, Class<T> type) {
        int ordinal = in.readVarInt(); T[] values = type.getEnumConstants();
        if (ordinal < 0 || ordinal >= values.length) throw new ProtocolException("Invalid " + type.getSimpleName()); return values[ordinal];
    }
    private static void writeEnum(WireOutput out, Enum<?> value) { out.writeVarInt(value.ordinal()); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }

    public static final class LibrarySummary implements CinemarrMessage {
        private final String id, displayName; private final boolean allowMovies, allowShows; private final int permissionLevel;
        public LibrarySummary(String id, String displayName, boolean allowMovies, boolean allowShows, int permissionLevel) {
            this.id=safe(id); this.displayName=safe(displayName); this.allowMovies=allowMovies; this.allowShows=allowShows; this.permissionLevel=permissionLevel;
        }
        public String id(){return id;} public String displayName(){return displayName;} public boolean allowMovies(){return allowMovies;}
        public boolean allowShows(){return allowShows;} public int permissionLevel(){return permissionLevel;}
    }
    public static final class LibraryList implements CinemarrMessage {
        private final List<LibrarySummary> libraries; public LibraryList(List<LibrarySummary> values){libraries=immutable(values);} public List<LibrarySummary> libraries(){return libraries;}
    }
    public static final class BrowseRequest implements CinemarrMessage {
        private final String libraryId,parentKey,query; private final int page;
        public BrowseRequest(String libraryId,String parentKey,String query,int page){this.libraryId=safe(libraryId);this.parentKey=safe(parentKey);this.query=safe(query);this.page=page;}
        public String libraryId(){return libraryId;} public String parentKey(){return parentKey;} public String query(){return query;} public int page(){return page;}
    }
    public static final class BrowseResults implements CinemarrMessage {
        private final String libraryId,parentKey,query; private final int page; private final boolean hasMore; private final List<VideoMediaItem> items;
        public BrowseResults(String libraryId,String parentKey,String query,int page,boolean hasMore,List<VideoMediaItem> items){this.libraryId=safe(libraryId);this.parentKey=safe(parentKey);this.query=safe(query);this.page=page;this.hasMore=hasMore;this.items=immutable(items);}
        public String libraryId(){return libraryId;} public String parentKey(){return parentKey;} public String query(){return query;} public int page(){return page;} public boolean hasMore(){return hasMore;} public List<VideoMediaItem> items(){return items;}
    }
    public static final class SessionCommand implements CinemarrMessage {
        private final SessionAction action; private final long controllerPos; private final String libraryId,itemKey,sessionName; private final PresentationMode mode;
        private final long expectedGeneration,seekPositionMs; private final int audioStreamId,subtitleStreamId;
        public SessionCommand(SessionAction action,long controllerPos,String libraryId,String itemKey,String sessionName,PresentationMode mode,long expectedGeneration,long seekPositionMs,int audioStreamId,int subtitleStreamId){this.action=action;this.controllerPos=controllerPos;this.libraryId=safe(libraryId);this.itemKey=safe(itemKey);this.sessionName=safe(sessionName);this.mode=mode;this.expectedGeneration=expectedGeneration;this.seekPositionMs=seekPositionMs;this.audioStreamId=audioStreamId;this.subtitleStreamId=subtitleStreamId;}
        public SessionAction action(){return action;} public long controllerPos(){return controllerPos;} public String libraryId(){return libraryId;} public String itemKey(){return itemKey;} public String sessionName(){return sessionName;} public PresentationMode presentationMode(){return mode;} public long expectedGeneration(){return expectedGeneration;} public long seekPositionMs(){return seekPositionMs;} public int audioStreamId(){return audioStreamId;} public int subtitleStreamId(){return subtitleStreamId;}
    }
    public static final class SessionState implements CinemarrMessage {
        private final UUID televisionId,sessionId; private final long controllerPos,generation; private final SessionStatus status; private final VideoMediaItem item;
        private final long positionMs,durationMs; private final boolean paused; private final PresentationMode mode; private final int width,height; private final byte[] mask;
        private final ScreenFacing facing; private final int plane,minimumU,minimumV; private final long serverEpochMs; private final boolean canControl; private final String message;
        private final List<VideoStreamOption> streams;private final int selectedAudioStreamId,selectedSubtitleStreamId;
        public SessionState(UUID tv,long controllerPos,UUID session,long generation,SessionStatus status,VideoMediaItem item,long position,long duration,boolean paused,PresentationMode mode,int width,int height,byte[] mask,ScreenFacing facing,int plane,int minimumU,int minimumV,List<VideoStreamOption> streams,int selectedAudioStreamId,int selectedSubtitleStreamId,long epoch,boolean control,String message){this.televisionId=tv;this.controllerPos=controllerPos;this.sessionId=session;this.generation=generation;this.status=status;this.item=item;this.positionMs=position;this.durationMs=duration;this.paused=paused;this.mode=mode;this.width=width;this.height=height;this.mask=mask==null?new byte[0]:mask.clone();this.facing=facing;this.plane=plane;this.minimumU=minimumU;this.minimumV=minimumV;this.streams=immutable(streams);this.selectedAudioStreamId=selectedAudioStreamId;this.selectedSubtitleStreamId=selectedSubtitleStreamId;this.serverEpochMs=epoch;this.canControl=control;this.message=safe(message);}
        public UUID televisionId(){return televisionId;} public long controllerPos(){return controllerPos;} public UUID sessionId(){return sessionId;} public long generation(){return generation;} public SessionStatus status(){return status;} public VideoMediaItem item(){return item;} public long positionMs(){return positionMs;} public long durationMs(){return durationMs;} public boolean paused(){return paused;} public PresentationMode presentationMode(){return mode;} public int screenWidth(){return width;} public int screenHeight(){return height;} public byte[] visibilityMask(){return mask.clone();} public ScreenFacing screenFacing(){return facing;} public int screenPlane(){return plane;} public int minimumU(){return minimumU;} public int minimumV(){return minimumV;} public List<VideoStreamOption> streams(){return streams;}public int selectedAudioStreamId(){return selectedAudioStreamId;}public int selectedSubtitleStreamId(){return selectedSubtitleStreamId;} public long serverEpochMs(){return serverEpochMs;} public boolean canControl(){return canControl;} public String message(){return message;}
    }
    public static final class TelevisionRemoved implements CinemarrMessage {
        private final long controllerPos;
        public TelevisionRemoved(long controllerPos){this.controllerPos=controllerPos;}
        public long controllerPos(){return controllerPos;}
    }
    public static final class SessionQueue implements CinemarrMessage{
        private final UUID sessionId;private final long generation;private final List<QueuedVideo> entries;
        public SessionQueue(UUID sessionId,long generation,List<QueuedVideo> entries){this.sessionId=sessionId;this.generation=generation;this.entries=immutable(entries);}
        public UUID sessionId(){return sessionId;}public long generation(){return generation;}public List<QueuedVideo> entries(){return entries;}
    }
    public static final class SegmentDescriptor {
        private final int index,byteLength; private final long pts,duration; private final boolean keyframe; private final String sha;
        public SegmentDescriptor(int index,long pts,long duration,boolean keyframe,int bytes,String sha){this.index=index;this.pts=pts;this.duration=duration;this.keyframe=keyframe;this.byteLength=bytes;this.sha=safe(sha);}
        public int index(){return index;} public long presentationTimeMs(){return pts;} public long durationMs(){return duration;} public boolean keyframe(){return keyframe;} public int byteLength(){return byteLength;} public String sha256(){return sha;}
    }
    public static final class SegmentManifest implements CinemarrMessage {
        private final UUID session; private final long generation,duration; private final int width,height; private final String container,video,audio; private final boolean hasMore; private final List<SegmentDescriptor> segments;
        public SegmentManifest(UUID session,long generation,int width,int height,String container,String video,String audio,long duration,boolean hasMore,List<SegmentDescriptor> segments){this.session=session;this.generation=generation;this.width=width;this.height=height;this.container=safe(container);this.video=safe(video);this.audio=safe(audio);this.duration=duration;this.hasMore=hasMore;this.segments=immutable(segments);}
        public UUID sessionId(){return session;} public long generation(){return generation;} public int width(){return width;} public int height(){return height;} public String container(){return container;} public String videoCodec(){return video;} public String audioCodec(){return audio;} public long durationMs(){return duration;} public boolean hasMore(){return hasMore;} public List<SegmentDescriptor> segments(){return segments;}
    }
    public static final class SegmentManifestRequest implements CinemarrMessage { private final UUID session;private final long generation;private final int first;public SegmentManifestRequest(UUID session,long generation,int first){this.session=session;this.generation=generation;this.first=first;}public UUID sessionId(){return session;}public long generation(){return generation;}public int firstSegmentIndex(){return first;} }
    public static final class SegmentRequest implements CinemarrMessage { private final UUID session; private final long generation,request; private final int segment,first,count; public SegmentRequest(UUID s,long g,long r,int seg,int first,int count){session=s;generation=g;request=r;segment=seg;this.first=first;this.count=count;} public UUID sessionId(){return session;} public long generation(){return generation;} public long requestId(){return request;} public int segmentIndex(){return segment;} public int firstChunk(){return first;} public int chunkCount(){return count;} }
    public static final class SegmentChunk implements CinemarrMessage { private final UUID session; private final long generation,request,pts; private final int segment,chunk,total; private final boolean keyframe; private final String sha; private final byte[] data; public SegmentChunk(UUID s,long g,long r,int seg,int chunk,int total,long pts,boolean key,String sha,byte[] data){session=s;generation=g;request=r;segment=seg;this.chunk=chunk;this.total=total;this.pts=pts;keyframe=key;this.sha=safe(sha);this.data=data==null?new byte[0]:data.clone();} public UUID sessionId(){return session;} public long generation(){return generation;} public long requestId(){return request;} public int segmentIndex(){return segment;} public int chunkIndex(){return chunk;} public int totalChunks(){return total;} public long presentationTimeMs(){return pts;} public boolean keyframe(){return keyframe;} public String segmentSha256(){return sha;} public byte[] data(){return data.clone();} }
    public static final class SegmentAcknowledgement implements CinemarrMessage { private final UUID session; private final long generation,request,buffered; private final int segment,through; public SegmentAcknowledgement(UUID s,long g,long r,int seg,int through,long buffered){session=s;generation=g;request=r;segment=seg;this.through=through;this.buffered=buffered;} public UUID sessionId(){return session;} public long generation(){return generation;} public long requestId(){return request;} public int segmentIndex(){return segment;} public int receivedThroughChunk(){return through;} public long bufferedMs(){return buffered;} }
    public static final class ClientHealth implements CinemarrMessage { private final UUID session; private final long generation,buffered,drift; private final String state; private final int recoveries,drops,underruns; public ClientHealth(UUID s,long g,String state,int recoveries,int drops,int underruns,long buffered,long drift){session=s;generation=g;this.state=safe(state);this.recoveries=recoveries;this.drops=drops;this.underruns=underruns;this.buffered=buffered;this.drift=drift;} public UUID sessionId(){return session;} public long generation(){return generation;} public String state(){return state;} public int decoderRecoveries(){return recoveries;} public int videoDrops(){return drops;} public int audioUnderruns(){return underruns;} public long bufferedMs(){return buffered;} public long driftMs(){return drift;} }
    private VideoPackets() {}
}
