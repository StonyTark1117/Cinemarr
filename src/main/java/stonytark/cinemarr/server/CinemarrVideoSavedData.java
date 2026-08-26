package stonytark.cinemarr.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable video checkpoints only; Plex URLs, tokens, manifests, and media bytes are never saved. */
public final class CinemarrVideoSavedData extends SavedData {
    public static final int SCHEMA_VERSION=1;
    private static final int MAX_SESSIONS=64;
    public static final Factory<CinemarrVideoSavedData> FACTORY=new Factory<>(CinemarrVideoSavedData::new,CinemarrVideoSavedData::load,null);
    private final Map<String,Record> sessions=new LinkedHashMap<>();

    public static CinemarrVideoSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"cinemarr_video_sessions");}
    public List<Record> records(){return Collections.unmodifiableList(new ArrayList<>(sessions.values()));}
    public Record record(String name){return sessions.get(name);}
    public void put(Record value){if(value==null)return;sessions.put(value.sessionName(),value);while(sessions.size()>MAX_SESSIONS)sessions.remove(sessions.keySet().iterator().next());setDirty();}
    public void remove(String name){if(sessions.remove(name)!=null)setDirty();}
    public void retain(java.util.Set<String> names){if(sessions.keySet().removeIf(name->!names.contains(name)))setDirty();}

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("schemaVersion",SCHEMA_VERSION);ListTag values=new ListTag();for(Record record:sessions.values())values.add(save(record));tag.put("sessions",values);return tag;
    }
    public static CinemarrVideoSavedData load(CompoundTag tag,HolderLookup.Provider registries){
        CinemarrVideoSavedData data=new CinemarrVideoSavedData();ListTag values=tag.getList("sessions",Tag.TAG_COMPOUND);
        for(int index=0;index<values.size()&&data.sessions.size()<MAX_SESSIONS;index++){Record record=loadRecord(values.getCompound(index));if(record!=null)data.sessions.put(record.sessionName(),record);}return data;
    }
    private static CompoundTag save(Record value){
        CompoundTag tag=new CompoundTag();tag.putString("name",value.sessionName);tag.putString("library",value.libraryId);tag.putLong("positionMs",value.positionMs);tag.putBoolean("paused",value.paused);tag.putInt("audioStreamId",value.audioStreamId);tag.putInt("subtitleStreamId",value.subtitleStreamId);
        VideoMediaItem item=value.item;tag.putString("kind",item.kind().name());tag.putString("key",item.key());tag.putString("title",item.title());tag.putString("parentTitle",item.parentTitle());tag.putString("contentRating",item.contentRating());tag.putInt("index",item.index());tag.putLong("durationMs",item.durationMs());return tag;
    }
    private static Record loadRecord(CompoundTag tag){
        try{String name=tag.getString("name").trim(),library=tag.getString("library").trim(),key=tag.getString("key").trim();if(name.isEmpty()||library.isEmpty()||key.isEmpty())return null;
            VideoMediaItem item=new VideoMediaItem(MediaKind.valueOf(tag.getString("kind")),key,tag.getString("title"),tag.getString("parentTitle"),tag.getString("contentRating"),tag.getInt("index"),Math.max(0,tag.getLong("durationMs")));
            return new Record(name,library,item,Math.max(0,tag.getLong("positionMs")),tag.getBoolean("paused"),tag.contains("audioStreamId")?tag.getInt("audioStreamId"):-1,tag.contains("subtitleStreamId")?tag.getInt("subtitleStreamId"):-1);
        }catch(IllegalArgumentException invalid){return null;}
    }

    public static final class Record{
        private final String sessionName,libraryId;private final VideoMediaItem item;private final long positionMs;private final boolean paused;private final int audioStreamId,subtitleStreamId;
        public Record(String sessionName,String libraryId,VideoMediaItem item,long positionMs,boolean paused,int audioStreamId,int subtitleStreamId){if(sessionName==null||sessionName.trim().isEmpty()||libraryId==null||libraryId.trim().isEmpty()||item==null)throw new IllegalArgumentException("Invalid saved video session");this.sessionName=sessionName.trim();this.libraryId=libraryId.trim();this.item=item;this.positionMs=Math.max(0,positionMs);this.paused=paused;this.audioStreamId=audioStreamId;this.subtitleStreamId=subtitleStreamId;}
        public String sessionName(){return sessionName;}public String libraryId(){return libraryId;}public VideoMediaItem item(){return item;}public long positionMs(){return positionMs;}public boolean paused(){return paused;}public int audioStreamId(){return audioStreamId;}public int subtitleStreamId(){return subtitleStreamId;}
    }
}
