package stonytark.cinemarr.server;

import stonytark.cinemarr.core.model.QueueTrack;
import stonytark.cinemarr.core.model.StationModels;
import stonytark.cinemarr.core.protocol.ControlPackets;
import stonytark.cinemarr.core.server.PlexService;
import stonytark.cinemarr.network.CinemarrPayloads;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Minecraft payload facade over the shared Java 8 Plex implementation. */
public final class PlexClient implements StationCatalog {
    static final int MAX_EXPANDED_TRACKS = PlexService.MAX_EXPANDED_TRACKS;
    static final int MAX_JSON_BYTES = PlexService.MAX_JSON_BYTES;
    static final long MAX_TRANSCODE_BYTES = PlexService.MAX_TRANSCODE_BYTES;
    static final long MAX_TRACK_DURATION_MS = PlexService.MAX_TRACK_DURATION_MS;

    public record Page(List<CinemarrPayloads.MediaItem> items, boolean hasMore) {
        public Page { items = List.copyOf(items); }
    }

    public record SonicStatus(CinemarrPayloads.SonicCapability capability, String message) {}
    public record SonicResult(CinemarrPayloads.MediaItem item, double distance) {}

    private final PlexService delegate;

    public PlexClient() {
        delegate = new PlexService();
    }

    PlexClient(String url, String token, String library) {
        delegate = new PlexService(url, token, library);
    }

    PlexClient(String url, String token, String library, Duration requestTimeout) {
        delegate = new PlexService(url, token, library, requestTimeout);
    }

    PlexClient(String url, String token, String library, Duration requestTimeout,
               int maxJsonBytes, long maxTranscodeBytes) {
        delegate = new PlexService(url, token, library, requestTimeout, maxJsonBytes, maxTranscodeBytes);
    }

    public void validate() throws IOException, InterruptedException {
        delegate.validate();
    }

    public SonicStatus sonicStatus() throws IOException, InterruptedException {
        PlexService.SonicStatus status = delegate.sonicStatus();
        return new SonicStatus(enumValue(CinemarrPayloads.SonicCapability.class, status.capability()), status.message());
    }

    public List<QueueTrack> nativeRadioTracks(CinemarrPayloads.StationSeed seed, int limit)
            throws IOException, InterruptedException {
        return delegate.nativeRadioTracks(toCore(seed), limit);
    }

    public boolean hasSonicAnalysis(String key) throws IOException, InterruptedException {
        return delegate.hasSonicAnalysis(key);
    }

    public List<QueueTrack> analyzedTracks(int limit) throws IOException, InterruptedException {
        return delegate.analyzedTracks(limit);
    }

    public List<SonicResult> nearest(CinemarrPayloads.ItemKind kind, String key, int limit, double maxDistance)
            throws IOException, InterruptedException {
        List<SonicResult> results = new ArrayList<>();
        for (StationModels.SonicResult result : delegate.nearest(
                enumValue(StationModels.ItemKind.class, kind), key, limit, maxDistance)) {
            results.add(new SonicResult(toPayload(result.item()), result.distance()));
        }
        return List.copyOf(results);
    }

    public List<QueueTrack> nearestTracks(String key, int limit, double maxDistance)
            throws IOException, InterruptedException {
        return delegate.nearestTracks(key, limit, maxDistance);
    }

    public List<QueueTrack> sonicPath(String startKey, String endKey, int limit)
            throws IOException, InterruptedException {
        return delegate.sonicPath(startKey, endKey, limit);
    }

    public List<QueueTrack> randomTracks(int limit, Set<String> excluded)
            throws IOException, InterruptedException {
        return delegate.randomTracks(limit, excluded);
    }

    public List<QueueTrack> metadataFallback(List<CinemarrPayloads.StationSeed> seeds, int limit,
                                             Set<String> excluded)
            throws IOException, InterruptedException {
        List<StationModels.StationSeed> converted = new ArrayList<>();
        for (CinemarrPayloads.StationSeed seed : seeds) converted.add(toCore(seed));
        return delegate.metadataFallback(converted, limit, excluded);
    }

    public Page browse(CinemarrPayloads.BrowseKind kind, String query, int page, int pageSize)
            throws IOException, InterruptedException {
        PlexService.Page result = delegate.browse(enumValue(ControlPackets.BrowseKind.class, kind), query, page, pageSize);
        List<CinemarrPayloads.MediaItem> items = new ArrayList<>();
        for (StationModels.MediaItem item : result.items()) items.add(toPayload(item));
        return new Page(items, result.hasMore());
    }

    public List<QueueTrack> expand(CinemarrPayloads.ItemKind kind, String key)
            throws IOException, InterruptedException {
        return delegate.expand(enumValue(StationModels.ItemKind.class, kind), key);
    }

    public List<QueueTrack> expand(CinemarrPayloads.ItemKind kind, String key, int limit)
            throws IOException, InterruptedException {
        return delegate.expand(enumValue(StationModels.ItemKind.class, kind), key, limit);
    }

    public void transcode(QueueTrack track, Path output, int bitrate) throws IOException, InterruptedException {
        delegate.transcode(track, output, bitrate);
    }

    private static StationModels.StationSeed toCore(CinemarrPayloads.StationSeed seed) {
        return new StationModels.StationSeed(enumValue(StationModels.ItemKind.class, seed.kind()),
                seed.key(), seed.title(), seed.subtitle());
    }

    private static CinemarrPayloads.MediaItem toPayload(StationModels.MediaItem item) {
        return new CinemarrPayloads.MediaItem(enumValue(CinemarrPayloads.ItemKind.class, item.kind()),
                item.key(), item.title(), item.subtitle(), item.durationMs());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Enum<?> value) {
        return Enum.valueOf(type, value.name());
    }
}
