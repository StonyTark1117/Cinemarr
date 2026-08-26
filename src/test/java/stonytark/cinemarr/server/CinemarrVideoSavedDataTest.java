package stonytark.cinemarr.server;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.QueuedVideo;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CinemarrVideoSavedDataTest {
    @Test void roundTripKeepsPlaybackCheckpointWithoutCredentials(){
        CinemarrVideoSavedData data=new CinemarrVideoSavedData();
        VideoMediaItem movie=new VideoMediaItem(MediaKind.MOVIE,"42","Movie","","PG",0,90_000);
        data.put(new CinemarrVideoSavedData.Record("living-room","family_movies",movie,12_345,true,7,-1,java.util.List.of(new QueuedVideo("family_movies",movie))));
        CompoundTag tag=data.save(new CompoundTag(),null);CinemarrVideoSavedData restored=CinemarrVideoSavedData.load(tag,null);CinemarrVideoSavedData.Record record=restored.record("living-room");
        assertNotNull(record);assertEquals("42",record.item().key());assertEquals(12_345,record.positionMs());assertTrue(record.paused());assertEquals(7,record.audioStreamId());assertEquals(-1,record.subtitleStreamId());
        assertEquals("42",record.queue().getFirst().item().key());
        String encoded=tag.toString();assertFalse(encoded.contains("token"));assertFalse(encoded.contains("192.168."));assertFalse(encoded.contains("http"));
    }

    @Test void malformedAndOrphanedRecordsAreDropped(){
        CinemarrVideoSavedData data=new CinemarrVideoSavedData();data.put(new CinemarrVideoSavedData.Record("kept","tv",new VideoMediaItem(MediaKind.EPISODE,"7","Episode","Show","TV-PG",3,30_000),1_000,false,-1,-1));
        data.retain(Set.of("different"));assertTrue(data.records().isEmpty());
        CompoundTag malformed=new CompoundTag();malformed.putString("kind","NOT_A_KIND");assertTrue(CinemarrVideoSavedData.load(malformed,null).records().isEmpty());
    }
}
