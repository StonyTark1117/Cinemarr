package stonytark.cinemarr.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns one decoder/texture pipeline per distinct visible watch-party generation. */
public final class CinemarrVideoPlaybackManager implements AutoCloseable {
    private final Map<CinemarrVideoClientState.StreamKey,CinemarrVideoPlayback> pipelines=new LinkedHashMap<>();

    public void tick(CinemarrVideoClientState state){
        java.util.Set<CinemarrVideoClientState.StreamKey> current=new java.util.LinkedHashSet<>();
        for(CinemarrVideoClientState.StreamState stream:state.streamStates()){
            current.add(stream.key());pipelines.computeIfAbsent(stream.key(),ignored->new CinemarrVideoPlayback()).tick(stream);
        }
        for(CinemarrVideoClientState.StreamKey key:new ArrayList<>(pipelines.keySet()))if(!current.contains(key)){pipelines.remove(key).close();}
    }
    CinemarrVideoPlayback pipeline(CinemarrVideoClientState.StreamKey key){return pipelines.get(key);}
    Map<CinemarrVideoClientState.StreamKey,CinemarrVideoPlayback> pipelines(){return java.util.Collections.unmodifiableMap(pipelines);}
    public void reset(){for(CinemarrVideoPlayback value:pipelines.values())value.close();pipelines.clear();}
    @Override public void close(){reset();}
}
