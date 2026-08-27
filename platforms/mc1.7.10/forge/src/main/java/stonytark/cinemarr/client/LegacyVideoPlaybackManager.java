package stonytark.cinemarr.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Owns one decoder/texture pipeline per distinct visible session generation. */
final class LegacyVideoPlaybackManager implements AutoCloseable {
    private final Map<LegacyVideoClientState.StreamKey, LegacyVideoPlayback> pipelines = new LinkedHashMap<LegacyVideoClientState.StreamKey, LegacyVideoPlayback>();
    void tick(LegacyVideoClientState state) {
        Set<LegacyVideoClientState.StreamKey> current = new LinkedHashSet<LegacyVideoClientState.StreamKey>();
        for (LegacyVideoClientState.StreamState stream : state.streamStates()) {
            current.add(stream.key()); LegacyVideoPlayback pipeline = pipelines.get(stream.key());
            if (pipeline == null) { pipeline = new LegacyVideoPlayback(); pipelines.put(stream.key(), pipeline); } pipeline.tick(stream);
        }
        for (LegacyVideoClientState.StreamKey key : new ArrayList<LegacyVideoClientState.StreamKey>(pipelines.keySet())) if (!current.contains(key)) pipelines.remove(key).close();
    }
    LegacyVideoPlayback pipeline(LegacyVideoClientState.StreamKey key) { return pipelines.get(key); }
    Map<LegacyVideoClientState.StreamKey, LegacyVideoPlayback> pipelines() { return java.util.Collections.unmodifiableMap(pipelines); }
    void reset() { for (LegacyVideoPlayback value : pipelines.values()) value.close(); pipelines.clear(); }
    @Override public void close() { reset(); }
}
