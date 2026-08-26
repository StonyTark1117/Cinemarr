package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.server.CinemarrServer;

import java.util.function.Consumer;

public final class CinemarrNetwork {
    public static final int PROTOCOL=ProtocolLimits.VERSION;
    public static final String VERSION=Integer.toString(PROTOCOL);
    private static volatile Consumer<CinemarrMessage> clientSender;
    private static volatile MinecraftServer server;
    public static boolean protocolMatches(int offered){return offered==PROTOCOL;}
    public static void installClientSender(Consumer<CinemarrMessage> sender){clientSender=sender;}
    public static void activeServer(MinecraftServer value){server=value;}
    public static void sendToServer(CinemarrMessage payload){Consumer<CinemarrMessage> sender=clientSender;if(sender==null)throw new IllegalStateException("Cinemarr client networking is not initialized");sender.accept(payload);}
    public static void sendToPlayer(ServerPlayer player,CinemarrMessage payload){FriendlyByteBuf buffer=PacketByteBufs.create();ResourceLocation id;if(VideoPayloads.supports(payload)){VideoPayloads.write(payload,buffer);id=VideoPayloads.idOf(payload);}else{CinemarrPayloads.write(payload,buffer);id=CinemarrPayloads.idOf(payload);}ServerPlayNetworking.send(player,id,buffer);}
    public static void sendToAllPlayers(CinemarrMessage payload){MinecraftServer current=server;if(current!=null)for(ServerPlayer player:current.getPlayerList().getPlayers())sendToPlayer(player,payload);}
    public static void register(){
        receive(CinemarrPayloads.ClientHello.ID,CinemarrPayloads.ClientHello::read,(player,payload)->{if(!protocolMatches(payload.protocolVersion())){Component reason=Component.literal("Cinemarr protocol mismatch: server requires version "+PROTOCOL);player.connection.send(new ClientboundDisconnectPacket(reason));}else CinemarrServer.instance().hello(player);});
        receive(CinemarrPayloads.TimeSyncRequest.ID,CinemarrPayloads.TimeSyncRequest::read,(player,payload)->{if(CinemarrServer.instance().accepted(player))sendToPlayer(player,new CinemarrPayloads.TimeSyncResponse(payload.nonce(),payload.clientSentEpochMs(),System.currentTimeMillis()));});
        receive(VideoPayloads.LibraryListRequest.ID,VideoPayloads.LibraryListRequest::read,(player,payload)->CinemarrServer.instance().videoLibraries(player));
        receive(VideoPayloads.BrowseRequest.ID,VideoPayloads.BrowseRequest::read,(player,payload)->CinemarrServer.instance().videoBrowse(player,payload.value()));
        receive(VideoPayloads.SessionCommand.ID,VideoPayloads.SessionCommand::read,(player,payload)->CinemarrServer.instance().videoCommand(player,payload.value()));
        receive(VideoPayloads.SegmentRequest.ID,VideoPayloads.SegmentRequest::read,(player,payload)->CinemarrServer.instance().videoSegments(player,payload.value()));
        receive(VideoPayloads.SegmentManifestRequest.ID,VideoPayloads.SegmentManifestRequest::read,(player,payload)->CinemarrServer.instance().videoManifest(player,payload.value()));
        receive(VideoPayloads.SegmentAcknowledgement.ID,VideoPayloads.SegmentAcknowledgement::read,(player,payload)->CinemarrServer.instance().videoAcknowledge(player,payload.value()));
        receive(VideoPayloads.ClientHealth.ID,VideoPayloads.ClientHealth::read,(player,payload)->CinemarrServer.instance().videoHealth(player,payload.value()));
    }
    public static FriendlyByteBuf encode(CinemarrMessage payload){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());if(VideoPayloads.supports(payload))VideoPayloads.write(payload,b);else CinemarrPayloads.write(payload,b);return b;}
    private static <T extends CinemarrMessage> void receive(ResourceLocation id,Decoder<T> decoder,ServerHandler<T> action){ServerPlayNetworking.registerGlobalReceiver(id,(server,player,handler,buffer,responseSender)->{final T payload;try{payload=decoder.read(buffer);if(buffer.readableBytes()!=0)throw new IllegalArgumentException("Trailing bytes in Cinemarr packet");}catch(RuntimeException malformed){server.execute(()->player.connection.disconnect(Component.literal("Malformed Cinemarr packet")));return;}server.execute(()->action.handle(player,payload));});}
    @FunctionalInterface public interface Decoder<T>{T read(FriendlyByteBuf buffer);}
    @FunctionalInterface private interface ServerHandler<T>{void handle(ServerPlayer player,T payload);}
    private CinemarrNetwork(){}
}
