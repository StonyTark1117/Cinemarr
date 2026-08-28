package stonytark.cinemarr.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.server.TelevisionLifecycle;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CinemarrWorldScreensTest {
    @Test void activatedGeometryOwnershipMaskPresentationAndSessionRoundTrip(){
        CinemarrWorldScreens data=new CinemarrWorldScreens();UUID owner=UUID.randomUUID();
        data.putPixel(new BlockPos(0,0,0),Direction.NORTH);data.putPixel(new BlockPos(1,0,0),Direction.NORTH);
        data.putPixel(new BlockPos(0,1,0),Direction.NORTH);data.putPixel(new BlockPos(1,1,0),Direction.NORTH);
        BlockPos controller=new BlockPos(0,0,1);assertTrue(data.activate(controller,owner).success());
        CinemarrWorldScreens.Television tv=data.television(controller);assertEquals(2,tv.width());assertEquals(2,tv.height());
        assertEquals(owner,tv.owner());assertArrayEquals(new byte[]{15},tv.mask());
        data.updatePresentation(controller,PresentationMode.STRETCH);data.updateSession(controller,"family");data.updateRendition(controller,3840,2160);
        CinemarrWorldScreens restored=CinemarrWorldScreens.load(data.save(new CompoundTag(),null),null);
        CinemarrWorldScreens.Television roundTrip=restored.television(controller);
        assertEquals(PresentationMode.STRETCH,roundTrip.presentationMode());assertEquals("family",roundTrip.sessionName());
        assertArrayEquals(new byte[]{15},roundTrip.mask());assertEquals(Direction.NORTH.name(),roundTrip.facing().name());
        assertEquals(3840,roundTrip.renditionWidth());assertEquals(2160,roundTrip.renditionHeight());
    }

    @Test void onePersistedTelevisionIsDiscoverableFromEveryScreenChunk() {
        CinemarrWorldScreens data=new CinemarrWorldScreens(); UUID owner=UUID.randomUUID();
        for(int x=0;x<17;x++)data.putPixel(new BlockPos(x,0,0),Direction.NORTH);
        BlockPos controller=new BlockPos(0,0,1);assertTrue(data.activate(controller,owner).success());
        assertEquals(data.television(controller).id(),data.televisionsForChunk(new ChunkPos(0,0)).getFirst().id());
        assertEquals(data.television(controller).id(),data.televisionsForChunk(new ChunkPos(1,0)).getFirst().id());
        assertTrue(data.televisionsForChunk(new ChunkPos(2,0)).isEmpty());
    }

    @Test void breakingARegisteredPixelUnregistersAndNotifiesLifecycle() {
        java.util.concurrent.atomic.AtomicInteger removed=new java.util.concurrent.atomic.AtomicInteger();
        TelevisionLifecycle.reset((id,session)->removed.incrementAndGet());
        CinemarrWorldScreens data=new CinemarrWorldScreens();UUID owner=UUID.randomUUID();
        data.putPixel(new BlockPos(0,0,0),Direction.NORTH);data.putPixel(new BlockPos(1,0,0),Direction.NORTH);
        data.putPixel(new BlockPos(2,0,0),Direction.NORTH);data.putPixel(new BlockPos(3,0,0),Direction.NORTH);
        BlockPos controller=new BlockPos(0,0,1);assertTrue(data.activate(controller,owner).success());
        assertEquals(1,TelevisionLifecycle.count(owner));
        data.removePixel(new BlockPos(2,0,0));
        assertFalse(data.televisions().iterator().hasNext());assertEquals(0,TelevisionLifecycle.count(owner));assertEquals(1,removed.get());
        data.putPixel(new BlockPos(2,0,0),Direction.NORTH);
        assertNull(data.television(controller));
        assertTrue(data.activate(controller,owner).success());
        TelevisionLifecycle.reset(null);
    }

    @Test void controllerRemovalAndRemoteLifecycleRemovalShareTheSameAuthority() {
        TelevisionLifecycle.reset(null);CinemarrWorldScreens data=new CinemarrWorldScreens();UUID owner=UUID.randomUUID();
        for(int x=0;x<4;x++)data.putPixel(new BlockPos(x,0,0),Direction.NORTH);
        BlockPos controller=new BlockPos(0,0,1);assertTrue(data.activate(controller,owner).success());UUID id=data.television(controller).id();
        assertTrue(TelevisionLifecycle.unregister(id));assertNull(data.television(controller));assertEquals(0,TelevisionLifecycle.count(owner));
        assertTrue(data.activate(controller,owner).success());data.removeController(controller);assertNull(data.television(controller));assertEquals(0,TelevisionLifecycle.count(owner));
        TelevisionLifecycle.reset(null);
    }

    @Test void overlappingActivationFailsAndSavedMissingPixelsArePrunedOnReconciliation() {
        TelevisionLifecycle.reset(null);CinemarrWorldScreens data=new CinemarrWorldScreens();UUID owner=UUID.randomUUID();
        for(int x=0;x<4;x++)data.putPixel(new BlockPos(x,0,0),Direction.NORTH);
        BlockPos first=new BlockPos(0,0,1),second=new BlockPos(3,0,1);assertTrue(data.activate(first,owner).success());assertFalse(data.activate(second,owner).success());
        CompoundTag saved=data.save(new CompoundTag(),null);saved.getList("pixels",net.minecraft.nbt.Tag.TAG_COMPOUND).remove(0);
        TelevisionLifecycle.reset(null);CinemarrWorldScreens restored=CinemarrWorldScreens.load(saved,null);restored.reconcileRegistrations();
        assertTrue(restored.televisions().isEmpty());assertEquals(0,TelevisionLifecycle.count(owner));
        TelevisionLifecycle.reset(null);
    }
}
