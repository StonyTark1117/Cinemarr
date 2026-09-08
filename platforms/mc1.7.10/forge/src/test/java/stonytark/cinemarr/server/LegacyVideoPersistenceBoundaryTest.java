package stonytark.cinemarr.server;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVideoPersistenceBoundaryTest {
    @Test void accessedLruOrderSurvivesCompressedSaveReloadAndNextEviction() throws Exception {
        LegacyVideoSavedData original = new LegacyVideoSavedData();
        for (int i = 0; i < 64; i++) original.put(record("session-" + i));
        original.setDirty(false);
        assertNotNull(original.record("session-0"));
        assertTrue(original.isDirty(), "An access that changes eviction order must be persisted");
        LegacyVideoSavedData restored = reload(original);
        assertEquals(names(original), names(restored));
        restored.put(record("session-64"));
        assertFalse(names(restored).contains("session-1"));
        assertTrue(names(restored).contains("session-0"));
        assertEquals(64, restored.records().size());
        LegacyVideoSavedData again = reload(restored);
        assertEquals(names(restored), names(again));
        again.put(record("session-65"));
        assertFalse(names(again).contains("session-2"));
        assertTrue(names(again).contains("session-0"));
    }

    @Test void corruptAndOversizedFieldsAreRejectedIndependentlyWithoutLosingHealthyNeighbors() {
        assertBadRecord(tag -> tag.setString("kind", "NOT_MEDIA"));
        assertBadRecord(tag -> tag.setString("name", repeat(65)));
        assertBadRecord(tag -> tag.setString("library", repeat(129)));
        assertBadRecord(tag -> tag.setString("key", repeat(513)));
        assertBadRecord(tag -> tag.setString("title", repeat(513)));
        assertBadRecord(tag -> tag.setString("parentTitle", repeat(513)));
        assertBadRecord(tag -> tag.setString("contentRating", repeat(65)));
        assertBadRecord(tag -> tag.setString("seriesKey", repeat(513)));
        assertBadRecord(tag -> tag.setLong("durationMs", -1));
        assertBadRecord(tag -> tag.setLong("durationMs", Long.MAX_VALUE));
        assertBadRecord(tag -> tag.setInteger("name", 17));
        assertBadRecord(tag -> tag.setInteger("library", 17));
        assertBadRecord(tag -> tag.setInteger("key", 17));
        assertBadRecord(tag -> tag.setInteger("kind", 17));
        assertBadRecord(tag -> tag.setInteger("title", 17));
        assertBadRecord(tag -> tag.setString("key", ""));
    }

    @Test void oversizedSessionAndQueueListsStayBoundedAfterBinaryRoundTrip() throws Exception {
        NBTTagList sessions = new NBTTagList();
        for (int i = 0; i < 90; i++) sessions.appendTag(savedRecord("session-" + i));
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("sessions", sessions);
        LegacyVideoSavedData restored = new LegacyVideoSavedData();
        restored.readFromNBT(binaryRoundTrip(root));
        assertEquals(64, restored.records().size());
        assertEquals("session-0", restored.records().get(0).sessionName());
        assertEquals("session-63", restored.records().get(63).sessionName());

        NBTTagCompound one = savedRecord("queued");
        NBTTagList queue = new NBTTagList();
        for (int i = 0; i < 530; i++) queue.appendTag(savedRecord("next-" + i));
        one.setTag("queue", queue);
        NBTTagList only = new NBTTagList(); only.appendTag(one); root.setTag("sessions", only);
        restored.readFromNBT(binaryRoundTrip(root));
        assertEquals(500, restored.record("queued").queue().size());
        assertEquals(500, reload(restored).record("queued").queue().size());
    }

    @Test void corruptQueueEntryDoesNotDropHealthyQueueEntriesOrCheckpoint() {
        NBTTagCompound one = savedRecord("queued");
        NBTTagList queue = new NBTTagList();
        queue.appendTag(savedRecord("first"));
        NBTTagCompound invalid = savedRecord("invalid"); invalid.setLong("durationMs", -1);
        queue.appendTag(invalid); queue.appendTag(savedRecord("last"));
        one.setTag("queue", queue);
        LegacyVideoSavedData restored = load(one);
        List<QueuedVideo> entries = restored.record("queued").queue();
        assertEquals(2, entries.size());
        assertEquals("first", entries.get(0).item().key());
        assertEquals("last", entries.get(1).item().key());
        assertEquals(1234, restored.record("queued").positionMs());
    }

    private static void assertBadRecord(Consumer<NBTTagCompound> mutate) {
        NBTTagCompound bad = savedRecord("bad"); mutate.accept(bad);
        LegacyVideoSavedData restored = load(savedRecord("before"), bad, savedRecord("after"));
        assertEquals(java.util.Arrays.asList("before", "after"), names(restored));
    }
    private static List<String> names(LegacyVideoSavedData data) {
        return data.records().stream().map(LegacyVideoSavedData.Record::sessionName).collect(Collectors.toList());
    }
    private static String repeat(int length) { return String.join("", Collections.nCopies(length, "x")); }
    private static LegacyVideoSavedData.Record record(String name) {
        return new LegacyVideoSavedData.Record(name, "movies",
                new VideoMediaItem(MediaKind.MOVIE, name, "Movie", "", "PG", 0, 60_000),
                1234, true, -1, -1, Collections.<QueuedVideo>emptyList());
    }
    private static NBTTagCompound savedRecord(String name) {
        LegacyVideoSavedData data = new LegacyVideoSavedData(); data.put(record(name));
        NBTTagCompound root = new NBTTagCompound(); data.writeToNBT(root);
        return root.getTagList("sessions", 10).getCompoundTagAt(0);
    }
    private static LegacyVideoSavedData load(NBTTagCompound... entries) {
        NBTTagCompound root = new NBTTagCompound(); NBTTagList list = new NBTTagList();
        for (NBTTagCompound entry : entries) list.appendTag(entry);
        root.setTag("sessions", list);
        LegacyVideoSavedData data = new LegacyVideoSavedData(); data.readFromNBT(root); return data;
    }
    private static LegacyVideoSavedData reload(LegacyVideoSavedData original) throws Exception {
        NBTTagCompound root = new NBTTagCompound(); original.writeToNBT(root);
        LegacyVideoSavedData restored = new LegacyVideoSavedData();
        restored.readFromNBT(binaryRoundTrip(root)); return restored;
    }
    private static NBTTagCompound binaryRoundTrip(NBTTagCompound root) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, bytes);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
