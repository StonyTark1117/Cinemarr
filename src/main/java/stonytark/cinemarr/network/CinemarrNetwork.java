package stonytark.cinemarr.network;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import stonytark.cinemarr.server.CinemarrServer;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.network.RequiredClientGate;

public final class CinemarrNetwork {
    /** Video protocol shared by the server and required client. */
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }

    public static void sendToServer(CinemarrMessage payload) { PacketDistributor.sendToServer((CustomPacketPayload)payload); }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) { PacketDistributor.sendToPlayer(player, (CustomPacketPayload)payload); }
    public static void sendToAllPlayers(CinemarrMessage payload) { PacketDistributor.sendToAllPlayers((CustomPacketPayload)payload); }

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
        registrar.playToClient(VideoPayloads.SessionQueue.TYPE,VideoPayloads.SessionQueue.CODEC,CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SegmentManifest.TYPE, VideoPayloads.SegmentManifest.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SegmentChunk.TYPE, VideoPayloads.SegmentChunk.CODEC, CinemarrNetwork::client);
        registrar.playToServer(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC, (payload, context) -> {
            if (!payload.valid()) {
                context.disconnect(Component.literal("Cinemarr protocol mismatch: server requires version " + PROTOCOL));
            } else {
                context.enqueueWork(() -> CinemarrServer.instance().hello((ServerPlayer)context.player()));
            }
        });
        registrar.playToServer(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC,
                (p, c) -> {if(RequiredClientGate.accepted(((ServerPlayer)c.player()).getUUID()))c.reply(new CinemarrPayloads.TimeSyncResponse(p.nonce(), p.clientSentEpochMs(), System.currentTimeMillis()));});
        registrar.playToServer(VideoPayloads.LibraryListRequest.TYPE, VideoPayloads.LibraryListRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoLibraries((ServerPlayer)c.player())));
        registrar.playToServer(VideoPayloads.BrowseRequest.TYPE, VideoPayloads.BrowseRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoBrowse((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.SessionCommand.TYPE, VideoPayloads.SessionCommand.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoCommand((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.SegmentRequest.TYPE, VideoPayloads.SegmentRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoSegments((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.SegmentManifestRequest.TYPE, VideoPayloads.SegmentManifestRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoManifest((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.SegmentAcknowledgement.TYPE, VideoPayloads.SegmentAcknowledgement.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoAcknowledge((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.ClientHealth.TYPE, VideoPayloads.ClientHealth.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoHealth((ServerPlayer)c.player(), p.value())));
    }

    private static void client(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload,
                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadBridge.accept((CinemarrMessage)payload));
    }

    private CinemarrNetwork() {}
}
