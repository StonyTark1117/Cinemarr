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

        NBTTagCompound tag = new NBTTagCompound();
        screens.writeToNBT(tag);
        LegacyWorldScreens restored = new LegacyWorldScreens();
        restored.readFromNBT(tag);
        LegacyWorldScreens.Television television = restored.television(controller);
        assertNotNull(television);
        assertEquals(activation.television().id(), television.id());
        assertArrayEquals(activation.television().mask(), television.mask());

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
