package stonytark.cinemarr.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.server.CinemarrServer;

public final class CinemarrNetwork {
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }
    public static void sendToServer(CinemarrMessage payload) { ClientPacketDistributor.sendToServer((CustomPacketPayload) payload); }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) { PacketDistributor.sendToPlayer(player, (CustomPacketPayload) payload); }
    public static void sendToAllPlayers(CinemarrMessage payload) { PacketDistributor.sendToAllPlayers((CustomPacketPayload) payload); }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.LibraryList.TYPE, VideoPayloads.LibraryList.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.OpenVideoScreen.TYPE, VideoPayloads.OpenVideoScreen.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.BrowseResults.TYPE, VideoPayloads.BrowseResults.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SessionState.TYPE, VideoPayloads.SessionState.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.TelevisionRemoved.TYPE, VideoPayloads.TelevisionRemoved.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SessionQueue.TYPE, VideoPayloads.SessionQueue.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SegmentManifest.TYPE, VideoPayloads.SegmentManifest.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SegmentChunk.TYPE, VideoPayloads.SegmentChunk.CODEC, CinemarrNetwork::client);
        registrar.playToServer(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC, (payload, context) -> {
            if (!payload.valid()) {
                ServerPlayer player = (ServerPlayer) context.player();
                String reason = "Cinemarr protocol mismatch: server requires version " + PROTOCOL;
                Cinemarr.LOGGER.warn("Disconnecting {}: {}", player.getGameProfile().name(), reason);
                player.connection.send(new ClientboundDisconnectPacket(Component.literal(reason)));
            } else context.enqueueWork(() -> CinemarrServer.instance().hello((ServerPlayer) context.player()));
        });
        registrar.playToServer(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC,
                (payload, context) -> context.reply(new CinemarrPayloads.TimeSyncResponse(
                        payload.nonce(), payload.clientSentEpochMs(), System.currentTimeMillis())));
        registrar.playToServer(VideoPayloads.LibraryListRequest.TYPE, VideoPayloads.LibraryListRequest.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoLibraries((ServerPlayer) context.player())));
        registrar.playToServer(VideoPayloads.BrowseRequest.TYPE, VideoPayloads.BrowseRequest.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoBrowse((ServerPlayer) context.player(), payload.value())));
        registrar.playToServer(VideoPayloads.SessionCommand.TYPE, VideoPayloads.SessionCommand.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoCommand((ServerPlayer) context.player(), payload.value())));
        registrar.playToServer(VideoPayloads.SegmentRequest.TYPE, VideoPayloads.SegmentRequest.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoSegments((ServerPlayer) context.player(), payload.value())));
        registrar.playToServer(VideoPayloads.SegmentManifestRequest.TYPE, VideoPayloads.SegmentManifestRequest.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoManifest((ServerPlayer) context.player(), payload.value())));
        registrar.playToServer(VideoPayloads.SegmentAcknowledgement.TYPE, VideoPayloads.SegmentAcknowledgement.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoAcknowledge((ServerPlayer) context.player(), payload.value())));
        registrar.playToServer(VideoPayloads.ClientHealth.TYPE, VideoPayloads.ClientHealth.CODEC,
                (payload, context) -> context.enqueueWork(() -> CinemarrServer.instance().videoHealth((ServerPlayer) context.player(), payload.value())));
    }

    private static void client(CustomPacketPayload payload,
                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadBridge.accept((CinemarrMessage) payload));
    }
    private CinemarrNetwork() {}
}
