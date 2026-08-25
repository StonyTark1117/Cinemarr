package stonytark.cinemarr.core.protocol;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoPacketCodecTest {
    @Test void sessionStateRoundTripsExactScreenAndTimelineState() {
        UUID tv=UUID.randomUUID(), session=UUID.randomUUID(); byte[] mask={1,2,3};
        VideoPackets.SessionState value=new VideoPackets.SessionState(tv,session,9,VideoPackets.SessionStatus.PLAYING,item(),1234,90000,false,PresentationMode.FILL,17,11,mask,ScreenFacing.EAST,22,-3,7,4567,true,"ok");
        VideoPackets.SessionState decoded=roundTrip(VideoPackets.SESSION_STATE,value);
        assertEquals(tv,decoded.televisionId()); assertEquals(session,decoded.sessionId()); assertEquals(9,decoded.generation());
        assertEquals(17,decoded.screenWidth()); assertEquals(11,decoded.screenHeight()); assertArrayEquals(mask,decoded.visibilityMask());
        assertEquals("Movie",decoded.item().title()); assertEquals(PresentationMode.FILL,decoded.presentationMode());
        assertEquals(ScreenFacing.EAST,decoded.screenFacing()); assertEquals(22,decoded.screenPlane());
    }

    @Test void segmentManifestAndChunkRoundTrip() {
        UUID session=UUID.randomUUID();
        VideoPackets.SegmentManifest manifest=new VideoPackets.SegmentManifest(session,4,640,360,"mpegts","h264","aac",90000,
                Arrays.asList(new VideoPackets.SegmentDescriptor(7,14000,2000,true,20000,repeat('a',64))));
        VideoPackets.SegmentManifest decoded=roundTrip(VideoPackets.SEGMENT_MANIFEST,manifest);
        assertEquals(7,decoded.segments().get(0).index()); assertEquals("h264",decoded.videoCodec());
        byte[] bytes={4,5,6}; VideoPackets.SegmentChunk chunk=new VideoPackets.SegmentChunk(session,4,10,7,0,2,14000,true,repeat('a',64),bytes);
        assertArrayEquals(bytes,roundTrip(VideoPackets.SEGMENT_CHUNK,chunk).data());
    }

    @Test void oversizedCollectionsAndChunksAreRejectedDuringDecode() {
        ByteArrayWireOutput libraries=new ByteArrayWireOutput(); libraries.writeVarInt(ProtocolLimits.MAX_VIDEO_LIBRARIES+1);
        assertThrows(ProtocolException.class,()->VideoPackets.LIBRARY_LIST.decode(new ByteArrayWireInput(libraries.toByteArray())));
        ByteArrayWireOutput chunk=new ByteArrayWireOutput(); chunk.writeUuid(UUID.randomUUID()); chunk.writeVarLong(1); chunk.writeVarLong(1);
        chunk.writeVarInt(0); chunk.writeVarInt(0); chunk.writeVarInt(1); chunk.writeVarLong(0); chunk.writeBoolean(true); chunk.writeUtf(repeat('a',64),64);
        chunk.writeByteArray(new byte[ProtocolLimits.MAX_VIDEO_CHUNK_BYTES+1],ProtocolLimits.MAX_VIDEO_CHUNK_BYTES+1);
        assertThrows(ProtocolException.class,()->VideoPackets.SEGMENT_CHUNK.decode(new ByteArrayWireInput(chunk.toByteArray())));
    }

    private static VideoMediaItem item(){return new VideoMediaItem(MediaKind.MOVIE,"1","Movie","","PG",0,90000);}
    private static <T> T roundTrip(WireCodec<T> codec,T value){ByteArrayWireOutput out=new ByteArrayWireOutput();codec.encode(out,value);return codec.decode(new ByteArrayWireInput(out.toByteArray()));}
    private static String repeat(char value,int count){char[] chars=new char[count];Arrays.fill(chars,value);return new String(chars);}
}
