package stonytark.cinemarr.server;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVideoSavedDataTest {
    @Test
    void roundTripsVideoCheckpointStreamsAndQueue() {
        VideoMediaItem movie = item("42", "Movie");
        LegacyVideoSavedData source = new LegacyVideoSavedData();
        source.put(new LegacyVideoSavedData.Record("lounge", "family", movie, 12_345, true, 7, 9,
                Collections.singletonList(new QueuedVideo("family", item("43", "Next")))));
        NBTTagCompound tag = new NBTTagCompound(); source.writeToNBT(tag);
        LegacyVideoSavedData restored = new LegacyVideoSavedData(); restored.readFromNBT(tag);
        LegacyVideoSavedData.Record value = restored.record("lounge");
        assertEquals("family", value.libraryId()); assertEquals("42", value.item().key());
        assertEquals(12_345, value.positionMs()); assertTrue(value.paused());
        assertEquals(7, value.audioStreamId()); assertEquals(9, value.subtitleStreamId());
        assertEquals("43", value.queue().get(0).item().key());
    }

    private static VideoMediaItem item(String key, String title) {
        return new VideoMediaItem(MediaKind.MOVIE, key, title, "", "PG", 0, 60_000, "", 0);
    }
}
