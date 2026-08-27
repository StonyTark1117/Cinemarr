package stonytark.cinemarr.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** One positional OpenAL stream per independently playing visible session. */
public final class CinemarrVideoAudioManager {
    private final Map<CinemarrVideoClientState.StreamKey,CinemarrVideoAudio> players=new LinkedHashMap<>();
    public void tick(CinemarrVideoPlaybackManager playback,CinemarrVideoClientState state){
        java.util.Set<CinemarrVideoClientState.StreamKey> current=new java.util.LinkedHashSet<>();
        for(CinemarrVideoClientState.StreamState stream:state.streamStates()){
            CinemarrVideoPlayback pipeline=playback.pipeline(stream.key());if(pipeline==null)continue;
            java.util.List<stonytark.cinemarr.core.protocol.VideoPackets.SessionState> televisions=state.televisionsForStream(stream.key());if(televisions.isEmpty())continue;
            net.minecraft.world.phys.Vec3 listener=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().position();
            stonytark.cinemarr.core.protocol.VideoPackets.SessionState nearest=televisions.stream().min(java.util.Comparator.comparingDouble(tv->CinemarrVideoAudio.nearestScreenPoint(tv,listener).distanceToSqr(listener))).orElse(televisions.get(0));
            current.add(stream.key());CinemarrVideoAudio audio=players.computeIfAbsent(stream.key(),ignored->new CinemarrVideoAudio());audio.tick(pipeline,nearest);pipeline.sendHealth(stream,audio.underruns());
        }
        for(CinemarrVideoClientState.StreamKey key:new ArrayList<>(players.keySet()))if(!current.contains(key)){players.remove(key).reset();}
    }
    public void audioEngineReloaded(){for(CinemarrVideoAudio value:players.values())value.audioEngineReloaded();}
    public void reset(){for(CinemarrVideoAudio value:players.values())value.reset();players.clear();}
}
