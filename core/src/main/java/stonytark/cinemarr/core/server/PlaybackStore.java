package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.model.QueueTrack;
import stonytark.cinemarr.core.model.StationModels;
import stonytark.cinemarr.core.protocol.StatePackets;

import java.util.List;

/** Canonical schema-4 state contract implemented by each Minecraft saved-data adapter. */
public interface PlaybackStore {
    List<QueueTrack> queue();
    List<QueueTrack> history();
    QueueTrack current();
    StatePackets.PlaybackOrigin currentOrigin();
    String currentSourceName();
    StationModels.StationDefinition station();
    boolean autoplayEnabled();
    long checkpointMs();
    boolean paused();
    void current(QueueTrack track, StatePackets.PlaybackOrigin origin, String sourceName);
    void station(StationModels.StationDefinition value);
    void autoplayEnabled(boolean enabled);
    void remember(QueueTrack track);
    void update(long checkpointMs, boolean paused);
    void clearAll();
    /** Marks platform persistence dirty without exposing a remapped Minecraft method name. */
    void markChanged();
}
