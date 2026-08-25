package stonytark.cinemarr.server;

import stonytark.cinemarr.core.model.QueueTrack;


import stonytark.cinemarr.network.CinemarrPayloads;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/** The bounded, read-only Plex operations used by station generation. */
interface StationCatalog {
    List<QueueTrack> nativeRadioTracks(CinemarrPayloads.StationSeed seed, int limit) throws IOException, InterruptedException;
    boolean hasSonicAnalysis(String key) throws IOException, InterruptedException;
    List<PlexClient.SonicResult> nearest(CinemarrPayloads.ItemKind kind, String key, int limit, double maxDistance) throws IOException, InterruptedException;
    List<QueueTrack> nearestTracks(String key, int limit, double maxDistance) throws IOException, InterruptedException;
    List<QueueTrack> sonicPath(String startKey, String endKey, int limit) throws IOException, InterruptedException;
    List<QueueTrack> randomTracks(int limit, Set<String> excluded) throws IOException, InterruptedException;
    List<QueueTrack> metadataFallback(List<CinemarrPayloads.StationSeed> seeds, int limit, Set<String> excluded) throws IOException, InterruptedException;
    List<QueueTrack> expand(CinemarrPayloads.ItemKind kind, String key, int limit) throws IOException, InterruptedException;
}
