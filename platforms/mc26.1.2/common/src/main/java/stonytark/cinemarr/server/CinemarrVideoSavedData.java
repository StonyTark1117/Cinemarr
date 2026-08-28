package stonytark.cinemarr.server;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.QueuedVideo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable video checkpoints only; Plex URLs, tokens, manifests, and media bytes are never saved. */
public final class CinemarrVideoSavedData extends SavedData {
    public static final int SCHEMA_VERSION=1;
    private static final int MAX_SESSIONS=64;
    private static final Codec<CinemarrVideoSavedData> CODEC=CompoundTag.CODEC.xmap(CinemarrVideoSavedData::load,CinemarrVideoSavedData::saveTag);
    public static final SavedDataType<CinemarrVideoSavedData> TYPE=new SavedDataType<>(
            Identifier.fromNamespaceAndPath("cinemarr","cinemarr_video_sessions"),
            CinemarrVideoSavedData::new,CODEC,DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<String,Record> sessions=new LinkedHashMap<>();

    public static CinemarrVideoSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(TYPE);}
    public List<Record> records(){return Collections.unmodifiableList(new ArrayList<>(sessions.values()));}
    public Record record(String name){return sessions.get(name);}
    public void put(Record value){if(value==null)return;sessions.remove(value.sessionName());sessions.put(value.sessionName(),value);while(sessions.size()>MAX_SESSIONS)sessions.remove(sessions.keySet().iterator().next());setDirty();}
    public void remove(String name){if(sessions.remove(name)!=null)setDirty();}
    public void retain(java.util.Set<String> names){if(sessions.keySet().removeIf(name->!names.contains(name)))setDirty();}

    private CompoundTag saveTag(){CompoundTag tag=new CompoundTag();
        tag.putInt("schemaVersion",SCHEMA_VERSION);ListTag values=new ListTag();for(Record record:sessions.values())values.add(save(record));tag.put("sessions",values);return tag;
    }
    public static CinemarrVideoSavedData load(CompoundTag tag){
        CinemarrVideoSavedData data=new CinemarrVideoSavedData();ListTag values=tag.getListOrEmpty("sessions");
        for(int index=0;index<values.size()&&data.sessions.size()<MAX_SESSIONS;index++){Record record=loadRecord(values.getCompoundOrEmpty(index));if(record!=null)data.sessions.put(record.sessionName(),record);}return data;
    }
    private static CompoundTag save(Record value){
        CompoundTag tag=new CompoundTag();tag.putString("name",value.sessionName);tag.putString("library",value.libraryId);tag.putLong("positionMs",value.positionMs);tag.putBoolean("paused",value.paused);tag.putInt("audioStreamId",value.audioStreamId);tag.putInt("subtitleStreamId",value.subtitleStreamId);saveItem(tag,value.item);
        ListTag queued=new ListTag();for(QueuedVideo entry:value.queue){CompoundTag queuedTag=new CompoundTag();queuedTag.putString("library",entry.libraryId());saveItem(queuedTag,entry.item());queued.add(queuedTag);}tag.put("queue",queued);return tag;
    }
    private static Record loadRecord(CompoundTag tag){
        try{String name=tag.getStringOr("name","").trim(),library=tag.getStringOr("library","").trim(),key=tag.getStringOr("key","").trim();if(name.isEmpty()||library.isEmpty()||key.isEmpty())return null;
            VideoMediaItem item=loadItem(tag);if(item==null)return null;
            List<QueuedVideo> queue=new ArrayList<>();ListTag queued=tag.getListOrEmpty("queue");for(int index=0;index<queued.size()&&queue.size()<500;index++){CompoundTag value=queued.getCompoundOrEmpty(index);VideoMediaItem queuedItem=loadItem(value);String queuedLibrary=value.getStringOr("library","").trim();if(queuedItem!=null&&!queuedLibrary.isEmpty())queue.add(new QueuedVideo(queuedLibrary,queuedItem));}
            return new Record(name,library,item,Math.max(0,tag.getLongOr("positionMs",0L)),tag.getBooleanOr("paused",false),tag.contains("audioStreamId")?tag.getIntOr("audioStreamId",-1):-1,tag.contains("subtitleStreamId")?tag.getIntOr("subtitleStreamId",-1):-1,queue);
        }catch(IllegalArgumentException invalid){return null;}
    }
    private static void saveItem(CompoundTag tag,VideoMediaItem item){tag.putString("kind",item.kind().name());tag.putString("key",item.key());tag.putString("title",item.title());tag.putString("parentTitle",item.parentTitle());tag.putString("contentRating",item.contentRating());tag.putInt("index",item.index());tag.putLong("durationMs",item.durationMs());tag.putString("seriesKey",item.seriesKey());tag.putInt("parentIndex",item.parentIndex());}
    private static VideoMediaItem loadItem(CompoundTag tag){try{return new VideoMediaItem(MediaKind.valueOf(tag.getStringOr("kind","")),tag.getStringOr("key",""),tag.getStringOr("title",""),tag.getStringOr("parentTitle",""),tag.getStringOr("contentRating",""),tag.getIntOr("index",0),Math.max(0,tag.getLongOr("durationMs",0L)),tag.getStringOr("seriesKey",""),tag.getIntOr("parentIndex",0));}catch(IllegalArgumentException invalid){return null;}}

    public static final class Record{
        private final String sessionName,libraryId;private final VideoMediaItem item;private final long positionMs;private final boolean paused;private final int audioStreamId,subtitleStreamId;private final List<QueuedVideo> queue;
        public Record(String sessionName,String libraryId,VideoMediaItem item,long positionMs,boolean paused,int audioStreamId,int subtitleStreamId){this(sessionName,libraryId,item,positionMs,paused,audioStreamId,subtitleStreamId,Collections.emptyList());}
        public Record(String sessionName,String libraryId,VideoMediaItem item,long positionMs,boolean paused,int audioStreamId,int subtitleStreamId,List<QueuedVideo> queue){if(sessionName==null||sessionName.trim().isEmpty()||libraryId==null||libraryId.trim().isEmpty()||item==null)throw new IllegalArgumentException("Invalid saved video session");this.sessionName=sessionName.trim();this.libraryId=libraryId.trim();this.item=item;this.positionMs=Math.max(0,positionMs);this.paused=paused;this.audioStreamId=audioStreamId;this.subtitleStreamId=subtitleStreamId;this.queue=Collections.unmodifiableList(new ArrayList<>(queue==null?Collections.emptyList():queue));}
        public String sessionName(){return sessionName;}public String libraryId(){return libraryId;}public VideoMediaItem item(){return item;}public long positionMs(){return positionMs;}public boolean paused(){return paused;}public int audioStreamId(){return audioStreamId;}public int subtitleStreamId(){return subtitleStreamId;}public List<QueuedVideo> queue(){return queue;}
    }
}
