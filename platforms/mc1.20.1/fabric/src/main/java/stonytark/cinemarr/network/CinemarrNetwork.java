package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.server.CinemarrServer;

import java.util.function.Consumer;

public final class CinemarrNetwork {
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);
    private static volatile Consumer<CinemarrMessage> clientSender;
    private static volatile MinecraftServer server;

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }
    public static void installClientSender(Consumer<CinemarrMessage> sender) { clientSender = sender; }
    public static void activeServer(MinecraftServer value) { server = value; }

    public static void sendToServer(CinemarrMessage payload) {
        Consumer<CinemarrMessage> sender = clientSender;
        if (sender == null) throw new IllegalStateException("Cinemarr client networking is not initialized");
        sender.accept(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        CinemarrPayloads.write(payload, buffer);
        ServerPlayNetworking.send(player, CinemarrPayloads.idOf(payload), buffer);
    }

    public static void sendToAllPlayers(CinemarrMessage payload) {
        MinecraftServer current = server;
        if (current != null) for (ServerPlayer player : current.getPlayerList().getPlayers()) sendToPlayer(player, payload);
    }

    public static void register() {
        receive(CinemarrPayloads.ClientHello.ID, CinemarrPayloads.ClientHello::read, (player, payload) -> {
            if (!protocolMatches(payload.protocolVersion())) {
                String reason = "Cinemarr protocol mismatch: server requires version " + PROTOCOL;
                Cinemarr.LOGGER.warn("Disconnecting {}: {}", player.getGameProfile().getName(), reason);
                player.connection.disconnect(Component.literal(reason));
            } else CinemarrServer.instance().hello(player);
        });
        receive(CinemarrPayloads.TimeSyncRequest.ID, CinemarrPayloads.TimeSyncRequest::read, (player, payload) -> {
            if (CinemarrServer.instance().accepted(player)) sendToPlayer(player,
                    new CinemarrPayloads.TimeSyncResponse(payload.nonce(), payload.clientSentEpochMs(), System.currentTimeMillis()));
        });
        receive(CinemarrPayloads.BrowseRequest.ID, CinemarrPayloads.BrowseRequest::read,
                (player, payload) -> CinemarrServer.instance().browse(player, payload));
        receive(CinemarrPayloads.QueueRequest.ID, CinemarrPayloads.QueueRequest::read,
                (player, payload) -> CinemarrServer.instance().queue(player, payload));
        receive(CinemarrPayloads.ControlRequest.ID, CinemarrPayloads.ControlRequest::read,
                (player, payload) -> CinemarrServer.instance().control(player, payload));
        receive(CinemarrPayloads.StationRequest.ID, CinemarrPayloads.StationRequest::read,
                (player, payload) -> CinemarrServer.instance().station(player, payload));
        receive(CinemarrPayloads.ChunkRequest.ID, CinemarrPayloads.ChunkRequest::read,
                (player, payload) -> CinemarrServer.instance().chunks(player, payload));
        receive(CinemarrPayloads.ChunkAcknowledgement.ID, CinemarrPayloads.ChunkAcknowledgement::read,
                (player, payload) -> CinemarrServer.instance().acknowledge(player, payload));
        receive(CinemarrPayloads.AudioHealth.ID, CinemarrPayloads.AudioHealth::read,
                (player, payload) -> CinemarrServer.instance().health(player, payload));
        receive(CinemarrPayloads.ManifestRequest.ID, CinemarrPayloads.ManifestRequest::read,
                (player, payload) -> CinemarrServer.instance().sync(player));
    }

    public static FriendlyByteBuf encode(CinemarrMessage payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        CinemarrPayloads.write(payload, buffer);
        return buffer;
    }

    private static <T extends CinemarrMessage> void receive(ResourceLocation id, Decoder<T> decoder, ServerHandler<T> action) {
        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, responseSender) -> {
            final T payload;
            try {
                payload = decoder.read(buffer);
                if (buffer.readableBytes() != 0) throw new IllegalArgumentException("Trailing bytes in Cinemarr packet");
            } catch (RuntimeException malformed) {
                server.execute(() -> player.connection.disconnect(Component.literal("Malformed Cinemarr packet")));
                return;
            }
            server.execute(() -> action.handle(player, payload));
        });
    }

    @FunctionalInterface public interface Decoder<T> { T read(FriendlyByteBuf buffer); }
    @FunctionalInterface private interface ServerHandler<T> { void handle(ServerPlayer player, T payload); }
    private CinemarrNetwork() {}
}
