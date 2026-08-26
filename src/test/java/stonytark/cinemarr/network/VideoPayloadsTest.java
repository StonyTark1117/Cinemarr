package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoPayloadsTest {
    @Test void protocolEightIsAdvertisedAndSessionStateRoundTripsThroughNeoForge(){
        assertEquals(8,ProtocolLimits.VERSION);UUID tv=UUID.randomUUID(),session=UUID.randomUUID();
        VideoPackets.SessionState state=new VideoPackets.SessionState(tv,99L,session,2,VideoPackets.SessionStatus.PLAYING,
                new VideoMediaItem(MediaKind.MOVIE,"1","Movie","","PG",0,90_000),1_000,90_000,false,PresentationMode.FIT,17,11,new byte[]{3,4},ScreenFacing.NORTH,42,-8,10,List.of(),-1,-1,5_000,true,"ok");
        VideoPayloads.SessionState decoded=roundTrip(VideoPayloads.SessionState.CODEC,new VideoPayloads.SessionState(state));
        assertEquals(tv,decoded.value().televisionId());assertEquals("Movie",decoded.value().item().title());assertArrayEquals(new byte[]{3,4},decoded.value().visibilityMask());
        assertEquals(ScreenFacing.NORTH,decoded.value().screenFacing());assertEquals(42,decoded.value().screenPlane());
        assertEquals(99L,decoded.value().controllerPos());
        assertEquals(99L,roundTrip(VideoPayloads.TelevisionRemoved.CODEC,new VideoPayloads.TelevisionRemoved(new VideoPackets.TelevisionRemoved(99L))).value().controllerPos());
        assertEquals("Movie",roundTrip(VideoPayloads.SessionQueue.CODEC,new VideoPayloads.SessionQueue(new VideoPackets.SessionQueue(session,2,List.of(new QueuedVideo("movies",state.item()))))).value().entries().getFirst().item().title());
    }
    @Test void browseAndManifestRoundTrip(){
        VideoPackets.BrowseResults browse=new VideoPackets.BrowseResults("movies","","q",0,false,List.of(new VideoMediaItem(MediaKind.MOVIE,"1","Movie","","PG",0,1)));
        assertEquals("Movie",roundTrip(VideoPayloads.BrowseResults.CODEC,new VideoPayloads.BrowseResults(browse)).value().items().get(0).title());
        UUID session=UUID.randomUUID();VideoPackets.SegmentManifest manifest=new VideoPackets.SegmentManifest(session,1,640,360,"mpegts","h264","aac",1,false,List.of(new VideoPackets.SegmentDescriptor(0,0,1000,true,0,"")));
        assertEquals(640,roundTrip(VideoPayloads.SegmentManifest.CODEC,new VideoPayloads.SegmentManifest(manifest)).value().width());
    }
    private static <T>T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,T> codec,T value){RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);codec.encode(buffer,value);buffer.readerIndex(0);return codec.decode(buffer);}
}
