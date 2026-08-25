package stonytark.cinemarr.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
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
        sender.accept((CustomPacketPayload)payload);
    }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) {
        ServerPlayNetworking.send(player, (CustomPacketPayload)payload);
    }
    public static void sendToAllPlayers(CinemarrMessage payload) {
        MinecraftServer current = server;
        if (current != null) for (ServerPlayer player : current.getPlayerList().getPlayers()) sendToPlayer(player, payload);
    }

    public static void register() {
        registerS2C(CinemarrPayloads.OpenScreen.TYPE, CinemarrPayloads.OpenScreen.CODEC);
        registerS2C(CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC);
        registerS2C(CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC);
        registerS2C(CinemarrPayloads.BrowseResults.TYPE, CinemarrPayloads.BrowseResults.CODEC);
        registerS2C(CinemarrPayloads.AudioManifest.TYPE, CinemarrPayloads.AudioManifest.CODEC);
        registerS2C(CinemarrPayloads.AudioChunk.TYPE, CinemarrPayloads.AudioChunk.CODEC);
        registerS2C(CinemarrPayloads.PlaybackState.TYPE, CinemarrPayloads.PlaybackState.CODEC);
        registerS2C(CinemarrPayloads.StationState.TYPE, CinemarrPayloads.StationState.CODEC);
        registerS2C(CinemarrPayloads.AdventurePreview.TYPE, CinemarrPayloads.AdventurePreview.CODEC);
        registerS2C(CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC);
        registerC2S(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC);
        registerC2S(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC);
        registerC2S(CinemarrPayloads.BrowseRequest.TYPE, CinemarrPayloads.BrowseRequest.CODEC);
        registerC2S(CinemarrPayloads.QueueRequest.TYPE, CinemarrPayloads.QueueRequest.CODEC);
        registerC2S(CinemarrPayloads.ControlRequest.TYPE, CinemarrPayloads.ControlRequest.CODEC);
        registerC2S(CinemarrPayloads.StationRequest.TYPE, CinemarrPayloads.StationRequest.CODEC);
        registerC2S(CinemarrPayloads.ChunkRequest.TYPE, CinemarrPayloads.ChunkRequest.CODEC);
        registerC2S(CinemarrPayloads.ChunkAcknowledgement.TYPE, CinemarrPayloads.ChunkAcknowledgement.CODEC);
        registerC2S(CinemarrPayloads.AudioHealth.TYPE, CinemarrPayloads.AudioHealth.CODEC);
        registerC2S(CinemarrPayloads.ManifestRequest.TYPE, CinemarrPayloads.ManifestRequest.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.ClientHello.TYPE, (payload, context) -> {
            if (!protocolMatches(payload.protocolVersion())) {
                String reason = "Cinemarr protocol mismatch: server requires version " + PROTOCOL;
                Cinemarr.LOGGER.warn("Disconnecting {}: {}", context.player().getGameProfile().getName(), reason);
                context.player().connection.disconnect(Component.literal(reason));
            } else CinemarrServer.instance().hello(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.TimeSyncRequest.TYPE, (p, c) -> {
            if (CinemarrServer.instance().accepted(c.player())) {
                c.responseSender().sendPacket(new CinemarrPayloads.TimeSyncResponse(p.nonce(), p.clientSentEpochMs(), System.currentTimeMillis()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.BrowseRequest.TYPE, (p, c) -> CinemarrServer.instance().browse(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.QueueRequest.TYPE, (p, c) -> CinemarrServer.instance().queue(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.ControlRequest.TYPE, (p, c) -> CinemarrServer.instance().control(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.StationRequest.TYPE, (p, c) -> CinemarrServer.instance().station(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.ChunkRequest.TYPE, (p, c) -> CinemarrServer.instance().chunks(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.ChunkAcknowledgement.TYPE, (p, c) -> CinemarrServer.instance().acknowledge(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.AudioHealth.TYPE, (p, c) -> CinemarrServer.instance().health(c.player(), p));
        ServerPlayNetworking.registerGlobalReceiver(CinemarrPayloads.ManifestRequest.TYPE, (p, c) -> CinemarrServer.instance().sync(c.player()));
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
