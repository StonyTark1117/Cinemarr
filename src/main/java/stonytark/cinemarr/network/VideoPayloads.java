package stonytark.cinemarr.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolException;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.WireCodec;

public final class VideoPayloads {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, path));
    }
    private static <T> T decode(WireCodec<T> codec, RegistryFriendlyByteBuf buffer) {
        try { return codec.decode(new MinecraftWireInput(buffer)); }
        catch (ProtocolException malformed) { throw new DecoderException(malformed.getMessage(), malformed); }
    }

    public record OpenVideoScreen(long controllerPos) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<OpenVideoScreen> TYPE=VideoPayloads.type("open_video_screen");
        public static final StreamCodec<RegistryFriendlyByteBuf,OpenVideoScreen> CODEC=StreamCodec.ofMember(OpenVideoScreen::write,OpenVideoScreen::read);
        private static OpenVideoScreen read(RegistryFriendlyByteBuf buffer){return new OpenVideoScreen(buffer.readLong());}
        private void write(RegistryFriendlyByteBuf buffer){buffer.writeLong(controllerPos);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    public record LibraryListRequest() implements CustomPacketPayload, CinemarrMessage {
        public static final Type<LibraryListRequest> TYPE=VideoPayloads.type("video_library_list_request");
        public static final StreamCodec<RegistryFriendlyByteBuf,LibraryListRequest> CODEC=StreamCodec.unit(new LibraryListRequest());
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record LibraryList(VideoPackets.LibraryList value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<LibraryList> TYPE=VideoPayloads.type("video_library_list");
        public static final StreamCodec<RegistryFriendlyByteBuf,LibraryList> CODEC=StreamCodec.ofMember(LibraryList::write,LibraryList::read);
        private static LibraryList read(RegistryFriendlyByteBuf buffer){return new LibraryList(decode(VideoPackets.LIBRARY_LIST,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.LIBRARY_LIST.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record BrowseRequest(VideoPackets.BrowseRequest value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<BrowseRequest> TYPE=VideoPayloads.type("video_browse_request");
        public static final StreamCodec<RegistryFriendlyByteBuf,BrowseRequest> CODEC=StreamCodec.ofMember(BrowseRequest::write,BrowseRequest::read);
        private static BrowseRequest read(RegistryFriendlyByteBuf buffer){return new BrowseRequest(decode(VideoPackets.BROWSE_REQUEST,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.BROWSE_REQUEST.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record BrowseResults(VideoPackets.BrowseResults value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<BrowseResults> TYPE=VideoPayloads.type("video_browse_results");
        public static final StreamCodec<RegistryFriendlyByteBuf,BrowseResults> CODEC=StreamCodec.ofMember(BrowseResults::write,BrowseResults::read);
        private static BrowseResults read(RegistryFriendlyByteBuf buffer){return new BrowseResults(decode(VideoPackets.BROWSE_RESULTS,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.BROWSE_RESULTS.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record SessionCommand(VideoPackets.SessionCommand value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<SessionCommand> TYPE=VideoPayloads.type("video_session_command");
        public static final StreamCodec<RegistryFriendlyByteBuf,SessionCommand> CODEC=StreamCodec.ofMember(SessionCommand::write,SessionCommand::read);
        private static SessionCommand read(RegistryFriendlyByteBuf buffer){return new SessionCommand(decode(VideoPackets.SESSION_COMMAND,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.SESSION_COMMAND.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record SessionState(VideoPackets.SessionState value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<SessionState> TYPE=VideoPayloads.type("video_session_state");
        public static final StreamCodec<RegistryFriendlyByteBuf,SessionState> CODEC=StreamCodec.ofMember(SessionState::write,SessionState::read);
        private static SessionState read(RegistryFriendlyByteBuf buffer){return new SessionState(decode(VideoPackets.SESSION_STATE,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.SESSION_STATE.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record SegmentManifest(VideoPackets.SegmentManifest value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<SegmentManifest> TYPE=VideoPayloads.type("video_segment_manifest");
        public static final StreamCodec<RegistryFriendlyByteBuf,SegmentManifest> CODEC=StreamCodec.ofMember(SegmentManifest::write,SegmentManifest::read);
        private static SegmentManifest read(RegistryFriendlyByteBuf buffer){return new SegmentManifest(decode(VideoPackets.SEGMENT_MANIFEST,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.SEGMENT_MANIFEST.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record SegmentRequest(VideoPackets.SegmentRequest value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<SegmentRequest> TYPE=VideoPayloads.type("video_segment_request");
        public static final StreamCodec<RegistryFriendlyByteBuf,SegmentRequest> CODEC=StreamCodec.ofMember(SegmentRequest::write,SegmentRequest::read);
        private static SegmentRequest read(RegistryFriendlyByteBuf buffer){return new SegmentRequest(decode(VideoPackets.SEGMENT_REQUEST,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.SEGMENT_REQUEST.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record SegmentChunk(VideoPackets.SegmentChunk value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<SegmentChunk> TYPE=VideoPayloads.type("video_segment_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf,SegmentChunk> CODEC=StreamCodec.ofMember(SegmentChunk::write,SegmentChunk::read);
        private static SegmentChunk read(RegistryFriendlyByteBuf buffer){return new SegmentChunk(decode(VideoPackets.SEGMENT_CHUNK,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.SEGMENT_CHUNK.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record SegmentAcknowledgement(VideoPackets.SegmentAcknowledgement value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<SegmentAcknowledgement> TYPE=VideoPayloads.type("video_segment_ack");
        public static final StreamCodec<RegistryFriendlyByteBuf,SegmentAcknowledgement> CODEC=StreamCodec.ofMember(SegmentAcknowledgement::write,SegmentAcknowledgement::read);
        private static SegmentAcknowledgement read(RegistryFriendlyByteBuf buffer){return new SegmentAcknowledgement(decode(VideoPackets.SEGMENT_ACKNOWLEDGEMENT,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.SEGMENT_ACKNOWLEDGEMENT.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record ClientHealth(VideoPackets.ClientHealth value) implements CustomPacketPayload, CinemarrMessage {
        public static final Type<ClientHealth> TYPE=VideoPayloads.type("video_client_health");
        public static final StreamCodec<RegistryFriendlyByteBuf,ClientHealth> CODEC=StreamCodec.ofMember(ClientHealth::write,ClientHealth::read);
        private static ClientHealth read(RegistryFriendlyByteBuf buffer){return new ClientHealth(decode(VideoPackets.CLIENT_HEALTH,buffer));}
        private void write(RegistryFriendlyByteBuf buffer){VideoPackets.CLIENT_HEALTH.encode(new MinecraftWireOutput(buffer),value);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private VideoPayloads() {}
}
