package stonytark.cinemarr.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CinemarrVideoSavedData extends SavedData {
    private static final int MAX_SESSIONS=64;
    private final Map<String,Record> sessions=new LinkedHashMap<>(16,0.75F,true);
    public static CinemarrVideoSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(CinemarrVideoSavedData::load,CinemarrVideoSavedData::new,"cinemarr_video_sessions");}
    public List<Record> records(){return Collections.unmodifiableList(new ArrayList<>(sessions.values()));}
    public Record record(String name){Record value=sessions.get(name);if(value!=null)setDirty();return value;}
    public void put(Record value){if(value==null)return;sessions.remove(value.sessionName());sessions.put(value.sessionName(),value);while(sessions.size()>MAX_SESSIONS)sessions.remove(sessions.keySet().iterator().next());setDirty();}
    public void remove(String name){if(sessions.remove(name)!=null)setDirty();}
    public void retain(java.util.Set<String> names){if(sessions.keySet().removeIf(name->!names.contains(name)))setDirty();}
    @Override public CompoundTag save(CompoundTag tag){tag.putInt("schemaVersion",1);ListTag values=new ListTag();for(Record record:sessions.values())values.add(saveRecord(record));tag.put("sessions",values);return tag;}
    public static CinemarrVideoSavedData load(CompoundTag tag){CinemarrVideoSavedData data=new CinemarrVideoSavedData();ListTag values=tag.getList("sessions",Tag.TAG_COMPOUND);for(int i=0;i<values.size()&&data.sessions.size()<MAX_SESSIONS;i++){Record value=loadRecord(values.getCompound(i));if(value!=null)data.sessions.put(value.sessionName(),value);}return data;}
    private static CompoundTag saveRecord(Record value){CompoundTag tag=new CompoundTag();tag.putString("name",value.sessionName);tag.putString("library",value.libraryId);tag.putLong("positionMs",value.positionMs);tag.putBoolean("paused",value.paused);tag.putInt("audioStreamId",value.audioStreamId);tag.putInt("subtitleStreamId",value.subtitleStreamId);saveItem(tag,value.item);ListTag queue=new ListTag();for(QueuedVideo entry:value.queue){CompoundTag q=new CompoundTag();q.putString("library",entry.libraryId());saveItem(q,entry.item());queue.add(q);}tag.put("queue",queue);return tag;}
    private static Record loadRecord(CompoundTag tag){try{String name=tag.getString("name").trim(),library=tag.getString("library").trim();VideoMediaItem item=loadItem(tag);if(name.isEmpty()||library.isEmpty()||item==null)return null;List<QueuedVideo> queue=new ArrayList<>();ListTag values=tag.getList("queue",Tag.TAG_COMPOUND);for(int i=0;i<values.size()&&i<500;i++){CompoundTag q=values.getCompound(i);VideoMediaItem qi=loadItem(q);String ql=q.getString("library").trim();if(qi!=null&&!ql.isEmpty())queue.add(new QueuedVideo(ql,qi));}return new Record(name,library,item,Math.max(0,tag.getLong("positionMs")),tag.getBoolean("paused"),tag.contains("audioStreamId")?tag.getInt("audioStreamId"):-1,tag.contains("subtitleStreamId")?tag.getInt("subtitleStreamId"):-1,queue);}catch(IllegalArgumentException invalid){return null;}}
    private static void saveItem(CompoundTag tag,VideoMediaItem item){tag.putString("kind",item.kind().name());tag.putString("key",item.key());tag.putString("title",item.title());tag.putString("parentTitle",item.parentTitle());tag.putString("contentRating",item.contentRating());tag.putInt("index",item.index());tag.putLong("durationMs",item.durationMs());tag.putString("seriesKey",item.seriesKey());tag.putInt("parentIndex",item.parentIndex());}
    private static VideoMediaItem loadItem(CompoundTag tag){try{return new VideoMediaItem(MediaKind.valueOf(tag.getString("kind")),tag.getString("key"),tag.getString("title"),tag.getString("parentTitle"),tag.getString("contentRating"),tag.getInt("index"),Math.max(0,tag.getLong("durationMs")),tag.getString("seriesKey"),tag.getInt("parentIndex"));}catch(IllegalArgumentException invalid){return null;}}
    public static final class Record {private final String sessionName,libraryId;private final VideoMediaItem item;private final long positionMs;private final boolean paused;private final int audioStreamId,subtitleStreamId;private final List<QueuedVideo> queue;public Record(String name,String library,VideoMediaItem item,long position,boolean paused,int audio,int subtitle,List<QueuedVideo> queue){this.sessionName=name;this.libraryId=library;this.item=item;this.positionMs=position;this.paused=paused;this.audioStreamId=audio;this.subtitleStreamId=subtitle;this.queue=Collections.unmodifiableList(new ArrayList<>(queue));}public String sessionName(){return sessionName;}public String libraryId(){return libraryId;}public VideoMediaItem item(){return item;}public long positionMs(){return positionMs;}public boolean paused(){return paused;}public int audioStreamId(){return audioStreamId;}public int subtitleStreamId(){return subtitleStreamId;}public List<QueuedVideo> queue(){return queue;}}
}
