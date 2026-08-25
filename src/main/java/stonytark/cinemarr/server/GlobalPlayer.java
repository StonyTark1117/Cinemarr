package stonytark.cinemarr.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.model.QueueTrack;
import stonytark.cinemarr.core.model.StationModels;
import stonytark.cinemarr.core.platform.CoreLogger;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.ControlPackets;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.StatePackets;
import stonytark.cinemarr.core.protocol.TransportPackets;
import stonytark.cinemarr.core.server.CoordinatorRuntime;
import stonytark.cinemarr.core.server.GlobalPlaybackCoordinator;
import stonytark.cinemarr.core.server.PlaybackStore;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mojang-mapped server adapter over the shared Java 8 playback coordinator. */
public final class GlobalPlayer implements AutoCloseable {
    private final GlobalPlaybackCoordinator<ServerPlayer> delegate;

    public GlobalPlayer(final MinecraftServer server) throws IOException {
        final CinemarrSavedData saved = CinemarrSavedData.get(server);
        delegate = new GlobalPlaybackCoordinator<>(new CoordinatorRuntime<>() {
            @Override public UUID playerId(ServerPlayer player) { return player.getUUID(); }
            @Override public boolean isOperator(ServerPlayer player, int permissionLevel) {
                return CinemarrPermissions.has(player, permissionLevel);
            }
            @Override public List<ServerPlayer> players() {
                return new ArrayList<>(server.getPlayerList().getPlayers());
            }
            @Override public int playerCount() { return server.getPlayerList().getPlayerCount(); }
            @Override public java.nio.file.Path cacheDirectory() {
                return Paths.get(server.getServerDirectory().toString()).resolve("cinemarr-cache");
            }
            @Override public void execute(Runnable action) { server.execute(action); }
            @Override public void send(ServerPlayer player, CinemarrMessage message) {
                CinemarrNetwork.sendToPlayer(player, toPayload(message));
            }
            @Override public void chat(ServerPlayer player, String message) {
                player.sendSystemMessage(Component.literal(message));
            }
            @Override public CoreLogger logger() {
                return new CoreLogger() {
                    @Override public void info(String message) { Cinemarr.LOGGER.info(message); }
                    @Override public void warn(String message, Throwable error) { Cinemarr.LOGGER.warn(message, error); }
                };
            }
        }, new ModernPlaybackStore(saved));
    }

    public void hello(ServerPlayer player) {
        CinemarrNetwork.sendToPlayer(player,
                new CinemarrPayloads.ServerHello(CinemarrNetwork.PROTOCOL, System.currentTimeMillis()));
        delegate.playerJoined(player);
    }

    public void validatePlex() { delegate.validatePlex(); }
    public void tick() { delegate.tick(); }
    public void browse(ServerPlayer player, CinemarrPayloads.BrowseRequest request) {
        delegate.browse(player, new ControlPackets.BrowseRequest(
                enumValue(ControlPackets.BrowseKind.class, request.kind()), request.query(), request.page()));
    }
    public void queue(ServerPlayer player, CinemarrPayloads.QueueRequest request) {
        delegate.queue(player, new ControlPackets.QueueRequest(
                enumValue(StationModels.ItemKind.class, request.kind()), request.key()));
    }
    public void control(ServerPlayer player, CinemarrPayloads.ControlRequest request) {
        delegate.control(player, new ControlPackets.ControlRequest(
                enumValue(ControlPackets.ControlAction.class, request.action()), request.index(), request.expectedKey()));
    }
    public void station(ServerPlayer player, CinemarrPayloads.StationRequest request) {
        List<StationModels.StationSeed> seeds = new ArrayList<>();
        for (CinemarrPayloads.StationSeed seed : request.seeds()) seeds.add(toCore(seed));
        delegate.station(player, new ControlPackets.StationRequest(
                enumValue(ControlPackets.StationAction.class, request.action()),
                enumValue(StationModels.StationType.class, request.stationType()),
                request.enabled(), request.expectedGeneration(), seeds));
    }
    public void chunks(ServerPlayer player, CinemarrPayloads.ChunkRequest request) {
        delegate.chunks(player, new TransportPackets.ChunkRequest(
                request.sessionId(), request.requestId(), request.startIndex(), request.count()));
    }
    public void acknowledge(ServerPlayer player, CinemarrPayloads.ChunkAcknowledgement value) {
        delegate.acknowledge(player, new TransportPackets.ChunkAcknowledgement(
                value.sessionId(), value.requestId(), value.receivedThroughIndex(), value.bufferedMs()));
    }
    public void health(ServerPlayer player, CinemarrPayloads.AudioHealth value) {
        delegate.health(player, new StatePackets.AudioHealth(value.sessionId(), value.state(),
                value.recoveryAttempts(), value.underruns(), value.receivedChunks(), value.bufferedMs()));
    }
    public void playerJoined(ServerPlayer player) { delegate.playerJoined(player); }
    public void sync(ServerPlayer player) { delegate.sync(player); }
    public void playerLeft(ServerPlayer player) { delegate.playerLeft(player); }
    public long cacheSize() { return delegate.cacheSize(); }
    public String status() { return delegate.status(); }
    public String stationStatus() { return delegate.stationStatus(); }
    public long stationGeneration() { return delegate.stationGeneration(); }
    public String diagnostics() { return delegate.diagnostics(); }
    @Override public void close() { delegate.close(); }

    private static CinemarrPayloads.StationSeed toPayload(StationModels.StationSeed value) {
        return new CinemarrPayloads.StationSeed(enumValue(CinemarrPayloads.ItemKind.class, value.kind()),
                value.key(), value.title(), value.subtitle());
    }

    private static StationModels.StationSeed toCore(CinemarrPayloads.StationSeed value) {
        return new StationModels.StationSeed(enumValue(StationModels.ItemKind.class, value.kind()),
                value.key(), value.title(), value.subtitle());
    }

    private static CinemarrPayloads.QueueEntry toPayload(StatePackets.QueueEntry value) {
        return new CinemarrPayloads.QueueEntry(value.key(), value.title(), value.artist(), value.durationMs(),
                enumValue(CinemarrPayloads.PlaybackOrigin.class, value.source()), value.editable());
    }

    private static CinemarrMessage toPayload(CinemarrMessage value) {
        if (value instanceof ControlPackets.BrowseResults result) {
            List<CinemarrPayloads.MediaItem> items = new ArrayList<>();
            for (StationModels.MediaItem item : result.items()) {
                items.add(new CinemarrPayloads.MediaItem(enumValue(CinemarrPayloads.ItemKind.class, item.kind()),
                        item.key(), item.title(), item.subtitle(), item.durationMs()));
            }
            return new CinemarrPayloads.BrowseResults(enumValue(CinemarrPayloads.BrowseKind.class, result.kind()),
                    result.query(), result.page(), result.hasMore(), items);
        }
        if (value instanceof StatePackets.AdventurePreview preview) {
            return new CinemarrPayloads.AdventurePreview(preview.generation(), preview.message(),
                    preview.path().stream().map(GlobalPlayer::toPayload).toList());
        }
        if (value instanceof TransportPackets.AudioChunk chunk) {
            return new CinemarrPayloads.AudioChunk(chunk.sessionId(), chunk.requestId(), chunk.index(),
                    chunk.startMs(), chunk.sha256(), chunk.data());
        }
        if (value instanceof TransportPackets.AudioManifest manifest) {
            return new CinemarrPayloads.AudioManifest(manifest.sessionId(), manifest.title(), manifest.artist(),
                    manifest.totalChunks(), manifest.firstChunk(), manifest.durationMs(), manifest.startedAtEpochMs(),
                    manifest.paused(), manifest.pausedPositionMs(), manifest.sha256());
        }
        if (value instanceof StatePackets.PlaybackState state) {
            return new CinemarrPayloads.PlaybackState(enumValue(CinemarrPayloads.PlaybackStatus.class, state.status()),
                    state.statusMessage(), state.title(), state.artist(), state.paused(), state.positionMs(),
                    state.durationMs(), state.serverEpochMs(), state.operator(),
                    enumValue(CinemarrPayloads.PlaybackOrigin.class, state.origin()), state.sourceName(),
                    state.queue().stream().map(GlobalPlayer::toPayload).toList());
        }
        if (value instanceof StatePackets.StationState state) {
            return new CinemarrPayloads.StationState(enumValue(CinemarrPayloads.StationType.class, state.stationType()),
                    state.active(), state.autoplayEnabled(), state.generation(),
                    enumValue(CinemarrPayloads.SonicCapability.class, state.capability()),
                    state.capabilityMessage(), state.name(),
                    state.seeds().stream().map(GlobalPlayer::toPayload).toList(),
                    state.preview().stream().map(GlobalPlayer::toPayload).toList());
        }
        if (value instanceof StatePackets.ErrorMessage error) {
            return new CinemarrPayloads.ErrorMessage(
                    enumValue(CinemarrPayloads.ErrorCode.class, error.code()), error.message());
        }
        throw new IllegalArgumentException("Unsupported Cinemarr clientbound message " + value.getClass().getName());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, Enum<?> value) {
        return Enum.valueOf(type, value.name());
    }

    private static final class ModernPlaybackStore implements PlaybackStore {
        private final CinemarrSavedData delegate;
        private ModernPlaybackStore(CinemarrSavedData delegate) { this.delegate = delegate; }

        @Override public List<QueueTrack> queue() { return delegate.queue(); }
        @Override public List<QueueTrack> history() { return delegate.history(); }
        @Override public QueueTrack current() { return delegate.current(); }
        @Override public StatePackets.PlaybackOrigin currentOrigin() {
            return enumValue(StatePackets.PlaybackOrigin.class, delegate.currentOrigin());
        }
        @Override public String currentSourceName() { return delegate.currentSourceName(); }
        @Override public StationModels.StationDefinition station() {
            StationDefinition value = delegate.station();
            List<StationModels.StationSeed> seeds = new ArrayList<>();
            for (CinemarrPayloads.StationSeed seed : value.seeds()) seeds.add(toCore(seed));
            return new StationModels.StationDefinition(enumValue(StationModels.StationType.class, value.type()),
                    value.name(), seeds, value.generation());
        }
        @Override public boolean autoplayEnabled() { return delegate.autoplayEnabled(); }
        @Override public long checkpointMs() { return delegate.checkpointMs(); }
        @Override public boolean paused() { return delegate.paused(); }
        @Override public void current(QueueTrack track, StatePackets.PlaybackOrigin origin, String sourceName) {
            delegate.current(track, enumValue(CinemarrPayloads.PlaybackOrigin.class, origin), sourceName);
        }
        @Override public void station(StationModels.StationDefinition value) {
            List<CinemarrPayloads.StationSeed> seeds = new ArrayList<>();
            for (StationModels.StationSeed seed : value.seeds()) seeds.add(toPayload(seed));
            delegate.station(new StationDefinition(enumValue(CinemarrPayloads.StationType.class, value.type()),
                    value.name(), seeds, value.generation()));
        }
        @Override public void autoplayEnabled(boolean enabled) { delegate.autoplayEnabled(enabled); }
        @Override public void remember(QueueTrack track) { delegate.remember(track); }
        @Override public void update(long checkpointMs, boolean paused) { delegate.update(checkpointMs, paused); }
        @Override public void clearAll() { delegate.clearAll(); }
        @Override public void markChanged() { delegate.setDirty(); }
    }
}
