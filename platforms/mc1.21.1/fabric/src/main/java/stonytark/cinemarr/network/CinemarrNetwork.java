package stonytark.cinemarr.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.server.CinemarrServer;

import java.util.function.Consumer;

public final class CinemarrNetwork {
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);
    private static volatile Consumer<CustomPacketPayload> clientSender;
    private static volatile MinecraftServer server;

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }
    public static void installClientSender(Consumer<CustomPacketPayload> sender) { clientSender = sender; }
    public static void activeServer(MinecraftServer value) { server = value; }
    public static void sendToServer(CinemarrMessage payload) {
        Consumer<CustomPacketPayload> sender = clientSender;
        if (sender == null) throw new IllegalStateException("Cinemarr client networking is not initialized");
        sender.accept((CustomPacketPayload) payload);
    }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) { ServerPlayNetworking.send(player, (CustomPacketPayload) payload); }
    public static void sendToAllPlayers(CinemarrMessage payload) {
        MinecraftServer current = server;
        if (current != null) for (ServerPlayer player : current.getPlayerList().getPlayers()) sendToPlayer(player, payload);
    }

    public static void register() {
        registerS2C(CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC);
        registerS2C(CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC);
        registerS2C(CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC);
        registerS2C(VideoPayloads.LibraryList.TYPE, VideoPayloads.LibraryList.CODEC);
        registerS2C(VideoPayloads.OpenVideoScreen.TYPE, VideoPayloads.OpenVideoScreen.CODEC);
        registerS2C(VideoPayloads.BrowseResults.TYPE, VideoPayloads.BrowseResults.CODEC);
        registerS2C(VideoPayloads.SessionState.TYPE, VideoPayloads.SessionState.CODEC);
        registerS2C(VideoPayloads.TelevisionRemoved.TYPE, VideoPayloads.TelevisionRemoved.CODEC);
        registerS2C(VideoPayloads.SessionQueue.TYPE, VideoPayloads.SessionQueue.CODEC);
        registerS2C(VideoPayloads.SegmentManifest.TYPE, VideoPayloads.SegmentManifest.CODEC);
        registerS2C(VideoPayloads.SegmentChunk.TYPE, VideoPayloads.SegmentChunk.CODEC);
        registerC2S(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC);
        registerC2S(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC);
        registerC2S(VideoPayloads.LibraryListRequest.TYPE, VideoPayloads.LibraryListRequest.CODEC);
        registerC2S(VideoPayloads.BrowseRequest.TYPE, VideoPayloads.BrowseRequest.CODEC);
        registerC2S(VideoPayloads.SessionCommand.TYPE, VideoPayloads.SessionCommand.CODEC);
        registerC2S(VideoPayloads.SegmentRequest.TYPE, VideoPayloads.SegmentRequest.CODEC);
        registerC2S(VideoPayloads.SegmentManifestRequest.TYPE, VideoPayloads.SegmentManifestRequest.CODEC);
        registerC2S(VideoPayloads.SegmentAcknowledgement.TYPE, VideoPayloads.SegmentAcknowledgement.CODEC);
        registerC2S(VideoPayloads.ClientHealth.TYPE, VideoPayloads.ClientHealth.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.ClientHello.TYPE, (payload, context) -> {
            if (!payload.valid()) context.player().connection.disconnect(Component.literal(
                    "Cinemarr protocol mismatch: server requires version " + PROTOCOL));
            else CinemarrServer.instance().hello(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.TimeSyncRequest.TYPE, (p, c) -> {
            if (CinemarrServer.instance().accepted(c.player())) c.responseSender().sendPacket(
                    new CinemarrPayloads.TimeSyncResponse(p.nonce(), p.clientSentEpochMs(), System.currentTimeMillis()));
        });
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.LibraryListRequest.TYPE, (p, c) -> CinemarrServer.instance().videoLibraries(c.player()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.BrowseRequest.TYPE, (p, c) -> CinemarrServer.instance().videoBrowse(c.player(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.SessionCommand.TYPE, (p, c) -> CinemarrServer.instance().videoCommand(c.player(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.SegmentRequest.TYPE, (p, c) -> CinemarrServer.instance().videoSegments(c.player(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.SegmentManifestRequest.TYPE, (p, c) -> CinemarrServer.instance().videoManifest(c.player(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.SegmentAcknowledgement.TYPE, (p, c) -> CinemarrServer.instance().videoAcknowledge(c.player(), p.value()));
        ServerPlayNetworking.registerGlobalReceiver(VideoPayloads.ClientHealth.TYPE, (p, c) -> CinemarrServer.instance().videoHealth(c.player(), p.value()));
    }

    private static <T extends CustomPacketPayload & CinemarrMessage> void registerS2C(CustomPacketPayload.Type<T> type,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.playS2C().register(type, codec);
    }
    private static <T extends CustomPacketPayload & CinemarrMessage> void registerC2S(CustomPacketPayload.Type<T> type,
            net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.playC2S().register(type, codec);
    }
    private CinemarrNetwork() {}
}
