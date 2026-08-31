package stonytark.cinemarr.server;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Durable video session checkpoints and queues for Forge 1.7.10. */
public final class LegacyVideoSavedData extends WorldSavedData {
    public static final String DATA_NAME = "cinemarr_video_sessions";
    private static final int MAX_SESSIONS = 64;
    private static final int SCHEMA_VERSION=1,MAX_NAME=64,MAX_LIBRARY=128,MAX_KEY=512,MAX_TITLE=512;
    private static final long MAX_DURATION_MS=7L*24L*60L*60L*1_000L;
    private final Map<String, Record> sessions = new LinkedHashMap<String, Record>(16, 0.75F, true);

    public LegacyVideoSavedData() { this(DATA_NAME); }
    public LegacyVideoSavedData(String name) { super(name); }

    public static LegacyVideoSavedData get(MinecraftServer server) {
        MapStorage storage = server.worldServerForDimension(0).mapStorage;
        LegacyVideoSavedData value = (LegacyVideoSavedData) storage.loadData(LegacyVideoSavedData.class, DATA_NAME);
        if (value == null) { value = new LegacyVideoSavedData(DATA_NAME); storage.setData(DATA_NAME, value); value.markDirty(); }
        return value;
    }

    public List<Record> records() { return Collections.unmodifiableList(new ArrayList<Record>(sessions.values())); }
    public Record record(String name) { Record value = sessions.get(name); if (value != null) markDirty(); return value; }
    public void put(Record value) {
        if (value == null) return;
        sessions.remove(value.sessionName());
        sessions.put(value.sessionName(), value);
        while (sessions.size() > MAX_SESSIONS) sessions.remove(sessions.keySet().iterator().next());
        markDirty();
    }
    public void remove(String name) { if (sessions.remove(name) != null) markDirty(); }
    public void retain(Set<String> names) {
        java.util.Iterator<String> iterator = sessions.keySet().iterator(); boolean changed = false;
        while (iterator.hasNext()) if (!names.contains(iterator.next())) { iterator.remove(); changed = true; }
        if (changed) markDirty();
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("schemaVersion", SCHEMA_VERSION); NBTTagList values = new NBTTagList();
        for (Record record : sessions.values()) values.appendTag(saveRecord(record));
        tag.setTag("sessions", values);
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        sessions.clear();if(tag.hasKey("schemaVersion")&&tag.getInteger("schemaVersion")>SCHEMA_VERSION)return;NBTTagList values = tag.getTagList("sessions", 10);
        for (int index = 0; index < values.tagCount() && sessions.size() < MAX_SESSIONS; index++) {
            Record value = loadRecord(values.getCompoundTagAt(index));
            if (value != null) sessions.put(value.sessionName(), value);
        }
    }

    private static NBTTagCompound saveRecord(Record value) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("name", value.sessionName); tag.setString("library", value.libraryId);
        tag.setLong("positionMs", value.positionMs); tag.setBoolean("paused", value.paused); tag.setInteger("audioStreamId", value.audioStreamId);
        tag.setInteger("subtitleStreamId", value.subtitleStreamId); saveItem(tag, value.item); NBTTagList queue = new NBTTagList();
        for (QueuedVideo entry : value.queue) { NBTTagCompound queued = new NBTTagCompound(); queued.setString("library", entry.libraryId()); saveItem(queued, entry.item()); queue.appendTag(queued); }
        tag.setTag("queue", queue); return tag;
    }

    private static Record loadRecord(NBTTagCompound tag) {
        try {
            String name = tag.getString("name").trim(), library = tag.getString("library").trim(); VideoMediaItem item = loadItem(tag);
            if (name.isEmpty() || name.length()>MAX_NAME || library.isEmpty() || library.length()>MAX_LIBRARY || item == null) return null;
            List<QueuedVideo> queue = new ArrayList<QueuedVideo>(); NBTTagList values = tag.getTagList("queue", 10);
            for (int index = 0; index < values.tagCount() && index < 500; index++) {
                NBTTagCompound queued = values.getCompoundTagAt(index); VideoMediaItem queuedItem = loadItem(queued);
                String queuedLibrary = queued.getString("library").trim();
                if (queuedItem != null && !queuedLibrary.isEmpty()&&queuedLibrary.length()<=MAX_LIBRARY) queue.add(new QueuedVideo(queuedLibrary, queuedItem));
            }
            long position=Math.max(0,tag.getLong("positionMs"));if(item.durationMs()>0)position=Math.min(position,item.durationMs());
            return new Record(name, library, item, position, tag.getBoolean("paused"),
                    tag.hasKey("audioStreamId") ? tag.getInteger("audioStreamId") : -1,
                    tag.hasKey("subtitleStreamId") ? tag.getInteger("subtitleStreamId") : -1, queue);
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static void saveItem(NBTTagCompound tag, VideoMediaItem item) {
        tag.setString("kind", item.kind().name()); tag.setString("key", item.key()); tag.setString("title", item.title());
        tag.setString("parentTitle", item.parentTitle()); tag.setString("contentRating", item.contentRating());
        tag.setInteger("index", item.index()); tag.setLong("durationMs", item.durationMs()); tag.setString("seriesKey", item.seriesKey());
        tag.setInteger("parentIndex", item.parentIndex());
    }
    private static VideoMediaItem loadItem(NBTTagCompound tag) {
        try {String key=tag.getString("key"),title=tag.getString("title"),parent=tag.getString("parentTitle"),rating=tag.getString("contentRating"),series=tag.getString("seriesKey");long duration=tag.getLong("durationMs");
            if(key.isEmpty()||key.length()>MAX_KEY||title.length()>MAX_TITLE||parent.length()>MAX_TITLE||rating.length()>64||series.length()>MAX_KEY||duration<0||duration>MAX_DURATION_MS)return null;
            return new VideoMediaItem(MediaKind.valueOf(tag.getString("kind")),key,title,parent,rating,tag.getInteger("index"),duration,series,tag.getInteger("parentIndex")); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    public static final class Record {
        private final String sessionName, libraryId;
        private final VideoMediaItem item;
        private final long positionMs;
        private final boolean paused;
        private final int audioStreamId, subtitleStreamId;
        private final List<QueuedVideo> queue;
        public Record(String name, String library, VideoMediaItem item, long position, boolean paused, int audio, int subtitle,
                      List<QueuedVideo> queue) {
            sessionName = name; libraryId = library; this.item = item; positionMs = position; this.paused = paused;
            audioStreamId = audio; subtitleStreamId = subtitle; this.queue = Collections.unmodifiableList(new ArrayList<QueuedVideo>(queue));
        }
        public String sessionName() { return sessionName; }
        public String libraryId() { return libraryId; }
        public VideoMediaItem item() { return item; }
        public long positionMs() { return positionMs; }
        public boolean paused() { return paused; }
        public int audioStreamId() { return audioStreamId; }
        public int subtitleStreamId() { return subtitleStreamId; }
        public List<QueuedVideo> queue() { return queue; }
    }
}
