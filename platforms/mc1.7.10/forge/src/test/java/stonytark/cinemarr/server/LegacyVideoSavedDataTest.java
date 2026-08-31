package stonytark.cinemarr.server;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void dormantSessionsUseDurableLeastRecentlyUsedEviction() {
        LegacyVideoSavedData data = new LegacyVideoSavedData();
        for (int index = 0; index < 64; index++) data.put(record("session-" + index));
        assertEquals("session-0", data.record("session-0").sessionName());
        data.put(record("session-64"));
        assertNull(data.record("session-1"));
        assertEquals(64, data.records().size());
        assertEquals("session-0", data.records().get(62).sessionName());
        assertEquals("session-64", data.records().get(63).sessionName());
    }

    @Test void migratesSchemaZeroDefaultsAndClampsCheckpoint(){LegacyVideoSavedData source=new LegacyVideoSavedData();source.put(new LegacyVideoSavedData.Record("old","movies",item("1","Movie"),999_999,false,-1,-1,Collections.<QueuedVideo>emptyList()));NBTTagCompound tag=new NBTTagCompound();source.writeToNBT(tag);tag.removeTag("schemaVersion");tag.getTagList("sessions",10).getCompoundTagAt(0).removeTag("audioStreamId");LegacyVideoSavedData restored=new LegacyVideoSavedData();restored.readFromNBT(tag);assertEquals(60_000,restored.record("old").positionMs());assertEquals(-1,restored.record("old").audioStreamId());}

    @Test void rejectsFutureSchemaCorruptKindsAndOversizedStrings(){NBTTagCompound future=new NBTTagCompound();future.setInteger("schemaVersion",999);LegacyVideoSavedData data=new LegacyVideoSavedData();data.put(record("existing"));data.readFromNBT(future);assertTrue(data.records().isEmpty());NBTTagCompound root=new NBTTagCompound();NBTTagCompound invalid=new NBTTagCompound();invalid.setString("name",String.join("",Collections.nCopies(65,"x")));invalid.setString("library","movies");invalid.setString("kind","NOT_MEDIA");NBTTagListCompat.add(root,invalid);data.readFromNBT(root);assertTrue(data.records().isEmpty());}

    private static final class NBTTagListCompat {static void add(NBTTagCompound root,NBTTagCompound value){net.minecraft.nbt.NBTTagList list=new net.minecraft.nbt.NBTTagList();list.appendTag(value);root.setTag("sessions",list);}}

    private static LegacyVideoSavedData.Record record(String name) {
        return new LegacyVideoSavedData.Record(name, "movies", item(name, "Movie"), 0, true, -1, -1,
                Collections.<QueuedVideo>emptyList());
    }

    private static VideoMediaItem item(String key, String title) {
        return new VideoMediaItem(MediaKind.MOVIE, key, title, "", "PG", 0, 60_000, "", 0);
    }
}
