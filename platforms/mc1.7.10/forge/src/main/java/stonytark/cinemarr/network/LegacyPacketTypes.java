package stonytark.cinemarr.network;

import stonytark.cinemarr.core.protocol.WireCodec;
import stonytark.cinemarr.core.protocol.WireInput;
import stonytark.cinemarr.core.protocol.WireOutput;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.ProtocolCapabilities;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable protocol-10 identifiers for connection management and television playback. */
public final class LegacyPacketTypes {
    public enum Direction { CLIENTBOUND, SERVERBOUND }

    public static final class EmptyRequest {
        public static final EmptyRequest INSTANCE = new EmptyRequest();
        private EmptyRequest() {}
    }
    public static final class ClientHello {
        private final int protocolVersion; private final long featureBits; private final int maxChunkBytes,maxTransferWindow,healthIntervalMs;
        public ClientHello(int protocolVersion) { this(protocolVersion,ProtocolCapabilities.REQUIRED_FEATURES,ProtocolCapabilities.currentOffer().maxChunkBytes(),ProtocolCapabilities.MAX_TRANSFER_WINDOW,ProtocolCapabilities.HEALTH_INTERVAL_MS); }
        public ClientHello(int protocolVersion,long featureBits,int maxChunkBytes,int maxTransferWindow,int healthIntervalMs) { this.protocolVersion=protocolVersion;this.featureBits=featureBits;this.maxChunkBytes=maxChunkBytes;this.maxTransferWindow=maxTransferWindow;this.healthIntervalMs=healthIntervalMs; }
        public int protocolVersion() { return protocolVersion; }
        public boolean valid(){try{ProtocolCapabilities.negotiate(protocolVersion,featureBits,maxChunkBytes,maxTransferWindow,healthIntervalMs);return true;}catch(IllegalArgumentException invalid){return false;}}
        public long featureBits(){return featureBits;} public int maxChunkBytes(){return maxChunkBytes;} public int maxTransferWindow(){return maxTransferWindow;} public int healthIntervalMs(){return healthIntervalMs;}
    }
    public static final class ServerHello {
        private final int protocolVersion; private final long featureBits; private final int maxChunkBytes,maxTransferWindow,healthIntervalMs; private final long serverEpochMs;
        public ServerHello(int protocolVersion,long serverEpochMs){this(protocolVersion,ProtocolCapabilities.REQUIRED_FEATURES,ProtocolCapabilities.currentOffer().maxChunkBytes(),ProtocolCapabilities.MAX_TRANSFER_WINDOW,ProtocolCapabilities.HEALTH_INTERVAL_MS,serverEpochMs);}
        public ServerHello(int protocolVersion,long featureBits,int maxChunkBytes,int maxTransferWindow,int healthIntervalMs,long serverEpochMs){this.protocolVersion=protocolVersion;this.featureBits=featureBits;this.maxChunkBytes=maxChunkBytes;this.maxTransferWindow=maxTransferWindow;this.healthIntervalMs=healthIntervalMs;this.serverEpochMs=serverEpochMs;}
        public int protocolVersion() { return protocolVersion; }
        public boolean valid(){try{ProtocolCapabilities.negotiate(protocolVersion,featureBits,maxChunkBytes,maxTransferWindow,healthIntervalMs);return true;}catch(IllegalArgumentException invalid){return false;}}
        public long featureBits(){return featureBits;} public int maxChunkBytes(){return maxChunkBytes;} public int maxTransferWindow(){return maxTransferWindow;} public int healthIntervalMs(){return healthIntervalMs;}
        public long serverEpochMs() { return serverEpochMs; }
    }
    public static final class TimeSyncRequest {
        private final long nonce; private final long clientSentEpochMs;
        public TimeSyncRequest(long nonce, long clientSentEpochMs) { this.nonce = nonce; this.clientSentEpochMs = clientSentEpochMs; }
        public long nonce() { return nonce; }
        public long clientSentEpochMs() { return clientSentEpochMs; }
    }
    public static final class TimeSyncResponse {
        private final long nonce; private final long clientSentEpochMs; private final long serverEpochMs;
        public TimeSyncResponse(long nonce, long clientSentEpochMs, long serverEpochMs) { this.nonce = nonce; this.clientSentEpochMs = clientSentEpochMs; this.serverEpochMs = serverEpochMs; }
        public long nonce() { return nonce; }
        public long clientSentEpochMs() { return clientSentEpochMs; }
        public long serverEpochMs() { return serverEpochMs; }
    }
    public static final class ErrorMessage {
        private final String message;
        public ErrorMessage(String message) { this.message = message == null ? "" : message.substring(0, Math.min(message.length(), 256)); }
        public String message() { return message; }
    }
    public static final class OpenVideoScreen {
        private final long controllerPos;
        public OpenVideoScreen(long controllerPos) { this.controllerPos = controllerPos; }
        public long controllerPos() { return controllerPos; }
    }

    public static final class Type<T> {
        private final int id; private final String name; private final Direction direction; private final WireCodec<T> codec;
        private Type(int id, String name, Direction direction, WireCodec<T> codec) { this.id = id; this.name = name; this.direction = direction; this.codec = codec; }
        public int id() { return id; }
        public String name() { return name; }
        public Direction direction() { return direction; }
        public WireCodec<T> codec() { return codec; }
    }

    private static final WireCodec<EmptyRequest> EMPTY = new WireCodec<EmptyRequest>() {
        @Override public EmptyRequest decode(WireInput input) { return EmptyRequest.INSTANCE; }
        @Override public void encode(WireOutput output, EmptyRequest value) {}
    };
    private static final WireCodec<ClientHello> CLIENT_HELLO_CODEC = new WireCodec<ClientHello>() {
        @Override public ClientHello decode(WireInput input) { return new ClientHello(input.readVarInt(),input.readLong(),input.readVarInt(),input.readVarInt(),input.readVarInt()); }
        @Override public void encode(WireOutput output, ClientHello value) { output.writeVarInt(value.protocolVersion());output.writeLong(value.featureBits());output.writeVarInt(value.maxChunkBytes());output.writeVarInt(value.maxTransferWindow());output.writeVarInt(value.healthIntervalMs()); }
    };
    private static final WireCodec<ServerHello> SERVER_HELLO_CODEC = new WireCodec<ServerHello>() {
        @Override public ServerHello decode(WireInput input) { return new ServerHello(input.readVarInt(),input.readLong(),input.readVarInt(),input.readVarInt(),input.readVarInt(),input.readLong()); }
        @Override public void encode(WireOutput output, ServerHello value) { output.writeVarInt(value.protocolVersion());output.writeLong(value.featureBits());output.writeVarInt(value.maxChunkBytes());output.writeVarInt(value.maxTransferWindow());output.writeVarInt(value.healthIntervalMs());output.writeLong(value.serverEpochMs()); }
    };
    private static final WireCodec<TimeSyncRequest> TIME_REQUEST_CODEC = new WireCodec<TimeSyncRequest>() {
        @Override public TimeSyncRequest decode(WireInput input) { return new TimeSyncRequest(input.readLong(), input.readLong()); }
        @Override public void encode(WireOutput output, TimeSyncRequest value) { output.writeLong(value.nonce()); output.writeLong(value.clientSentEpochMs()); }
    };
    private static final WireCodec<TimeSyncResponse> TIME_RESPONSE_CODEC = new WireCodec<TimeSyncResponse>() {
        @Override public TimeSyncResponse decode(WireInput input) { return new TimeSyncResponse(input.readLong(), input.readLong(), input.readLong()); }
        @Override public void encode(WireOutput output, TimeSyncResponse value) { output.writeLong(value.nonce()); output.writeLong(value.clientSentEpochMs()); output.writeLong(value.serverEpochMs()); }
    };
    private static final WireCodec<ErrorMessage> ERROR_CODEC = new WireCodec<ErrorMessage>() {
        @Override public ErrorMessage decode(WireInput input) { return new ErrorMessage(input.readUtf(256)); }
        @Override public void encode(WireOutput output, ErrorMessage value) { output.writeUtf(value.message(), 256); }
    };
    private static final WireCodec<OpenVideoScreen> OPEN_VIDEO_SCREEN_CODEC = new WireCodec<OpenVideoScreen>() {
        @Override public OpenVideoScreen decode(WireInput input) { return new OpenVideoScreen(input.readLong()); }
        @Override public void encode(WireOutput output, OpenVideoScreen value) { output.writeLong(value.controllerPos()); }
    };

    private static final Map<Integer, Type<?>> MUTABLE_TYPES = new LinkedHashMap<Integer, Type<?>>();
    public static final Type<ClientHello> CLIENT_HELLO = type(0, "client_hello", Direction.SERVERBOUND, CLIENT_HELLO_CODEC);
    public static final Type<ServerHello> SERVER_HELLO = type(1, "server_hello", Direction.CLIENTBOUND, SERVER_HELLO_CODEC);
    public static final Type<TimeSyncRequest> TIME_SYNC_REQUEST = type(2, "time_sync_request", Direction.SERVERBOUND, TIME_REQUEST_CODEC);
    public static final Type<TimeSyncResponse> TIME_SYNC_RESPONSE = type(3, "time_sync_response", Direction.CLIENTBOUND, TIME_RESPONSE_CODEC);
    public static final Type<ErrorMessage> ERROR = type(4, "error", Direction.CLIENTBOUND, ERROR_CODEC);
    public static final Type<OpenVideoScreen> OPEN_VIDEO_SCREEN = type(5, "open_video_screen", Direction.CLIENTBOUND, OPEN_VIDEO_SCREEN_CODEC);
    public static final Type<EmptyRequest> VIDEO_LIBRARY_LIST_REQUEST = type(6, "video_library_list_request", Direction.SERVERBOUND, EMPTY);
    public static final Type<VideoPackets.LibraryList> VIDEO_LIBRARY_LIST = type(7, "video_library_list", Direction.CLIENTBOUND, VideoPackets.LIBRARY_LIST);
    public static final Type<VideoPackets.BrowseRequest> VIDEO_BROWSE_REQUEST = type(8, "video_browse_request", Direction.SERVERBOUND, VideoPackets.BROWSE_REQUEST);
    public static final Type<VideoPackets.BrowseResults> VIDEO_BROWSE_RESULTS = type(9, "video_browse_results", Direction.CLIENTBOUND, VideoPackets.BROWSE_RESULTS);
    public static final Type<VideoPackets.SessionCommand> VIDEO_SESSION_COMMAND = type(10, "video_session_command", Direction.SERVERBOUND, VideoPackets.SESSION_COMMAND);
    public static final Type<VideoPackets.SessionState> VIDEO_SESSION_STATE = type(11, "video_session_state", Direction.CLIENTBOUND, VideoPackets.SESSION_STATE);
    public static final Type<VideoPackets.TelevisionRemoved> VIDEO_TELEVISION_REMOVED = type(12, "video_television_removed", Direction.CLIENTBOUND, VideoPackets.TELEVISION_REMOVED);
    public static final Type<VideoPackets.SessionQueue> VIDEO_SESSION_QUEUE = type(13, "video_session_queue", Direction.CLIENTBOUND, VideoPackets.SESSION_QUEUE);
    public static final Type<VideoPackets.SegmentManifestRequest> VIDEO_MANIFEST_REQUEST = type(14, "video_manifest_request", Direction.SERVERBOUND, VideoPackets.SEGMENT_MANIFEST_REQUEST);
    public static final Type<VideoPackets.SegmentManifest> VIDEO_MANIFEST = type(15, "video_manifest", Direction.CLIENTBOUND, VideoPackets.SEGMENT_MANIFEST);
    public static final Type<VideoPackets.SegmentRequest> VIDEO_SEGMENT_REQUEST = type(16, "video_segment_request", Direction.SERVERBOUND, VideoPackets.SEGMENT_REQUEST);
    public static final Type<VideoPackets.SegmentChunk> VIDEO_SEGMENT_CHUNK = type(17, "video_segment_chunk", Direction.CLIENTBOUND, VideoPackets.SEGMENT_CHUNK);
    public static final Type<VideoPackets.SegmentAcknowledgement> VIDEO_SEGMENT_ACKNOWLEDGEMENT = type(18, "video_segment_ack", Direction.SERVERBOUND, VideoPackets.SEGMENT_ACKNOWLEDGEMENT);
    public static final Type<VideoPackets.ClientHealth> VIDEO_CLIENT_HEALTH = type(19, "video_client_health", Direction.SERVERBOUND, VideoPackets.CLIENT_HEALTH);
    public static final Map<Integer, Type<?>> TYPES = Collections.unmodifiableMap(MUTABLE_TYPES);

    private static <T> Type<T> type(int id, String name, Direction direction, WireCodec<T> codec) {
        Type<T> value = new Type<T>(id, name, direction, codec);
        if (MUTABLE_TYPES.put(id, value) != null) throw new IllegalStateException("Duplicate legacy packet ID " + id);
        return value;
    }
    public static Type<?> byId(int id) { return TYPES.get(id); }
    private LegacyPacketTypes() {}
}
