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

public final class CinemarrNetwork {
    /** Bumped for source-aware queues, stations, and Sonic Adventure payloads. */
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }

    public static void sendToServer(CinemarrMessage payload) { PacketDistributor.sendToServer((CustomPacketPayload)payload); }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) { PacketDistributor.sendToPlayer(player, (CustomPacketPayload)payload); }
    public static void sendToAllPlayers(CinemarrMessage payload) { PacketDistributor.sendToAllPlayers((CustomPacketPayload)payload); }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(CinemarrPayloads.OpenScreen.TYPE, CinemarrPayloads.OpenScreen.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.BrowseResults.TYPE, CinemarrPayloads.BrowseResults.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.AudioManifest.TYPE, CinemarrPayloads.AudioManifest.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.AudioChunk.TYPE, CinemarrPayloads.AudioChunk.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.PlaybackState.TYPE, CinemarrPayloads.PlaybackState.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.StationState.TYPE, CinemarrPayloads.StationState.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.AdventurePreview.TYPE, CinemarrPayloads.AdventurePreview.CODEC, CinemarrNetwork::client);
        registrar.playToClient(CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.LibraryList.TYPE, VideoPayloads.LibraryList.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.BrowseResults.TYPE, VideoPayloads.BrowseResults.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SessionState.TYPE, VideoPayloads.SessionState.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SegmentManifest.TYPE, VideoPayloads.SegmentManifest.CODEC, CinemarrNetwork::client);
        registrar.playToClient(VideoPayloads.SegmentChunk.TYPE, VideoPayloads.SegmentChunk.CODEC, CinemarrNetwork::client);
        registrar.playToServer(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC, (payload, context) -> {
            if (!protocolMatches(payload.protocolVersion())) {
                context.disconnect(Component.literal("Cinemarr protocol mismatch: server requires version " + PROTOCOL));
            } else {
                context.enqueueWork(() -> CinemarrServer.instance().hello((ServerPlayer)context.player()));
            }
        });
        registrar.playToServer(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC,
                (p, c) -> c.reply(new CinemarrPayloads.TimeSyncResponse(p.nonce(), p.clientSentEpochMs(), System.currentTimeMillis())));
        registrar.playToServer(CinemarrPayloads.BrowseRequest.TYPE, CinemarrPayloads.BrowseRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().browse((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.QueueRequest.TYPE, CinemarrPayloads.QueueRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().queue((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.ControlRequest.TYPE, CinemarrPayloads.ControlRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().control((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.StationRequest.TYPE, CinemarrPayloads.StationRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().station((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.ChunkRequest.TYPE, CinemarrPayloads.ChunkRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().chunks((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.ChunkAcknowledgement.TYPE, CinemarrPayloads.ChunkAcknowledgement.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().acknowledge((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.AudioHealth.TYPE, CinemarrPayloads.AudioHealth.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().health((ServerPlayer)c.player(), p)));
        registrar.playToServer(CinemarrPayloads.ManifestRequest.TYPE, CinemarrPayloads.ManifestRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().sync((ServerPlayer)c.player())));
        registrar.playToServer(VideoPayloads.LibraryListRequest.TYPE, VideoPayloads.LibraryListRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoLibraries((ServerPlayer)c.player())));
        registrar.playToServer(VideoPayloads.BrowseRequest.TYPE, VideoPayloads.BrowseRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoBrowse((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.SessionCommand.TYPE, VideoPayloads.SessionCommand.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoCommand((ServerPlayer)c.player(), p.value())));
        registrar.playToServer(VideoPayloads.SegmentRequest.TYPE, VideoPayloads.SegmentRequest.CODEC,
                (p, c) -> c.enqueueWork(() -> CinemarrServer.instance().videoSegments((ServerPlayer)c.player(), p.value())));
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
