package stonytark.cinemarr.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One positional OpenAL/Paulscode source per independently playing visible session. */
final class LegacyVideoAudioManager {
    private final Map<LegacyVideoClientState.StreamKey, LegacyVideoAudio> players = new LinkedHashMap<LegacyVideoClientState.StreamKey, LegacyVideoAudio>();
    private final Set<LegacyVideoClientState.StreamKey> acceptanceReady = new LinkedHashSet<LegacyVideoClientState.StreamKey>();
    void tick(LegacyVideoPlaybackManager playback, LegacyVideoClientState state) {
        Set<LegacyVideoClientState.StreamKey> current = new LinkedHashSet<LegacyVideoClientState.StreamKey>();
        for (LegacyVideoClientState.StreamState stream : state.streamStates()) {
            LegacyVideoPlayback pipeline = playback.pipeline(stream.key()); List<stonytark.cinemarr.core.protocol.VideoPackets.SessionState> televisions = state.televisionsForStream(stream.key());
            if (pipeline == null || televisions.isEmpty()) continue; current.add(stream.key());
            LegacyVideoAudio audio = players.get(stream.key()); if (audio == null) { audio = new LegacyVideoAudio(); players.put(stream.key(), audio); }
            audio.tick(pipeline, stream.session(), televisions); pipeline.sendHealth(stream, audio.underruns());
            if (stonytark.cinemarr.core.protocol.ProtocolLimits.videoProbeEnabled() && audio.ready() && acceptanceReady.add(stream.key())) {
                stonytark.cinemarr.Cinemarr.LOGGER.info("Acceptance video audio: session={} generation={} ready=true underruns={}",
                        stream.key().sessionId, stream.key().generation, audio.underruns());
            }
        }
        for (LegacyVideoClientState.StreamKey key : new ArrayList<LegacyVideoClientState.StreamKey>(players.keySet())) if (!current.contains(key)) { players.remove(key).reset(); acceptanceReady.remove(key); }
    }
    boolean anyReady() { for (LegacyVideoAudio value : players.values()) if (value.ready()) return true; return false; }
    void audioEngineReloaded() { for (LegacyVideoAudio audio : players.values()) audio.audioEngineReloaded(); }
    void reset() { for (LegacyVideoAudio audio : players.values()) audio.reset(); players.clear(); acceptanceReady.clear(); }
}
