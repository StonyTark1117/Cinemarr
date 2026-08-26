package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinemarrWorldScreensTest {
    @Test void activatedGeometryOwnershipMaskPresentationAndSessionRoundTrip(){
        CinemarrWorldScreens data=new CinemarrWorldScreens();UUID owner=UUID.randomUUID();
        data.putPixel(new BlockPos(0,0,0),Direction.NORTH);data.putPixel(new BlockPos(1,0,0),Direction.NORTH);
        data.putPixel(new BlockPos(0,1,0),Direction.NORTH);data.putPixel(new BlockPos(1,1,0),Direction.NORTH);
        BlockPos controller=new BlockPos(0,0,1);assertTrue(data.activate(controller,owner).success());
        CinemarrWorldScreens.Television tv=data.television(controller);assertEquals(2,tv.width());assertEquals(2,tv.height());
        assertEquals(owner,tv.owner());assertArrayEquals(new byte[]{15},tv.mask());
        data.updatePresentation(controller,PresentationMode.STRETCH);data.updateSession(controller,"family");
        CinemarrWorldScreens restored=CinemarrWorldScreens.load(data.save(new CompoundTag(),null),null);
        CinemarrWorldScreens.Television roundTrip=restored.television(controller);
        assertEquals(PresentationMode.STRETCH,roundTrip.presentationMode());assertEquals("family",roundTrip.sessionName());
        assertArrayEquals(new byte[]{15},roundTrip.mask());assertEquals(Direction.NORTH.name(),roundTrip.facing().name());
    }

    @Test void onePersistedTelevisionIsDiscoverableFromEveryScreenChunk() {
        CinemarrWorldScreens data=new CinemarrWorldScreens(); UUID owner=UUID.randomUUID();
        for(int x=0;x<17;x++)data.putPixel(new BlockPos(x,0,0),Direction.NORTH);
        BlockPos controller=new BlockPos(0,0,1);assertTrue(data.activate(controller,owner).success());
        assertEquals(data.television(controller).id(),data.televisionsForChunk(new ChunkPos(0,0)).getFirst().id());
        assertEquals(data.television(controller).id(),data.televisionsForChunk(new ChunkPos(1,0)).getFirst().id());
        assertTrue(data.televisionsForChunk(new ChunkPos(2,0)).isEmpty());
    }
}
