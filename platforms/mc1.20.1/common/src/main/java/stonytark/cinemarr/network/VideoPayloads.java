package stonytark.cinemarr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolException;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.WireCodec;

/** Pre-1.20.5 custom-payload wrappers for the shared video protocol. */
public final class VideoPayloads {
    private static ResourceLocation id(String path) { return new ResourceLocation(Cinemarr.MODID, path); }
    private static <T> T decode(WireCodec<T> codec, FriendlyByteBuf buffer) {
        try { return codec.decode(new MinecraftWireInput(buffer)); }
        catch (ProtocolException malformed) { throw new io.netty.handler.codec.DecoderException(malformed); }
    }
    public static ResourceLocation idOf(CinemarrMessage value){
        if(value instanceof OpenVideoScreen)return OpenVideoScreen.ID;if(value instanceof LibraryListRequest)return LibraryListRequest.ID;
        if(value instanceof LibraryList)return LibraryList.ID;if(value instanceof BrowseRequest)return BrowseRequest.ID;if(value instanceof BrowseResults)return BrowseResults.ID;
        if(value instanceof SessionCommand)return SessionCommand.ID;if(value instanceof SessionState)return SessionState.ID;if(value instanceof TelevisionRemoved)return TelevisionRemoved.ID;
        if(value instanceof SessionQueue)return SessionQueue.ID;if(value instanceof SegmentManifest)return SegmentManifest.ID;if(value instanceof SegmentManifestRequest)return SegmentManifestRequest.ID;
        if(value instanceof SegmentRequest)return SegmentRequest.ID;if(value instanceof SegmentChunk)return SegmentChunk.ID;if(value instanceof SegmentAcknowledgement)return SegmentAcknowledgement.ID;
        if(value instanceof ClientHealth)return ClientHealth.ID;throw new IllegalArgumentException("Unknown video payload "+value.getClass().getName());
    }
    public static boolean supports(CinemarrMessage value){return value instanceof OpenVideoScreen||value instanceof LibraryListRequest||value instanceof LibraryList||value instanceof BrowseRequest||value instanceof BrowseResults||value instanceof SessionCommand||value instanceof SessionState||value instanceof TelevisionRemoved||value instanceof SessionQueue||value instanceof SegmentManifest||value instanceof SegmentManifestRequest||value instanceof SegmentRequest||value instanceof SegmentChunk||value instanceof SegmentAcknowledgement||value instanceof ClientHealth;}
    public static void write(CinemarrMessage value,FriendlyByteBuf b){
        if(value instanceof OpenVideoScreen v)v.write(b);else if(value instanceof LibraryListRequest v)v.write(b);else if(value instanceof LibraryList v)v.write(b);
        else if(value instanceof BrowseRequest v)v.write(b);else if(value instanceof BrowseResults v)v.write(b);else if(value instanceof SessionCommand v)v.write(b);
        else if(value instanceof SessionState v)v.write(b);else if(value instanceof TelevisionRemoved v)v.write(b);else if(value instanceof SessionQueue v)v.write(b);
        else if(value instanceof SegmentManifest v)v.write(b);else if(value instanceof SegmentManifestRequest v)v.write(b);else if(value instanceof SegmentRequest v)v.write(b);
        else if(value instanceof SegmentChunk v)v.write(b);else if(value instanceof SegmentAcknowledgement v)v.write(b);else if(value instanceof ClientHealth v)v.write(b);
        else throw new IllegalArgumentException("Unknown video payload "+value.getClass().getName());
    }

    public record OpenVideoScreen(long controllerPos) implements CinemarrMessage {
        public static final ResourceLocation ID=id("open_video_screen");
        public static OpenVideoScreen read(FriendlyByteBuf b){return new OpenVideoScreen(b.readLong());}
        public void write(FriendlyByteBuf b){b.writeLong(controllerPos);}
    }
    public record LibraryListRequest() implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_library_list_request");
        public static LibraryListRequest read(FriendlyByteBuf b){return new LibraryListRequest();}
        public void write(FriendlyByteBuf b){}
    }
    public record LibraryList(VideoPackets.LibraryList value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_library_list");
        public static LibraryList read(FriendlyByteBuf b){return new LibraryList(decode(VideoPackets.LIBRARY_LIST,b));}
        public void write(FriendlyByteBuf b){VideoPackets.LIBRARY_LIST.encode(new MinecraftWireOutput(b),value);}
    }
    public record BrowseRequest(VideoPackets.BrowseRequest value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_browse_request");
        public static BrowseRequest read(FriendlyByteBuf b){return new BrowseRequest(decode(VideoPackets.BROWSE_REQUEST,b));}
        public void write(FriendlyByteBuf b){VideoPackets.BROWSE_REQUEST.encode(new MinecraftWireOutput(b),value);}
    }
    public record BrowseResults(VideoPackets.BrowseResults value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_browse_results");
        public static BrowseResults read(FriendlyByteBuf b){return new BrowseResults(decode(VideoPackets.BROWSE_RESULTS,b));}
        public void write(FriendlyByteBuf b){VideoPackets.BROWSE_RESULTS.encode(new MinecraftWireOutput(b),value);}
    }
    public record SessionCommand(VideoPackets.SessionCommand value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_session_command");
        public static SessionCommand read(FriendlyByteBuf b){return new SessionCommand(decode(VideoPackets.SESSION_COMMAND,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SESSION_COMMAND.encode(new MinecraftWireOutput(b),value);}
    }
    public record SessionState(VideoPackets.SessionState value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_session_state");
        public static SessionState read(FriendlyByteBuf b){return new SessionState(decode(VideoPackets.SESSION_STATE,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SESSION_STATE.encode(new MinecraftWireOutput(b),value);}
    }
    public record TelevisionRemoved(VideoPackets.TelevisionRemoved value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_television_removed");
        public static TelevisionRemoved read(FriendlyByteBuf b){return new TelevisionRemoved(decode(VideoPackets.TELEVISION_REMOVED,b));}
        public void write(FriendlyByteBuf b){VideoPackets.TELEVISION_REMOVED.encode(new MinecraftWireOutput(b),value);}
    }
    public record SessionQueue(VideoPackets.SessionQueue value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_session_queue");
        public static SessionQueue read(FriendlyByteBuf b){return new SessionQueue(decode(VideoPackets.SESSION_QUEUE,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SESSION_QUEUE.encode(new MinecraftWireOutput(b),value);}
    }
    public record SegmentManifest(VideoPackets.SegmentManifest value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_segment_manifest");
        public static SegmentManifest read(FriendlyByteBuf b){return new SegmentManifest(decode(VideoPackets.SEGMENT_MANIFEST,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SEGMENT_MANIFEST.encode(new MinecraftWireOutput(b),value);}
    }
    public record SegmentManifestRequest(VideoPackets.SegmentManifestRequest value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_segment_manifest_request");
        public static SegmentManifestRequest read(FriendlyByteBuf b){return new SegmentManifestRequest(decode(VideoPackets.SEGMENT_MANIFEST_REQUEST,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SEGMENT_MANIFEST_REQUEST.encode(new MinecraftWireOutput(b),value);}
    }
    public record SegmentRequest(VideoPackets.SegmentRequest value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_segment_request");
        public static SegmentRequest read(FriendlyByteBuf b){return new SegmentRequest(decode(VideoPackets.SEGMENT_REQUEST,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SEGMENT_REQUEST.encode(new MinecraftWireOutput(b),value);}
    }
    public record SegmentChunk(VideoPackets.SegmentChunk value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_segment_chunk");
        public static SegmentChunk read(FriendlyByteBuf b){return new SegmentChunk(decode(VideoPackets.SEGMENT_CHUNK,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SEGMENT_CHUNK.encode(new MinecraftWireOutput(b),value);}
    }
    public record SegmentAcknowledgement(VideoPackets.SegmentAcknowledgement value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_segment_ack");
        public static SegmentAcknowledgement read(FriendlyByteBuf b){return new SegmentAcknowledgement(decode(VideoPackets.SEGMENT_ACKNOWLEDGEMENT,b));}
        public void write(FriendlyByteBuf b){VideoPackets.SEGMENT_ACKNOWLEDGEMENT.encode(new MinecraftWireOutput(b),value);}
    }
    public record ClientHealth(VideoPackets.ClientHealth value) implements CinemarrMessage {
        public static final ResourceLocation ID=id("video_client_health");
        public static ClientHealth read(FriendlyByteBuf b){return new ClientHealth(decode(VideoPackets.CLIENT_HEALTH,b));}
        public void write(FriendlyByteBuf b){VideoPackets.CLIENT_HEALTH.encode(new MinecraftWireOutput(b),value);}
    }
    private VideoPayloads() {}
}
