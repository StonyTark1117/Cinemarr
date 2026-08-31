package stonytark.cinemarr.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadConnection;
import net.minecraftforge.network.payload.PayloadFlow;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.server.CinemarrServer;

public final class CinemarrNetwork {
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);
    private static Channel<CustomPacketPayload> channel;

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }

    public static void register() {
        if (channel != null) return;
        PayloadConnection<CustomPacketPayload> connection = ChannelBuilder
                .named(Identifier.fromNamespaceAndPath(Cinemarr.MODID, "main"))
                .networkProtocolVersion(PROTOCOL).payloadChannel();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> clientbound = connection.play().flow(PacketFlow.CLIENTBOUND);
        client(clientbound, CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC);
        client(clientbound, CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC);
        client(clientbound, CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC);
        client(clientbound, VideoPayloads.LibraryList.TYPE, VideoPayloads.LibraryList.CODEC);
        client(clientbound, VideoPayloads.OpenVideoScreen.TYPE, VideoPayloads.OpenVideoScreen.CODEC);
        client(clientbound, VideoPayloads.BrowseResults.TYPE, VideoPayloads.BrowseResults.CODEC);
        client(clientbound, VideoPayloads.SessionState.TYPE, VideoPayloads.SessionState.CODEC);
        client(clientbound, VideoPayloads.TelevisionRemoved.TYPE, VideoPayloads.TelevisionRemoved.CODEC);
        client(clientbound, VideoPayloads.SessionQueue.TYPE, VideoPayloads.SessionQueue.CODEC);
        client(clientbound, VideoPayloads.SegmentManifest.TYPE, VideoPayloads.SegmentManifest.CODEC);
        client(clientbound, VideoPayloads.SegmentChunk.TYPE, VideoPayloads.SegmentChunk.CODEC);

        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> serverbound = connection.play().flow(PacketFlow.SERVERBOUND);
        serverbound.addMain(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC, (payload, context) -> {
            if (!payload.valid()) withSender(context, sender -> sender.connection.disconnect(
                    Component.literal("Cinemarr protocol mismatch: server requires version " + PROTOCOL)));
            else withSender(context, sender -> CinemarrServer.instance().hello(sender));
        });
        serverbound.addMain(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC,
                (payload, context) -> withSender(context, sender -> channel.reply(new CinemarrPayloads.TimeSyncResponse(
                        payload.nonce(), payload.clientSentEpochMs(), System.currentTimeMillis()), context)));
        server(serverbound, VideoPayloads.LibraryListRequest.TYPE, VideoPayloads.LibraryListRequest.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoLibraries(sender));
        server(serverbound, VideoPayloads.BrowseRequest.TYPE, VideoPayloads.BrowseRequest.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoBrowse(sender, payload.value()));
        server(serverbound, VideoPayloads.SessionCommand.TYPE, VideoPayloads.SessionCommand.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoCommand(sender, payload.value()));
        server(serverbound, VideoPayloads.SegmentRequest.TYPE, VideoPayloads.SegmentRequest.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoSegments(sender, payload.value()));
        server(serverbound, VideoPayloads.SegmentManifestRequest.TYPE, VideoPayloads.SegmentManifestRequest.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoManifest(sender, payload.value()));
        server(serverbound, VideoPayloads.SegmentAcknowledgement.TYPE, VideoPayloads.SegmentAcknowledgement.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoAcknowledge(sender, payload.value()));
        server(serverbound, VideoPayloads.ClientHealth.TYPE, VideoPayloads.ClientHealth.CODEC,
                (payload, sender) -> CinemarrServer.instance().videoHealth(sender, payload.value()));
        channel = clientbound.build();
    }

    public static void sendToServer(CinemarrMessage payload) { required().send((CustomPacketPayload) payload, PacketDistributor.SERVER.noArg()); }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) { required().send((CustomPacketPayload) payload, PacketDistributor.PLAYER.with(player)); }
    public static void sendToAllPlayers(CinemarrMessage payload) { required().send((CustomPacketPayload) payload, PacketDistributor.ALL.noArg()); }

    private static <T extends CustomPacketPayload & CinemarrMessage> void client(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
            CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        flow.addMain(type, codec, (payload, context) -> ClientPayloadBridge.accept(payload));
    }
    private static <T extends CustomPacketPayload & CinemarrMessage> void server(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
            CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> codec,
            java.util.function.BiConsumer<T, ServerPlayer> handler) {
        flow.addMain(type, codec, (payload, context) -> withSender(context, sender -> handler.accept(payload, sender)));
    }
    private static void withSender(CustomPayloadEvent.Context context, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer sender = context.getSender();
        if (sender != null) action.accept(sender);
    }
    private static Channel<CustomPacketPayload> required() {
        if (channel == null) throw new IllegalStateException("Cinemarr networking is not initialized");
        return channel;
    }
    private CinemarrNetwork() {}
}
