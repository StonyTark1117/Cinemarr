package stonytark.cinemarr.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.network.HelloGate;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.network.CinemarrPayloads;

import java.nio.file.Path;
import java.util.UUID;

public final class CinemarrServer {
    private static final CinemarrServer INSTANCE = new CinemarrServer();
    private static final long HELLO_TIMEOUT_TICKS = 100;
    private GlobalPlayer player;
    private final HelloGate<UUID> helloGate = new HelloGate<>(HELLO_TIMEOUT_TICKS);
    private long ticks;

    public static CinemarrServer instance() { return INSTANCE; }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CinemarrNetwork.activeServer(server);
            INSTANCE.ticks = 0;
            INSTANCE.helloGate.clear();
            try {
                Path configDirectory = FabricLoader.getInstance().getConfigDir();
                Path canonical = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig")
                        .resolve(CanonicalConfigFiles.SERVER_FILE_NAME);
                CanonicalConfigFiles.ServerConfig config = CanonicalConfigFiles.loadServerForLoader(
                        canonical, configDirectory, "fabric");
                CinemarrSettings.installServer(config);
                if (config.importedFrom() != null) {
                    Cinemarr.LOGGER.info("Imported legacy Cinemarr server settings from {}", config.importedFrom());
                }
                INSTANCE.player = new GlobalPlayer(server);
            }
            catch (Exception error) { throw new IllegalStateException("Unable to initialize Cinemarr", error); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (INSTANCE.player != null) { INSTANCE.player.close(); INSTANCE.player = null; }
            INSTANCE.helloGate.clear();
            CinemarrNetwork.activeServer(null);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            INSTANCE.ticks++;
            for (UUID id : INSTANCE.helloGate.expire(INSTANCE.ticks)) {
                ServerPlayer timedOut = server.getPlayerList().getPlayer(id);
                if (timedOut != null) timedOut.connection.disconnect(Component.literal(
                        "Cinemarr client handshake timed out; install a compatible Cinemarr protocol " + CinemarrNetwork.PROTOCOL + " client"));
            }
            if (INSTANCE.player != null) INSTANCE.player.tick();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(handler.player, CinemarrPayloads.ServerHello.ID)) {
                handler.disconnect(Component.literal("Cinemarr is required on the client (protocol " + CinemarrNetwork.PROTOCOL + ")"));
                return;
            }
            INSTANCE.helloGate.require(handler.player.getUUID(), INSTANCE.ticks);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            INSTANCE.helloGate.remove(handler.player.getUUID());
            if (INSTANCE.player != null) INSTANCE.player.playerLeft(handler.player);
        });
    }

    public void hello(ServerPlayer sender) {
        if (helloGate.accept(sender.getUUID()) && player != null) player.hello(sender);
    }
    public boolean accepted(ServerPlayer sender) { return helloGate.accepted(sender.getUUID()); }
    public void browse(ServerPlayer sender, CinemarrPayloads.BrowseRequest request) { if (accepted(sender) && player != null) player.browse(sender, request); }
    public void queue(ServerPlayer sender, CinemarrPayloads.QueueRequest request) { if (accepted(sender) && player != null) player.queue(sender, request); }
    public void control(ServerPlayer sender, CinemarrPayloads.ControlRequest request) { if (accepted(sender) && player != null) player.control(sender, request); }
    public void station(ServerPlayer sender, CinemarrPayloads.StationRequest request) { if (accepted(sender) && player != null) player.station(sender, request); }
    public void chunks(ServerPlayer sender, CinemarrPayloads.ChunkRequest request) { if (accepted(sender) && player != null) player.chunks(sender, request); }
    public void acknowledge(ServerPlayer sender, CinemarrPayloads.ChunkAcknowledgement value) { if (accepted(sender) && player != null) player.acknowledge(sender, value); }
    public void health(ServerPlayer sender, CinemarrPayloads.AudioHealth value) { if (accepted(sender) && player != null) player.health(sender, value); }
    public void sync(ServerPlayer sender) { if (accepted(sender) && player != null) player.sync(sender); }
    public GlobalPlayer player() { return player; }
}
