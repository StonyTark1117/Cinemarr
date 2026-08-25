package stonytark.cinemarr.network;

import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadConnection;
import net.minecraftforge.network.payload.PayloadFlow;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.server.CinemarrServer;

import java.util.function.BiConsumer;

public final class CinemarrNetwork {
    public static final int PROTOCOL = ProtocolLimits.VERSION;
    public static final String VERSION = Integer.toString(PROTOCOL);
    private static Channel<CustomPacketPayload> channel;

    public static boolean protocolMatches(int offered) { return offered == PROTOCOL; }

    public static void register() {
        if (channel != null) return;
        PayloadConnection<CustomPacketPayload> connection = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(Cinemarr.MODID, "main"))
                .networkProtocolVersion(PROTOCOL).payloadChannel();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> clientbound = connection.play().flow(PacketFlow.CLIENTBOUND);
        client(clientbound, CinemarrPayloads.OpenScreen.TYPE, CinemarrPayloads.OpenScreen.CODEC);
        client(clientbound, CinemarrPayloads.ServerHello.TYPE, CinemarrPayloads.ServerHello.CODEC);
        client(clientbound, CinemarrPayloads.TimeSyncResponse.TYPE, CinemarrPayloads.TimeSyncResponse.CODEC);
        client(clientbound, CinemarrPayloads.BrowseResults.TYPE, CinemarrPayloads.BrowseResults.CODEC);
        client(clientbound, CinemarrPayloads.AudioManifest.TYPE, CinemarrPayloads.AudioManifest.CODEC);
        client(clientbound, CinemarrPayloads.AudioChunk.TYPE, CinemarrPayloads.AudioChunk.CODEC);
        client(clientbound, CinemarrPayloads.PlaybackState.TYPE, CinemarrPayloads.PlaybackState.CODEC);
        client(clientbound, CinemarrPayloads.StationState.TYPE, CinemarrPayloads.StationState.CODEC);
        client(clientbound, CinemarrPayloads.AdventurePreview.TYPE, CinemarrPayloads.AdventurePreview.CODEC);
        client(clientbound, CinemarrPayloads.ErrorMessage.TYPE, CinemarrPayloads.ErrorMessage.CODEC);

        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> serverbound = connection.play().flow(PacketFlow.SERVERBOUND);
        serverbound.addMain(CinemarrPayloads.ClientHello.TYPE, CinemarrPayloads.ClientHello.CODEC, (payload, context) -> {
            if (!protocolMatches(payload.protocolVersion())) {
                withSender(context, sender -> {
                    String reason = "Cinemarr protocol mismatch: server requires version " + PROTOCOL;
                    Cinemarr.LOGGER.warn("Disconnecting {}: {}", sender.getGameProfile().getName(), reason);
                    sender.connection.disconnect(Component.literal(reason));
                });
            } else withSender(context, sender -> CinemarrServer.instance().hello(sender));
        });
        serverbound.addMain(CinemarrPayloads.TimeSyncRequest.TYPE, CinemarrPayloads.TimeSyncRequest.CODEC, (payload, context) ->
                withSender(context, sender -> channel.reply(new CinemarrPayloads.TimeSyncResponse(
                        payload.nonce(), payload.clientSentEpochMs(), System.currentTimeMillis()), context)));
        serverbound.addMain(CinemarrPayloads.BrowseRequest.TYPE, CinemarrPayloads.BrowseRequest.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().browse(sender, payload)));
        serverbound.addMain(CinemarrPayloads.QueueRequest.TYPE, CinemarrPayloads.QueueRequest.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().queue(sender, payload)));
        serverbound.addMain(CinemarrPayloads.ControlRequest.TYPE, CinemarrPayloads.ControlRequest.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().control(sender, payload)));
        serverbound.addMain(CinemarrPayloads.StationRequest.TYPE, CinemarrPayloads.StationRequest.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().station(sender, payload)));
        serverbound.addMain(CinemarrPayloads.ChunkRequest.TYPE, CinemarrPayloads.ChunkRequest.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().chunks(sender, payload)));
        serverbound.addMain(CinemarrPayloads.ChunkAcknowledgement.TYPE, CinemarrPayloads.ChunkAcknowledgement.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().acknowledge(sender, payload)));
        serverbound.addMain(CinemarrPayloads.AudioHealth.TYPE, CinemarrPayloads.AudioHealth.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().health(sender, payload)));
        serverbound.addMain(CinemarrPayloads.ManifestRequest.TYPE, CinemarrPayloads.ManifestRequest.CODEC,
                (payload, context) -> withSender(context, sender -> CinemarrServer.instance().sync(sender)));
        channel = clientbound.build();
    }

    public static void sendToServer(CinemarrMessage payload) { required().send((CustomPacketPayload)payload, PacketDistributor.SERVER.noArg()); }
    public static void sendToPlayer(ServerPlayer player, CinemarrMessage payload) { required().send((CustomPacketPayload)payload, PacketDistributor.PLAYER.with(player)); }
    public static void sendToAllPlayers(CinemarrMessage payload) { required().send((CustomPacketPayload)payload, PacketDistributor.ALL.noArg()); }

    private static <T extends CustomPacketPayload & CinemarrMessage> void client(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow,
                                                               CustomPacketPayload.Type<T> type,
                                                               StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        flow.addMain(type, codec, (payload, context) -> ClientPayloadBridge.accept(payload));
    }

    private static void withSender(CustomPayloadEvent.Context context, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer sender = context.getSender();
        if (sender != null) action.accept(sender);
    }

    private static Channel<CustomPacketPayload> required() {
        Channel<CustomPacketPayload> value = channel;
        if (value == null) throw new IllegalStateException("Cinemarr networking is not initialized");
        return value;
    }

    private CinemarrNetwork() {}
}
