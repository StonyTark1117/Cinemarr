package stonytark.cinemarr.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolCapabilities;

/** Connection negotiation and media-clock messages shared by the television protocol. */
public final class CinemarrPayloads {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> genericType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, path));
    }

    public record ClientHello(int protocolVersion, long featureBits, int maxChunkBytes, int maxTransferWindow,
                              int healthIntervalMs) implements CustomPacketPayload, CinemarrMessage {
        public ClientHello(int protocolVersion) { this(protocolVersion, ProtocolCapabilities.REQUIRED_FEATURES,
                ProtocolCapabilities.currentOffer().maxChunkBytes(), ProtocolCapabilities.MAX_TRANSFER_WINDOW,
                ProtocolCapabilities.HEALTH_INTERVAL_MS); }
        public boolean valid(){try{ProtocolCapabilities.negotiate(protocolVersion,featureBits,maxChunkBytes,maxTransferWindow,healthIntervalMs);return true;}catch(IllegalArgumentException invalid){return false;}}
        public static final Type<ClientHello> TYPE = genericType("client_hello");
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientHello> CODEC =
                StreamCodec.ofMember(ClientHello::write, ClientHello::read);
        private void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(protocolVersion); buffer.writeLong(featureBits); buffer.writeVarInt(maxChunkBytes); buffer.writeVarInt(maxTransferWindow); buffer.writeVarInt(healthIntervalMs); }
        private static ClientHello read(RegistryFriendlyByteBuf buffer) { return new ClientHello(buffer.readVarInt(),buffer.readLong(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt()); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ServerHello(int protocolVersion, long featureBits, int maxChunkBytes, int maxTransferWindow,
                              int healthIntervalMs, long serverEpochMs) implements CustomPacketPayload, CinemarrMessage {
        public ServerHello(int protocolVersion,long serverEpochMs){this(protocolVersion,ProtocolCapabilities.REQUIRED_FEATURES,
                ProtocolCapabilities.currentOffer().maxChunkBytes(),ProtocolCapabilities.MAX_TRANSFER_WINDOW,
                ProtocolCapabilities.HEALTH_INTERVAL_MS,serverEpochMs);}
        public boolean valid(){try{ProtocolCapabilities.negotiate(protocolVersion,featureBits,maxChunkBytes,maxTransferWindow,healthIntervalMs);return true;}catch(IllegalArgumentException invalid){return false;}}
        public static final Type<ServerHello> TYPE = genericType("server_hello");
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerHello> CODEC =
                StreamCodec.ofMember(ServerHello::write, ServerHello::read);
        private void write(RegistryFriendlyByteBuf buffer) { buffer.writeVarInt(protocolVersion);buffer.writeLong(featureBits);buffer.writeVarInt(maxChunkBytes);buffer.writeVarInt(maxTransferWindow);buffer.writeVarInt(healthIntervalMs);buffer.writeLong(serverEpochMs); }
        private static ServerHello read(RegistryFriendlyByteBuf buffer) { return new ServerHello(buffer.readVarInt(),buffer.readLong(),buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readLong()); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TimeSyncRequest(long nonce, long clientSentEpochMs) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<TimeSyncRequest> TYPE = genericType("time_sync_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, TimeSyncRequest> CODEC =
                StreamCodec.ofMember(TimeSyncRequest::write, TimeSyncRequest::read);
        private void write(RegistryFriendlyByteBuf buffer) { buffer.writeLong(nonce); buffer.writeLong(clientSentEpochMs); }
        private static TimeSyncRequest read(RegistryFriendlyByteBuf buffer) { return new TimeSyncRequest(buffer.readLong(), buffer.readLong()); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TimeSyncResponse(long nonce, long clientSentEpochMs, long serverEpochMs)
            implements CustomPacketPayload, CinemarrMessage {
        public static final Type<TimeSyncResponse> TYPE = genericType("time_sync_response");
        public static final StreamCodec<RegistryFriendlyByteBuf, TimeSyncResponse> CODEC =
                StreamCodec.ofMember(TimeSyncResponse::write, TimeSyncResponse::read);
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeLong(nonce); buffer.writeLong(clientSentEpochMs); buffer.writeLong(serverEpochMs);
        }
        private static TimeSyncResponse read(RegistryFriendlyByteBuf buffer) {
            return new TimeSyncResponse(buffer.readLong(), buffer.readLong(), buffer.readLong());
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ErrorMessage(String message) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<ErrorMessage> TYPE = genericType("error");
        public static final StreamCodec<RegistryFriendlyByteBuf, ErrorMessage> CODEC =
                StreamCodec.ofMember(ErrorMessage::write, ErrorMessage::read);
        public ErrorMessage { message = message == null ? "" : message.substring(0, Math.min(message.length(), 256)); }
        private void write(RegistryFriendlyByteBuf buffer) { buffer.writeUtf(message, 256); }
        private static ErrorMessage read(RegistryFriendlyByteBuf buffer) { return new ErrorMessage(buffer.readUtf(256)); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private CinemarrPayloads() {}
}
