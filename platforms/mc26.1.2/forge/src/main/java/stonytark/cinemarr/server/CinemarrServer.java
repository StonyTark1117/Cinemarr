package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrPayloads;

public final class CinemarrServer {
    private static final CinemarrServer INSTANCE = new CinemarrServer();
    private GlobalPlayer player;

    public static CinemarrServer instance() { return INSTANCE; }
    public static void register() {
        ServerStartedEvent.BUS.addListener(INSTANCE::started);
        ServerStoppingEvent.BUS.addListener(INSTANCE::stopping);
        TickEvent.ServerTickEvent.Post.BUS.addListener(INSTANCE::tick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(INSTANCE::joined);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(INSTANCE::left);
    }

    public void started(ServerStartedEvent event) {
        try {
            java.nio.file.Path configDirectory = FMLPaths.CONFIGDIR.get();
            java.nio.file.Path canonical = event.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("serverconfig").resolve(CanonicalConfigFiles.SERVER_FILE_NAME);
            CanonicalConfigFiles.ServerConfig config = CanonicalConfigFiles.loadServerForLoader(
                    canonical, configDirectory, "forge");
            CinemarrSettings.installServer(config);
            if (config.importedFrom() != null) {
                Cinemarr.LOGGER.info("Imported legacy Cinemarr server settings from {}", config.importedFrom());
            }
            player = new GlobalPlayer(event.getServer());
        }
        catch (Exception error) { throw new IllegalStateException("Unable to initialize Cinemarr", error); }
    }
    public void stopping(ServerStoppingEvent event) { if (player != null) { player.close(); player = null; } }
    public void tick(TickEvent.ServerTickEvent.Post event) { if (player != null) player.tick(); }
    public void joined(PlayerEvent.PlayerLoggedInEvent event) {
        if (player != null && event.getEntity() instanceof ServerPlayer serverPlayer) player.playerJoined(serverPlayer);
    }
    public void left(PlayerEvent.PlayerLoggedOutEvent event) {
        if (player != null && event.getEntity() instanceof ServerPlayer serverPlayer) player.playerLeft(serverPlayer);
    }

    public void hello(ServerPlayer sender) { if (player != null) player.hello(sender); }
    public void browse(ServerPlayer sender, CinemarrPayloads.BrowseRequest request) { if (player != null) player.browse(sender, request); }
    public void queue(ServerPlayer sender, CinemarrPayloads.QueueRequest request) { if (player != null) player.queue(sender, request); }
    public void control(ServerPlayer sender, CinemarrPayloads.ControlRequest request) { if (player != null) player.control(sender, request); }
    public void station(ServerPlayer sender, CinemarrPayloads.StationRequest request) { if (player != null) player.station(sender, request); }
    public void chunks(ServerPlayer sender, CinemarrPayloads.ChunkRequest request) { if (player != null) player.chunks(sender, request); }
    public void acknowledge(ServerPlayer sender, CinemarrPayloads.ChunkAcknowledgement value) { if (player != null) player.acknowledge(sender, value); }
    public void health(ServerPlayer sender, CinemarrPayloads.AudioHealth value) { if (player != null) player.health(sender, value); }
    public void sync(ServerPlayer sender) { if (player != null) player.sync(sender); }
    public GlobalPlayer player() { return player; }
}
