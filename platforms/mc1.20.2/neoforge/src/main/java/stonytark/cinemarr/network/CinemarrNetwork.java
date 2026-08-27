package stonytark.cinemarr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.simple.SimpleChannel;
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
        channel = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(Cinemarr.MODID, "main"))
                .networkProtocolVersion(() -> VERSION)
                .clientAcceptedVersions(VERSION::equals)
                .serverAcceptedVersions(VERSION::equals)
                .simpleChannel();
        int id = 0;
        client(id++, CinemarrPayloads.ServerHello.class, CinemarrPayloads.ServerHello::write, CinemarrPayloads.ServerHello::read);
        client(id++, CinemarrPayloads.TimeSyncResponse.class, CinemarrPayloads.TimeSyncResponse::write, CinemarrPayloads.TimeSyncResponse::read);
        client(id++, VideoPayloads.OpenVideoScreen.class, VideoPayloads.OpenVideoScreen::write, VideoPayloads.OpenVideoScreen::read);
        client(id++, VideoPayloads.LibraryList.class, VideoPayloads.LibraryList::write, VideoPayloads.LibraryList::read);
        client(id++, VideoPayloads.BrowseResults.class, VideoPayloads.BrowseResults::write, VideoPayloads.BrowseResults::read);
        client(id++, VideoPayloads.SessionState.class, VideoPayloads.SessionState::write, VideoPayloads.SessionState::read);
        client(id++, VideoPayloads.TelevisionRemoved.class, VideoPayloads.TelevisionRemoved::write, VideoPayloads.TelevisionRemoved::read);
        client(id++, VideoPayloads.SessionQueue.class, VideoPayloads.SessionQueue::write, VideoPayloads.SessionQueue::read);
        client(id++, VideoPayloads.SegmentManifest.class, VideoPayloads.SegmentManifest::write, VideoPayloads.SegmentManifest::read);
        client(id++, VideoPayloads.SegmentChunk.class, VideoPayloads.SegmentChunk::write, VideoPayloads.SegmentChunk::read);

        server(id++, CinemarrPayloads.ClientHello.class, CinemarrPayloads.ClientHello::write, CinemarrPayloads.ClientHello::read,
                (player, payload) -> {
                    if (!protocolMatches(payload.protocolVersion())) {
                        String reason = "Cinemarr protocol mismatch: server requires version " + PROTOCOL;
                        Cinemarr.LOGGER.warn("Disconnecting {}: {}", player.getGameProfile().getName(), reason);
                        // NeoForge 20.2 can close the connection before an immediate
                        // disconnect() delivers its reason, collapsing it to "Disconnected".
                        // Send the terminal packet explicitly; the client closes on receipt.
                        player.connection.send(new ClientboundDisconnectPacket(Component.literal(reason)));
                    } else CinemarrServer.instance().hello(player);
                });
        server(id++, CinemarrPayloads.TimeSyncRequest.class, CinemarrPayloads.TimeSyncRequest::write, CinemarrPayloads.TimeSyncRequest::read,
                (player, payload) -> sendToPlayer(player, new CinemarrPayloads.TimeSyncResponse(
                        payload.nonce(), payload.clientSentEpochMs(), System.currentTimeMillis())));
        server(id++, VideoPayloads.LibraryListRequest.class, VideoPayloads.LibraryListRequest::write, VideoPayloads.LibraryListRequest::read,
                (player, payload) -> CinemarrServer.instance().videoLibraries(player));
        server(id++, VideoPayloads.BrowseRequest.class, VideoPayloads.BrowseRequest::write, VideoPayloads.BrowseRequest::read,
                (player, payload) -> CinemarrServer.instance().videoBrowse(player, payload.value()));
        server(id++, VideoPayloads.SessionCommand.class, VideoPayloads.SessionCommand::write, VideoPayloads.SessionCommand::read,
                (player, payload) -> CinemarrServer.instance().videoCommand(player, payload.value()));
        server(id++, VideoPayloads.SegmentRequest.class, VideoPayloads.SegmentRequest::write, VideoPayloads.SegmentRequest::read,
                (player, payload) -> CinemarrServer.instance().videoSegments(player, payload.value()));
        server(id++, VideoPayloads.SegmentManifestRequest.class, VideoPayloads.SegmentManifestRequest::write, VideoPayloads.SegmentManifestRequest::read,
                (player, payload) -> CinemarrServer.instance().videoManifest(player, payload.value()));
        server(id++, VideoPayloads.SegmentAcknowledgement.class, VideoPayloads.SegmentAcknowledgement::write, VideoPayloads.SegmentAcknowledgement::read,
                (player, payload) -> CinemarrServer.instance().videoAcknowledge(player, payload.value()));
        server(id++, VideoPayloads.ClientHealth.class, VideoPayloads.ClientHealth::write, VideoPayloads.ClientHealth::read,
                (player, payload) -> CinemarrServer.instance().videoHealth(player, payload.value()));
        client(id, CinemarrPayloads.ErrorMessage.class, CinemarrPayloads.ErrorMessage::write, CinemarrPayloads.ErrorMessage::read);
    }

    public static void sendToServer(CinemarrMessage payload) { required().sendToServer(payload); }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) {
        required().send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
    public static void sendToAllPlayers(CinemarrMessage payload) {
        required().send(PacketDistributor.ALL.noArg(), payload);
    }

    private static <T extends CinemarrMessage> void client(int id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        required().messageBuilder(type, id, PlayNetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> encoder.accept(payload, buffer)).decoder(decoder::apply)
                .consumerMainThread((payload, context) -> ClientPayloadBridge.accept(payload))
                .add();
    }

    private static <T extends CinemarrMessage> void server(int id, Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
            BiConsumer<ServerPlayer, T> action) {
        required().messageBuilder(type, id, PlayNetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> encoder.accept(payload, buffer)).decoder(decoder::apply)
                .consumerMainThread((payload, context) -> withSender(context, player -> action.accept(player, payload)))
                .add();
    }

    private static void withSender(NetworkEvent.Context context, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer sender = context.getSender();
        if (sender != null) action.accept(sender);
    }

    private static SimpleChannel required() {
        if (channel == null) throw new IllegalStateException("Cinemarr networking is not initialized");
        return channel;
    }

    private CinemarrNetwork() {}
}
