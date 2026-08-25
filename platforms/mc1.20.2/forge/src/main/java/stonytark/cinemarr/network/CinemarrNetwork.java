package stonytark.cinemarr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.server.CinemarrServer;

import java.util.function.BiConsumer;
import java.util.function.Function;

public final class CinemarrNetwork {
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);
    private static SimpleChannel channel;

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }

    public static void register() {
        channel = ChannelBuilder
                .named(new ResourceLocation(Cinemarr.MODID, "main"))
                .networkProtocolVersion(PROTOCOL)
                .simpleChannel();
        int id = 0;
        client(id++, CinemarrPayloads.OpenScreen.class, (value, buffer) -> {}, buffer -> new CinemarrPayloads.OpenScreen());
        client(id++, CinemarrPayloads.ServerHello.class, CinemarrPayloads.ServerHello::write, CinemarrPayloads.ServerHello::read);
        client(id++, CinemarrPayloads.TimeSyncResponse.class, CinemarrPayloads.TimeSyncResponse::write, CinemarrPayloads.TimeSyncResponse::read);
        client(id++, CinemarrPayloads.BrowseResults.class, CinemarrPayloads.BrowseResults::write, CinemarrPayloads.BrowseResults::read);
        client(id++, CinemarrPayloads.AudioManifest.class, CinemarrPayloads.AudioManifest::write, CinemarrPayloads.AudioManifest::read);
        client(id++, CinemarrPayloads.AudioChunk.class, CinemarrPayloads.AudioChunk::write, CinemarrPayloads.AudioChunk::read);
        client(id++, CinemarrPayloads.PlaybackState.class, CinemarrPayloads.PlaybackState::write, CinemarrPayloads.PlaybackState::read);
        client(id++, CinemarrPayloads.StationState.class, CinemarrPayloads.StationState::write, CinemarrPayloads.StationState::read);
        client(id++, CinemarrPayloads.AdventurePreview.class, CinemarrPayloads.AdventurePreview::write, CinemarrPayloads.AdventurePreview::read);
        client(id++, CinemarrPayloads.ErrorMessage.class, CinemarrPayloads.ErrorMessage::write, CinemarrPayloads.ErrorMessage::read);

        server(id++, CinemarrPayloads.ClientHello.class, CinemarrPayloads.ClientHello::write, CinemarrPayloads.ClientHello::read,
                (player, payload) -> {
                    if (!protocolMatches(payload.protocolVersion())) {
                        String reason = "Cinemarr protocol mismatch: server requires version " + PROTOCOL;
                        Cinemarr.LOGGER.warn("Disconnecting {}: {}", player.getGameProfile().getName(), reason);
                        // Forge 48 can close the connection before disconnect() flushes its
                        // terminal packet, reducing the client-facing reason to "Disconnected".
                        // Send the terminal packet explicitly; the client closes on receipt.
                        player.connection.send(new ClientboundDisconnectPacket(Component.literal(reason)));
                    } else CinemarrServer.instance().hello(player);
                });
        server(id++, CinemarrPayloads.TimeSyncRequest.class, CinemarrPayloads.TimeSyncRequest::write, CinemarrPayloads.TimeSyncRequest::read,
                (player, payload) -> sendToPlayer(player, new CinemarrPayloads.TimeSyncResponse(
                        payload.nonce(), payload.clientSentEpochMs(), System.currentTimeMillis())));
        server(id++, CinemarrPayloads.BrowseRequest.class, CinemarrPayloads.BrowseRequest::write, CinemarrPayloads.BrowseRequest::read,
                (player, payload) -> CinemarrServer.instance().browse(player, payload));
        server(id++, CinemarrPayloads.QueueRequest.class, CinemarrPayloads.QueueRequest::write, CinemarrPayloads.QueueRequest::read,
                (player, payload) -> CinemarrServer.instance().queue(player, payload));
        server(id++, CinemarrPayloads.ControlRequest.class, CinemarrPayloads.ControlRequest::write, CinemarrPayloads.ControlRequest::read,
                (player, payload) -> CinemarrServer.instance().control(player, payload));
        server(id++, CinemarrPayloads.StationRequest.class, CinemarrPayloads.StationRequest::write, CinemarrPayloads.StationRequest::read,
                (player, payload) -> CinemarrServer.instance().station(player, payload));
        server(id++, CinemarrPayloads.ChunkRequest.class, CinemarrPayloads.ChunkRequest::write, CinemarrPayloads.ChunkRequest::read,
                (player, payload) -> CinemarrServer.instance().chunks(player, payload));
        server(id++, CinemarrPayloads.ChunkAcknowledgement.class, CinemarrPayloads.ChunkAcknowledgement::write, CinemarrPayloads.ChunkAcknowledgement::read,
                (player, payload) -> CinemarrServer.instance().acknowledge(player, payload));
        server(id++, CinemarrPayloads.AudioHealth.class, CinemarrPayloads.AudioHealth::write, CinemarrPayloads.AudioHealth::read,
                (player, payload) -> CinemarrServer.instance().health(player, payload));
        server(id, CinemarrPayloads.ManifestRequest.class, CinemarrPayloads.ManifestRequest::write, CinemarrPayloads.ManifestRequest::read,
                (player, payload) -> CinemarrServer.instance().sync(player));
    }

    public static void sendToServer(CinemarrMessage payload) {
        required().send(payload, PacketDistributor.SERVER.noArg());
    }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) {
        required().send(payload, PacketDistributor.PLAYER.with(player));
    }
    public static void sendToAllPlayers(CinemarrMessage payload) {
        required().send(payload, PacketDistributor.ALL.noArg());
    }

    private static <T extends CinemarrMessage> void client(int id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        required().messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encoder).decoder(decoder)
                .consumerMainThread((payload, context) -> ClientPayloadBridge.accept(payload))
                .add();
    }

    private static <T extends CinemarrMessage> void server(int id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
            BiConsumer<ServerPlayer, T> action) {
        required().messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encoder).decoder(decoder)
                .consumerMainThread((payload, context) -> withSender(context, player -> action.accept(player, payload)))
                .add();
    }

    private static void withSender(CustomPayloadEvent.Context context, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer sender = context.getSender();
        if (sender != null) action.accept(sender);
    }

    private static SimpleChannel required() {
        if (channel == null) throw new IllegalStateException("Cinemarr networking is not initialized");
        return channel;
    }

    private CinemarrNetwork() {}
}
