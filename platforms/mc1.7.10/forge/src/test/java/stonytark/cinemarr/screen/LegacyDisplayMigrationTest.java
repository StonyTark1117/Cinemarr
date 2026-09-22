package stonytark.cinemarr.screen;

import java.lang.reflect.Field;
import java.util.UUID;

import stonytark.cinemarr.core.screen.ScreenFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.block.Block;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.video.DisplaySettingsCodec;
import stonytark.cinemarr.core.video.PixelMapping;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.ResolutionChoice;
import stonytark.cinemarr.core.video.TvDisplaySettings;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDisplayMigrationTest {
    private static final long CONTROLLER = LegacyBlockPos.pack(0, 0, 1);

    @Test void oldAndCorruptSettingsDeferUntilTheControllerIsLoaded() throws Exception {
        for (String saved : new String[]{null, "corrupt", "1|CUSTOM|FIT|DETAILED|CUSTOM||8192|8192|0"}) {
            verifyMigration(saved, null);
            for (QuickTvPreset preset : QuickTvPreset.values()) verifyMigration(saved, preset);
        }
    }

    private void verifyMigration(String saved, QuickTvPreset preset) throws Exception {
        TelevisionLifecycle.reset(null);
        try {
            LegacyWorldScreens original = new LegacyWorldScreens();
            for (int x = 0; x < 4; x++) original.putPixel(x, 0, 0, ScreenFacing.NORTH);
            assertTrue(original.activate(0, 0, 1, UUID.randomUUID()).success());
            original.updatePresentation(CONTROLLER, PresentationMode.FILL);
            original.updateSession(CONTROLLER, "migration-party");
            original.beginQuickTvConstruction(LegacyBlockPos.pack(64, 0, 0), java.util.Collections.singletonList(LegacyBlockPos.pack(65, 0, 0)));
            NBTTagCompound old = save(original);
            old.setInteger("schemaVersion", 3);
            NBTTagCompound tv = old.getTagList("televisions", 10).getCompoundTagAt(0);
            if (saved == null) tv.removeTag("displaySettings"); else tv.setString("displaySettings", saved);
            // Equal dimensions deliberately cannot distinguish old custom and Quick TVs.
            tv.setInteger("renditionWidth", 256); tv.setInteger("renditionHeight", 144);
            TelevisionLifecycle.reset(null);
            LegacyWorldScreens restored = load(old);
            ControllerLevel world = ControllerLevel.create(preset == null ? new LegacyTvControllerBlock()
                    : new LegacyQuickTvBlock(preset));
            Field level = LegacyWorldScreens.class.getDeclaredField("world");
            level.setAccessible(true); level.set(restored, world);
            java.lang.reflect.Method reconcile = LegacyWorldScreens.class.getDeclaredMethod("reconcileRegistrations");
            reconcile.setAccessible(true); reconcile.invoke(restored);
            TvDisplaySettings deferred = restored.television(CONTROLLER).displaySettings();
            assertEquals(TvDisplaySettings.Origin.UNKNOWN, deferred.origin());
            assertEquals(0, world.blockReads, "Unloaded classification must not read/load the controller block");
            NBTTagCompound deferredSave = save(restored);
            assertEquals(4, deferredSave.getInteger("schemaVersion"));
            assertEquals(old.getTag("quickTvConstructions"), deferredSave.getTag("quickTvConstructions"));
            assertEquals(new UUID(tv.getLong("idMost"), tv.getLong("idLeast")), restored.television(CONTROLLER).id());
            assertEquals(1, TelevisionLifecycle.count(new UUID(tv.getLong("ownerMost"), tv.getLong("ownerLeast"))));
            assertEquals("migration-party", restored.television(CONTROLLER).sessionName());

            world.loaded = true;
            TvDisplaySettings classified = restored.television(CONTROLLER).displaySettings();
            assertEquals(preset == null ? TvDisplaySettings.Origin.CUSTOM : TvDisplaySettings.Origin.QUICK, classified.origin());
            assertEquals(PresentationMode.FILL, classified.layout());
            assertEquals(PixelMapping.DETAILED, classified.mapping());
            assertEquals(preset == null ? ResolutionChoice.AUTO : ResolutionChoice.preset(preset.id()), classified.resolution());
            TvDisplaySettings edited = classified.apply(classified.revision(), PresentationMode.STRETCH,
                    preset == null ? PixelMapping.ONE_PIXEL_PER_BLOCK : PixelMapping.DETAILED,
                    preset == null ? ResolutionChoice.custom(17, 11) : classified.resolution());
            restored.updateDisplay(CONTROLLER, new TvDisplaySettings(edited.origin(), edited.layout(),
                    edited.mapping(), edited.resolution(), classified.revision()));
            String expected = DisplaySettingsCodec.encode(restored.television(CONTROLLER).displaySettings());
            assertTrue(restored.activate(0, 0, 1, UUID.randomUUID()).success());
            assertEquals(expected, DisplaySettingsCodec.encode(restored.television(CONTROLLER).displaySettings()));
            assertEquals(new UUID(tv.getLong("ownerMost"), tv.getLong("ownerLeast")), restored.television(CONTROLLER).owner());
            NBTTagCompound resaved = save(restored);
            LegacyWorldScreens restarted = load(resaved);
            assertEquals(expected, DisplaySettingsCodec.encode(restarted.television(CONTROLLER).displaySettings()));
            assertEquals(old.getTag("quickTvConstructions"), resaved.getTag("quickTvConstructions"));
        } finally { TelevisionLifecycle.reset(null); }
    }

    private static NBTTagCompound save(LegacyWorldScreens value) {
        NBTTagCompound tag = new NBTTagCompound(); value.writeToNBT(tag); return tag;
    }
    private static LegacyWorldScreens load(NBTTagCompound tag) {
        LegacyWorldScreens value = new LegacyWorldScreens(); value.readFromNBT(tag); return value;
    }

    /** Only controller reads are available; no server, chunks, I/O or scheduler is constructed. */
    private static final class ControllerLevel extends WorldServer {
        private boolean loaded;
        private int blockReads;
        private Block controllerBlock;
        private ControllerLevel() { super(null, null, "unused-test-world", 0, null, null); }
        static ControllerLevel create(Block block) throws Exception {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            ControllerLevel value = (ControllerLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ControllerLevel.class);
            value.controllerBlock = block;
            Field provider = net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true);
            provider.set(value, new net.minecraft.world.WorldProviderSurface());
            return value;
        }
        @Override public boolean blockExists(int x, int y, int z) { return loaded; }
        @Override public Block getBlock(int x, int y, int z) {
            assertTrue(loaded, "Attempted controller access while its chunk was unavailable");
            assertEquals(CONTROLLER, LegacyBlockPos.pack(x, y, z)); blockReads++; return controllerBlock;
        }
    }
}
