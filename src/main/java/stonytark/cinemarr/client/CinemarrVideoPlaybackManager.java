package stonytark.cinemarr.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns one decoder/texture pipeline per distinct visible watch-party generation. */
public final class CinemarrVideoPlaybackManager implements AutoCloseable {
    private final Map<CinemarrVideoClientState.StreamKey,CinemarrVideoPlayback> pipelines=new LinkedHashMap<>();

    public void tick(CinemarrVideoClientState state){
        long now=System.currentTimeMillis();
        java.util.Set<CinemarrVideoClientState.StreamKey> current=new java.util.LinkedHashSet<>();
        for(CinemarrVideoClientState.StreamState stream:state.streamStates()){
            stream.tick(now);current.add(stream.key());pipelines.computeIfAbsent(stream.key(),ignored->new CinemarrVideoPlayback()).tick(stream);
        }
        for(CinemarrVideoClientState.StreamKey key:new ArrayList<>(pipelines.keySet()))if(!current.contains(key)){pipelines.remove(key).close();}
    }
    CinemarrVideoPlayback pipeline(CinemarrVideoClientState.StreamKey key){return pipelines.get(key);}
    Map<CinemarrVideoClientState.StreamKey,CinemarrVideoPlayback> pipelines(){return java.util.Collections.unmodifiableMap(pipelines);}
    boolean hasPresentedFrame(){return evidencePipeline()!=null;}
    boolean presentedFrameCaughtUp(){CinemarrVideoPlayback value=evidencePipeline();return value!=null&&value.caughtUp();}
    String presentedFrameSha256(){CinemarrVideoPlayback value=evidencePipeline();return value==null?"":value.lastFrameSha256();}
    long presentedFrameTimeUs(){CinemarrVideoPlayback value=evidencePipeline();return value==null?0:value.lastPresentedUs();}
    private CinemarrVideoPlayback evidencePipeline(){for(CinemarrVideoPlayback value:pipelines.values())if(value.texture().ready()&&!value.lastFrameSha256().isEmpty())return value;return null;}
    public void reset(){for(CinemarrVideoPlayback value:pipelines.values())value.close();pipelines.clear();}
    @Override public void close(){reset();}
}
