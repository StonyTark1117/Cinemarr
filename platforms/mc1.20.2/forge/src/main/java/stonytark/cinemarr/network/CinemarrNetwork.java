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
        client(id++, CinemarrPayloads.ServerHello.class, CinemarrPayloads.ServerHello::write, CinemarrPayloads.ServerHello::read);
        client(id++, CinemarrPayloads.TimeSyncResponse.class, CinemarrPayloads.TimeSyncResponse::write, CinemarrPayloads.TimeSyncResponse::read);
        client(id++,VideoPayloads.OpenVideoScreen.class,VideoPayloads.OpenVideoScreen::write,VideoPayloads.OpenVideoScreen::read);client(id++,VideoPayloads.LibraryList.class,VideoPayloads.LibraryList::write,VideoPayloads.LibraryList::read);client(id++,VideoPayloads.BrowseResults.class,VideoPayloads.BrowseResults::write,VideoPayloads.BrowseResults::read);client(id++,VideoPayloads.SessionState.class,VideoPayloads.SessionState::write,VideoPayloads.SessionState::read);client(id++,VideoPayloads.TelevisionRemoved.class,VideoPayloads.TelevisionRemoved::write,VideoPayloads.TelevisionRemoved::read);client(id++,VideoPayloads.SessionQueue.class,VideoPayloads.SessionQueue::write,VideoPayloads.SessionQueue::read);client(id++,VideoPayloads.SegmentManifest.class,VideoPayloads.SegmentManifest::write,VideoPayloads.SegmentManifest::read);client(id++,VideoPayloads.SegmentChunk.class,VideoPayloads.SegmentChunk::write,VideoPayloads.SegmentChunk::read);

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
        server(id++,VideoPayloads.LibraryListRequest.class,VideoPayloads.LibraryListRequest::write,VideoPayloads.LibraryListRequest::read,(p,v)->CinemarrServer.instance().videoLibraries(p));server(id++,VideoPayloads.BrowseRequest.class,VideoPayloads.BrowseRequest::write,VideoPayloads.BrowseRequest::read,(p,v)->CinemarrServer.instance().videoBrowse(p,v.value()));server(id++,VideoPayloads.SessionCommand.class,VideoPayloads.SessionCommand::write,VideoPayloads.SessionCommand::read,(p,v)->CinemarrServer.instance().videoCommand(p,v.value()));server(id++,VideoPayloads.SegmentRequest.class,VideoPayloads.SegmentRequest::write,VideoPayloads.SegmentRequest::read,(p,v)->CinemarrServer.instance().videoSegments(p,v.value()));server(id++,VideoPayloads.SegmentManifestRequest.class,VideoPayloads.SegmentManifestRequest::write,VideoPayloads.SegmentManifestRequest::read,(p,v)->CinemarrServer.instance().videoManifest(p,v.value()));server(id++,VideoPayloads.SegmentAcknowledgement.class,VideoPayloads.SegmentAcknowledgement::write,VideoPayloads.SegmentAcknowledgement::read,(p,v)->CinemarrServer.instance().videoAcknowledge(p,v.value()));server(id,VideoPayloads.ClientHealth.class,VideoPayloads.ClientHealth::write,VideoPayloads.ClientHealth::read,(p,v)->CinemarrServer.instance().videoHealth(p,v.value()));
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
