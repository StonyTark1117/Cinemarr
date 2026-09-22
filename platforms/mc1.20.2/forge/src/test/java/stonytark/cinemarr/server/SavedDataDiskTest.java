package stonytark.cinemarr.server;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import static org.junit.jupiter.api.Assertions.*;

class SavedDataDiskTest {
    @TempDir Path directory;

    @Test void registeredTelevisionAndPlaybackSurviveFreshDiskStorage() throws Exception {
        SharedConstants.tryDetectVersion();
        TelevisionLifecycle.reset(null);
        try {
            CinemarrWorldScreens screens = new CinemarrWorldScreens();
            for (int x = 0; x < 4; x++) screens.putPixel(new BlockPos(x, 0, 0), Direction.NORTH);
            BlockPos controller = new BlockPos(0, 0, 1);
            assertTrue(screens.activate(controller, UUID.randomUUID()).success());
            screens.updatePresentation(controller, PresentationMode.FILL);
            screens.updateSession(controller, "saved-party");
            CinemarrVideoSavedData video = new CinemarrVideoSavedData();
            video.put(new CinemarrVideoSavedData.Record("saved-party", "movies",
                    new VideoMediaItem(MediaKind.MOVIE, "42", "Movie", "", "PG", 0, 90_000),
                    12_000, true, 101, -1, List.of()));
            var field = CinemarrWorldScreens.class.getDeclaredField("FACTORY");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var screenFactory = (SavedData.Factory<CinemarrWorldScreens>) field.get(null);
            // Exercise Minecraft's compressed-file loader, not just our NBT codecs.
            // Older version metadata also verifies vanilla fixes preserve custom fields.
            for (int version : new int[]{SharedConstants.getCurrentVersion().getDataVersion().getVersion(), 1343}) {
                assertDiskRoundTrip("cinemarr_screens", screens, screenFactory, version);
                assertDiskRoundTrip("cinemarr_video_sessions", video, CinemarrVideoSavedData.FACTORY, version);
            }
        } finally {
            TelevisionLifecycle.reset(null);
        }
    }

    private <T extends SavedData> void assertDiskRoundTrip(String id, T original,
            SavedData.Factory<T> factory, int version) throws Exception {
        CompoundTag expected = original.save(new CompoundTag());
        CompoundTag file = new CompoundTag();
        file.put("data", expected.copy());
        file.putInt("DataVersion", version);
        NbtIo.writeCompressed(file, directory.resolve(id + ".dat").toFile());
        DimensionDataStorage fresh = new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer());
        T restored = fresh.get(factory, id);
        assertNotNull(restored, id + " was discarded by the disk loader");
        assertEquals(expected, restored.save(new CompoundTag()), id + " changed during disk reload");
    }
}
