package stonytark.cinemarr.screen;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.video.DisplaySettingsCodec;
import stonytark.cinemarr.core.video.PixelMapping;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.core.video.ResolutionChoice;
import stonytark.cinemarr.core.video.TvDisplaySettings;

import static org.junit.jupiter.api.Assertions.*;

class DisplayMigrationTest {
    private static final BlockPos CONTROLLER = new BlockPos(0, 0, 1);

    @Test void oldAndCorruptSettingsDeferUntilTheControllerIsLoaded() throws Exception {
        for (String saved : new String[]{null, "corrupt", "1|CUSTOM|FIT|DETAILED|CUSTOM||8192|8192|0"}) {
            verifyMigration(saved, null);
            for (QuickTvPreset preset : QuickTvPreset.values()) verifyMigration(saved, preset);
        }
    }

    private void verifyMigration(String saved, QuickTvPreset preset) throws Exception {
        TelevisionLifecycle.reset(null);
        try {
            CinemarrWorldScreens original = new CinemarrWorldScreens();
            for (int x = 0; x < 4; x++) original.putPixel(new BlockPos(x, 0, 0), Direction.NORTH);
            assertTrue(original.activate(CONTROLLER, UUID.randomUUID()).success());
            original.updatePresentation(CONTROLLER, PresentationMode.FILL);
            original.updateSession(CONTROLLER, "migration-party");
            original.beginQuickTvConstruction(new BlockPos(64, 0, 0), List.of(new BlockPos(65, 0, 0)));
            CompoundTag old = original.save(new CompoundTag(), null);
            old.putInt("schemaVersion", 3);
            CompoundTag tv = old.getList("televisions", 10).getCompound(0);
            if (saved == null) tv.remove("displaySettings"); else tv.putString("displaySettings", saved);
            // Equal dimensions deliberately cannot distinguish old custom and Quick TVs.
            tv.putInt("renditionWidth", 256); tv.putInt("renditionHeight", 144);
            TelevisionLifecycle.reset(null);
            CinemarrWorldScreens restored = CinemarrWorldScreens.load(old, null);
            ControllerLevel world = ControllerLevel.create(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                    "cinemarr", preset == null ? "tv_controller" : "quick_tv_" + preset.id())));
            Field level = CinemarrWorldScreens.class.getDeclaredField("level");
            level.setAccessible(true); level.set(restored, world);
            restored.reconcileRegistrations();
            TvDisplaySettings deferred = restored.television(CONTROLLER).displaySettings();
            assertEquals(TvDisplaySettings.Origin.UNKNOWN, deferred.origin());
            assertEquals(0, world.blockReads, "Unloaded classification must not read/load the controller block");
            CompoundTag deferredSave = restored.save(new CompoundTag(), null);
            assertEquals(4, deferredSave.getInt("schemaVersion"));
            assertEquals(old.get("quickTvConstructions"), deferredSave.get("quickTvConstructions"));
            assertEquals(tv.getUUID("id"), restored.television(CONTROLLER).id());
            assertEquals(1, TelevisionLifecycle.count(tv.getUUID("owner")));
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
            assertTrue(restored.activate(CONTROLLER, UUID.randomUUID()).success());
            assertEquals(expected, DisplaySettingsCodec.encode(restored.television(CONTROLLER).displaySettings()));
            assertEquals(tv.getUUID("owner"), restored.television(CONTROLLER).owner());
            CompoundTag resaved = restored.save(new CompoundTag(), null);
            CinemarrWorldScreens restarted = CinemarrWorldScreens.load(resaved, null);
            assertEquals(expected, DisplaySettingsCodec.encode(restarted.television(CONTROLLER).displaySettings()));
            assertEquals(old.get("quickTvConstructions"), resaved.get("quickTvConstructions"));
        } finally { TelevisionLifecycle.reset(null); }
    }

    /** Only controller reads are available; no server, chunks, I/O or scheduler is constructed. */
    private static final class ControllerLevel extends ServerLevel {
        private boolean loaded;
        private int blockReads;
        private BlockState controllerState;
        private ControllerLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        static ControllerLevel create(Block block) throws Exception {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            ControllerLevel value = (ControllerLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ControllerLevel.class);
            value.controllerState = block.defaultBlockState();
            return value;
        }
        @Override public boolean hasChunkAt(BlockPos pos) { return loaded; }
        @Override public BlockState getBlockState(BlockPos pos) {
            assertTrue(loaded, "Attempted controller access while its chunk was unavailable");
            assertEquals(CONTROLLER, pos); blockReads++; return controllerState;
        }
    }
}
