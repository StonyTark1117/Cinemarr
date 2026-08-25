package stonytark.cinemarr.server;

import stonytark.cinemarr.core.model.QueueTrack;
import stonytark.cinemarr.core.model.StationModels;
import stonytark.cinemarr.core.server.PlexException;
import stonytark.cinemarr.network.CinemarrPayloads;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** NeoForge payload adapter for the shared Java 8 station engine. */
public final class StationGenerator {
    static final int LOOKAHEAD_TARGET = stonytark.cinemarr.core.server.StationGenerator.LOOKAHEAD_TARGET;
    static final int TRACK_HISTORY_LIMIT = stonytark.cinemarr.core.server.StationGenerator.TRACK_HISTORY_LIMIT;
    static final int ARTIST_HISTORY_LIMIT = stonytark.cinemarr.core.server.StationGenerator.ARTIST_HISTORY_LIMIT;

    private final stonytark.cinemarr.core.server.StationGenerator delegate;

    public StationGenerator(StationCatalog plex) {
        delegate = new stonytark.cinemarr.core.server.StationGenerator(new CatalogAdapter(plex));
    }

    public GeneratedBatch generate(StationDefinition definition, List<QueueTrack> history,
                                   CinemarrPayloads.SonicCapability capability, boolean allowMetadataFallback)
            throws IOException, InterruptedException {
        stonytark.cinemarr.core.server.StationGenerator.GeneratedBatch result = delegate.generate(
                toCore(definition), history, enumValue(StationModels.SonicCapability.class, capability), allowMetadataFallback);
        return new GeneratedBatch(result.tracks(), result.adventurePath(), result.message());
    }

    public static void validate(StationDefinition definition) throws PlexException {
        stonytark.cinemarr.core.server.StationGenerator.validate(toCore(definition));
    }

    private static StationModels.StationDefinition toCore(StationDefinition definition) {
        if (definition == null) return null;
        return new StationModels.StationDefinition(enumValue(StationModels.StationType.class, definition.type()),
                definition.name(), toCoreSeeds(definition.seeds()), definition.generation());
    }

    private static List<StationModels.StationSeed> toCoreSeeds(List<CinemarrPayloads.StationSeed> values) {
        List<StationModels.StationSeed> seeds = new ArrayList<>();
        for (CinemarrPayloads.StationSeed seed : values) {
            seeds.add(new StationModels.StationSeed(enumValue(StationModels.ItemKind.class, seed.kind()),
                    seed.key(), seed.title(), seed.subtitle()));
        }
        return List.copyOf(seeds);
    }

    private static CinemarrPayloads.StationSeed toPayload(StationModels.StationSeed seed) {
        return new CinemarrPayloads.StationSeed(enumValue(CinemarrPayloads.ItemKind.class, seed.kind()),
                seed.key(), seed.title(), seed.subtitle());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Enum<?> value) {
        return Enum.valueOf(type, value.name());
    }

    public record GeneratedBatch(List<QueueTrack> tracks, boolean adventurePath, String message) {
        public GeneratedBatch { tracks = List.copyOf(tracks); }
    }

    private static final class CatalogAdapter implements stonytark.cinemarr.core.server.StationCatalog {
        private final StationCatalog delegate;

        private CatalogAdapter(StationCatalog delegate) {
            if (delegate == null) throw new IllegalArgumentException("plex");
            this.delegate = delegate;
        }

        @Override public List<QueueTrack> nativeRadioTracks(StationModels.StationSeed seed, int limit)
                throws IOException, InterruptedException {
            return delegate.nativeRadioTracks(toPayload(seed), limit);
        }

        @Override public boolean hasSonicAnalysis(String key) throws IOException, InterruptedException {
            return delegate.hasSonicAnalysis(key);
        }

        @Override public List<StationModels.SonicResult> nearest(StationModels.ItemKind kind, String key,
                                                                 int limit, double maxDistance)
                throws IOException, InterruptedException {
            List<StationModels.SonicResult> results = new ArrayList<>();
            for (PlexClient.SonicResult result : delegate.nearest(
                    enumValue(CinemarrPayloads.ItemKind.class, kind), key, limit, maxDistance)) {
                CinemarrPayloads.MediaItem item = result.item();
                results.add(new StationModels.SonicResult(new StationModels.MediaItem(
                        enumValue(StationModels.ItemKind.class, item.kind()), item.key(), item.title(),
                        item.subtitle(), item.durationMs()), result.distance()));
            }
            return List.copyOf(results);
        }

        @Override public List<QueueTrack> nearestTracks(String key, int limit, double maxDistance)
                throws IOException, InterruptedException {
            return delegate.nearestTracks(key, limit, maxDistance);
        }

        @Override public List<QueueTrack> sonicPath(String startKey, String endKey, int limit)
                throws IOException, InterruptedException {
            return delegate.sonicPath(startKey, endKey, limit);
        }

        @Override public List<QueueTrack> randomTracks(int limit, Set<String> excluded)
                throws IOException, InterruptedException {
            return delegate.randomTracks(limit, excluded);
        }

        @Override public List<QueueTrack> metadataFallback(List<StationModels.StationSeed> seeds, int limit,
                                                           Set<String> excluded)
                throws IOException, InterruptedException {
            List<CinemarrPayloads.StationSeed> payloadSeeds = new ArrayList<>();
            for (StationModels.StationSeed seed : seeds) payloadSeeds.add(toPayload(seed));
            return delegate.metadataFallback(List.copyOf(payloadSeeds), limit, excluded);
        }

        @Override public List<QueueTrack> expand(StationModels.ItemKind kind, String key, int limit)
                throws IOException, InterruptedException {
            return delegate.expand(enumValue(CinemarrPayloads.ItemKind.class, kind), key, limit);
        }
    }
}
