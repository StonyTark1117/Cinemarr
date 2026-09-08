package stonytark.cinemarr.screen;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.screen.ScreenFacing;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyWorldScreensTest {
    @Test void dimensionsHaveIndependentIndexesAtIdenticalCoordinates() {
        net.minecraft.world.storage.MapStorage overworld = new net.minecraft.world.storage.MapStorage(null);
        net.minecraft.world.storage.MapStorage nether = new net.minecraft.world.storage.MapStorage(null);
        LegacyWorldScreens first = LegacyWorldScreens.loadDimension(overworld);
        LegacyWorldScreens second = LegacyWorldScreens.loadDimension(nether);
        org.junit.jupiter.api.Assertions.assertNotSame(first,second);
        org.junit.jupiter.api.Assertions.assertSame(first,LegacyWorldScreens.loadDimension(overworld));
        first.putPixel(0,0,0,ScreenFacing.NORTH);
        NBTTagCompound firstTag=new NBTTagCompound(), secondTag=new NBTTagCompound();
        first.writeToNBT(firstTag); second.writeToNBT(secondTag);
        assertEquals(1,firstTag.getTagList("pixels",10).tagCount());
        assertEquals(0,secondTag.getTagList("pixels",10).tagCount());
        second.putPixel(0,0,0,ScreenFacing.SOUTH); second.writeToNBT(secondTag);
        assertEquals("SOUTH",secondTag.getTagList("pixels",10).getCompoundTagAt(0).getString("facing"));
        assertEquals("NORTH",firstTag.getTagList("pixels",10).getCompoundTagAt(0).getString("facing"));
    }

    @Test void oldOverworldEntriesAndOriginalRecoveryBytesArePreserved(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        LegacyWorldScreens old = new LegacyWorldScreens(); old.putPixel(2,3,4,ScreenFacing.WEST);
        NBTTagCompound tag=new NBTTagCompound(); old.writeToNBT(tag); tag.removeTag("dimensionLocalStorage");
        java.nio.file.Path source=directory.resolve("cinemarr_screens.dat");
        try (java.io.OutputStream stream=java.nio.file.Files.newOutputStream(source)) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(tag,stream);
        }
        byte[] original=java.nio.file.Files.readAllBytes(source);
        LegacyWorldScreens.preserveLegacySharedFile(source);
        java.nio.file.Path backup=source.resolveSibling("cinemarr_screens.dat.before-dimension-isolation.bak");
        assertArrayEquals(original,java.nio.file.Files.readAllBytes(source));
        assertArrayEquals(original,java.nio.file.Files.readAllBytes(backup));
        LegacyWorldScreens restored=new LegacyWorldScreens(); restored.readFromNBT(tag);
        net.minecraft.world.storage.MapStorage storage=new net.minecraft.world.storage.MapStorage(null);
        storage.setData(LegacyWorldScreens.DATA_NAME,restored);
        org.junit.jupiter.api.Assertions.assertSame(restored,LegacyWorldScreens.loadDimension(storage));
        NBTTagCompound saved=new NBTTagCompound(); restored.writeToNBT(saved);
        assertEquals(1,saved.getTagList("pixels",10).tagCount());
        assertEquals("WEST",saved.getTagList("pixels",10).getCompoundTagAt(0).getString("facing"));
        java.nio.file.Files.write(source,new byte[]{1,2,3});
        LegacyWorldScreens.preserveLegacySharedFile(source);
        assertArrayEquals(original,java.nio.file.Files.readAllBytes(backup));
    }

    @Test
    void activatesPersistsAndInvalidatesAnArbitraryScreen() {
        LegacyWorldScreens screens = new LegacyWorldScreens();
        screens.putPixel(0, 0, 0, ScreenFacing.NORTH);
        screens.putPixel(1, 0, 0, ScreenFacing.NORTH);
        screens.putPixel(0, 1, 0, ScreenFacing.NORTH);
        screens.putPixel(1, 1, 0, ScreenFacing.NORTH);

        long controller = LegacyBlockPos.pack(0, 0, 1);
        LegacyWorldScreens.Activation activation = screens.activate(0, 0, 1, UUID.fromString("12345678-1234-5678-9abc-def012345678"));
        assertTrue(activation.success(), activation.message());
        assertEquals(2, activation.television().width());
        assertEquals(2, activation.television().height());
        assertEquals(ScreenFacing.NORTH, activation.television().facing());
        assertEquals(0, activation.television().plane());
        assertEquals(4, activation.television().pixels().size());
        screens.updateRendition(controller, 7680, 4320);

        NBTTagCompound tag = new NBTTagCompound();
        screens.writeToNBT(tag);
        LegacyWorldScreens restored = new LegacyWorldScreens();
        restored.readFromNBT(tag);
        LegacyWorldScreens.Television television = restored.television(controller);
        assertNotNull(television);
        assertEquals(activation.television().id(), television.id());
        assertArrayEquals(activation.television().mask(), television.mask());
        assertEquals(7680, television.renditionWidth());
        assertEquals(4320, television.renditionHeight());

        restored.removePixel(1, 1, 0);
        assertFalse(restored.televisions().iterator().hasNext());
    }

    @Test
    void usesModernCompatiblePackedCoordinates() {
        long packed = LegacyBlockPos.pack(-30_000_000, 255, 29_999_999);
        assertEquals(-30_000_000, LegacyBlockPos.x(packed));
        assertEquals(255, LegacyBlockPos.y(packed));
        assertEquals(29_999_999, LegacyBlockPos.z(packed));
    }
}
