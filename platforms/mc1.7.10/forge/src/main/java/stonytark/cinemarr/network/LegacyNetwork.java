package stonytark.cinemarr.network;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import stonytark.cinemarr.core.network.BoundedPacketInbox;
import stonytark.cinemarr.core.network.HelloGate;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolException;
import stonytark.cinemarr.core.protocol.ProtocolLimits;

import java.util.HashMap;
import java.util.Map;

/** Forge 1.7.10 SimpleNetworkWrapper adapter for the television-only protocol 11. */
public final class LegacyNetwork {
    public interface ServerListener {
        void accept(EntityPlayerMP player, LegacyPacketTypes.Type<?> type, Object message);
    }

    public interface ClientListener {
        void accept(LegacyPacketTypes.Type<?> type, Object message);
    }

    private static final long HELLO_TIMEOUT_MS = ProtocolLimits.CLIENT_HELLO_TIMEOUT_MS;
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Cinemarr.MOD_ID);
    private static final LegacyNetwork INSTANCE = new LegacyNetwork();
    private static final BoundedPacketInbox<NetworkManager, ServerIncoming> SERVER_INBOX =
            new BoundedPacketInbox<>(512, 8L * 1024 * 1024, 64, 256L * 1024);
    private static final BoundedPacketInbox<NetworkManager, ClientIncoming> CLIENT_INBOX =
            new BoundedPacketInbox<>(1024, 16L * 1024 * 1024, 1024, 16L * 1024 * 1024);
    private static final int MAX_INCOMING_PER_TICK = 128;

    private final HelloGate<NetworkManager> helloGate = new HelloGate<>(HELLO_TIMEOUT_MS);
    private final Map<NetworkManager, EntityPlayerMP> pendingPlayers = new HashMap<>();
    private volatile NetworkManager clientConnection;
    private volatile ServerListener serverListener;
    private volatile ClientListener clientListener;
    private boolean registered;

    public static synchronized void register() {
        if (INSTANCE.registered) return;
        CHANNEL.registerMessage(ServerboundHandler.class, LegacyServerboundEnvelope.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientboundHandler.class, LegacyClientboundEnvelope.class, 1, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        INSTANCE.registered = true;
    }

    public static void setServerListener(ServerListener listener) { INSTANCE.serverListener = listener; }
    public static void setClientListener(ClientListener listener) { INSTANCE.clientListener = listener; }

    public static <T> void sendToPlayer(EntityPlayerMP player, LegacyPacketTypes.Type<T> type, T message) {
        CHANNEL.sendTo(LegacyClientboundEnvelope.of(type, message), player);
    }

    public static <T> void sendToAll(LegacyPacketTypes.Type<T> type, T message) {
        CHANNEL.sendToAll(LegacyClientboundEnvelope.of(type, message));
    }

    public static <T> void sendToServer(LegacyPacketTypes.Type<T> type, T message) {
        CHANNEL.sendToServer(LegacyServerboundEnvelope.of(type, message));
    }

    /** Prevents tracking payloads from overtaking the protocol hello on login. */
    public static boolean serverHandshakeComplete(EntityPlayerMP player) {
        return player != null && INSTANCE.helloGate.accepted(player.playerNetServerHandler.netManager);
    }

    public static synchronized void shutdown() {
        SERVER_INBOX.clear();
        CLIENT_INBOX.clear();
        INSTANCE.helloGate.clear();
        INSTANCE.pendingPlayers.clear();
        INSTANCE.clientConnection = null;
        INSTANCE.serverListener = null;
        INSTANCE.clientListener = null;
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        NetworkManager connection = player.playerNetServerHandler.netManager;
        helloGate.require(connection, System.currentTimeMillis());
        pendingPlayers.put(connection, player);
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        NetworkManager connection = ((EntityPlayerMP) event.player).playerNetServerHandler.netManager;
        helloGate.remove(connection);
        pendingPlayers.remove(connection);
        SERVER_INBOX.remove(connection);
    }

    @SubscribeEvent
    public void clientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        CLIENT_INBOX.clear();
        clientConnection = event.manager;
    }

    @SubscribeEvent
    public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (clientConnection == event.manager) clientConnection = null;
        CLIENT_INBOX.remove(event.manager);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        SERVER_INBOX.drain(MAX_INCOMING_PER_TICK, this::handleServer);
        long now = System.currentTimeMillis();
        for (NetworkManager connection : helloGate.expire(now)) {
            EntityPlayerMP player = pendingPlayers.remove(connection);
            if (player != null && connection.isChannelOpen()) {
                player.playerNetServerHandler.kickPlayerFromServer(
                        "Cinemarr protocol handshake timed out; install the matching Forge 1.7.10 Cinemarr client");
            }
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientListener listener = clientListener;
        CLIENT_INBOX.drain(MAX_INCOMING_PER_TICK, incoming -> {
            if (listener != null && incoming.connection == clientConnection && incoming.connection.isChannelOpen())
                listener.accept(incoming.type, incoming.message);
        });
    }

    private void handleServer(ServerIncoming incoming) {
        EntityPlayerMP player = incoming.player;
        NetworkManager connection = player.playerNetServerHandler.netManager;
        if (!connection.isChannelOpen()) return;
        if (incoming.type == LegacyPacketTypes.CLIENT_HELLO) {
            LegacyPacketTypes.ClientHello hello = (LegacyPacketTypes.ClientHello) incoming.message;
            if (!hello.valid()) {
                player.playerNetServerHandler.kickPlayerFromServer(
                        "Cinemarr protocol mismatch: server requires protocol " + Cinemarr.PROTOCOL);
                pendingPlayers.remove(connection);
                helloGate.remove(connection);
                return;
            }
            if (!helloGate.accept(connection)) return;
            pendingPlayers.remove(connection);
            sendToPlayer(player, LegacyPacketTypes.SERVER_HELLO,
                    new LegacyPacketTypes.ServerHello(Cinemarr.PROTOCOL, System.currentTimeMillis()));
            ServerListener listener = serverListener;
            if (listener != null) listener.accept(player, incoming.type, incoming.message);
            return;
        }
        if (!helloGate.accepted(connection)) {
            player.playerNetServerHandler.kickPlayerFromServer("Cinemarr protocol hello is required before play packets");
            return;
        }
        if (incoming.type == LegacyPacketTypes.TIME_SYNC_REQUEST) {
            LegacyPacketTypes.TimeSyncRequest request = (LegacyPacketTypes.TimeSyncRequest) incoming.message;
            sendToPlayer(player, LegacyPacketTypes.TIME_SYNC_RESPONSE,
                    new LegacyPacketTypes.TimeSyncResponse(request.nonce(), request.clientSentEpochMs(), System.currentTimeMillis()));
            return;
        }
        ServerListener listener = serverListener;
        if (listener != null) listener.accept(player, incoming.type, incoming.message);
    }

    public static final class ServerboundHandler implements IMessageHandler<LegacyServerboundEnvelope, IMessage> {
        @Override
        public IMessage onMessage(LegacyServerboundEnvelope envelope, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            NetworkManager connection = context.getServerHandler().netManager;
            if (!connection.isChannelOpen()) return null;
            try {
                if (!SERVER_INBOX.offer(connection, new ServerIncoming(player, envelope.type(),
                        envelope.decode(LegacyPacketTypes.Direction.SERVERBOUND)), envelope.payloadLength())) {
                    SERVER_INBOX.remove(connection);
                    connection.closeChannel(new ChatComponentText("Cinemarr incoming packet queue is full; reconnect after reducing request rate"));
                }
            } catch (ProtocolException malformed) {
                connection.closeChannel(new ChatComponentText("Malformed Cinemarr packet"));
            }
            return null;
        }
    }

    public static final class ClientboundHandler implements IMessageHandler<LegacyClientboundEnvelope, IMessage> {
        @Override
        public IMessage onMessage(LegacyClientboundEnvelope envelope, MessageContext context) {
            NetworkManager connection = context.getClientHandler().getNetworkManager();
            if (connection != INSTANCE.clientConnection || !connection.isChannelOpen()) return null;
            try {
                if (!CLIENT_INBOX.offer(connection, new ClientIncoming(connection, envelope.type(),
                        envelope.decode(LegacyPacketTypes.Direction.CLIENTBOUND)), envelope.payloadLength())) {
                    CLIENT_INBOX.remove(connection);
                    connection.closeChannel(new ChatComponentText("Cinemarr incoming media queue is full; reconnect to resume"));
                }
            } catch (ProtocolException malformed) {
                connection.closeChannel(new ChatComponentText("Malformed Cinemarr packet"));
            }
            return null;
        }
    }

    private static final class ServerIncoming {
        private final EntityPlayerMP player;
        private final LegacyPacketTypes.Type<?> type;
        private final Object message;
        private ServerIncoming(EntityPlayerMP player, LegacyPacketTypes.Type<?> type, Object message) {
            this.player = player;
            this.type = type;
            this.message = message;
        }
    }

    private static final class ClientIncoming {
        private final NetworkManager connection;
        private final LegacyPacketTypes.Type<?> type;
        private final Object message;
        private ClientIncoming(NetworkManager connection, LegacyPacketTypes.Type<?> type, Object message) {
            this.connection = connection;
            this.type = type;
            this.message = message;
        }
    }

    public static String incomingDiagnostics() {
        return "; inboxItems=" + SERVER_INBOX.size() + "; inboxBytes=" + SERVER_INBOX.retainedBytes()
                + "; inboxPeers=" + SERVER_INBOX.owners() + "; inboxRejected=" + SERVER_INBOX.rejectedPackets();
    }

    private LegacyNetwork() {}
}
