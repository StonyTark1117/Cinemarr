package stonytark.cinemarr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.CinemarrMessage;

/** Connection negotiation, clock synchronization, and television errors. */
public final class CinemarrPayloads {
    private static ResourceLocation id(String path){return new ResourceLocation(Cinemarr.MODID,path);}
    public record ClientHello(int protocolVersion) implements CinemarrMessage {public static final ResourceLocation ID=id("client_hello");public static ClientHello read(FriendlyByteBuf b){return new ClientHello(b.readVarInt());}public void write(FriendlyByteBuf b){b.writeVarInt(protocolVersion);}}
    public record ServerHello(int protocolVersion,long serverEpochMs) implements CinemarrMessage {public static final ResourceLocation ID=id("server_hello");public static ServerHello read(FriendlyByteBuf b){return new ServerHello(b.readVarInt(),b.readLong());}public void write(FriendlyByteBuf b){b.writeVarInt(protocolVersion);b.writeLong(serverEpochMs);}}
    public record TimeSyncRequest(long nonce,long clientSentEpochMs) implements CinemarrMessage {public static final ResourceLocation ID=id("time_sync_request");public static TimeSyncRequest read(FriendlyByteBuf b){return new TimeSyncRequest(b.readLong(),b.readLong());}public void write(FriendlyByteBuf b){b.writeLong(nonce);b.writeLong(clientSentEpochMs);}}
    public record TimeSyncResponse(long nonce,long clientSentEpochMs,long serverEpochMs) implements CinemarrMessage {public static final ResourceLocation ID=id("time_sync_response");public static TimeSyncResponse read(FriendlyByteBuf b){return new TimeSyncResponse(b.readLong(),b.readLong(),b.readLong());}public void write(FriendlyByteBuf b){b.writeLong(nonce);b.writeLong(clientSentEpochMs);b.writeLong(serverEpochMs);}}
    public record ErrorMessage(String message) implements CinemarrMessage {public static final ResourceLocation ID=id("error");public ErrorMessage{message=message==null?"":message.substring(0,Math.min(message.length(),256));}public static ErrorMessage read(FriendlyByteBuf b){return new ErrorMessage(b.readUtf(256));}public void write(FriendlyByteBuf b){b.writeUtf(message,256);}}
    public static ResourceLocation idOf(CinemarrMessage message){if(message instanceof ClientHello)return ClientHello.ID;if(message instanceof ServerHello)return ServerHello.ID;if(message instanceof TimeSyncRequest)return TimeSyncRequest.ID;if(message instanceof TimeSyncResponse)return TimeSyncResponse.ID;if(message instanceof ErrorMessage)return ErrorMessage.ID;throw new IllegalArgumentException("Unknown Cinemarr message type: "+message.getClass().getName());}
    public static void write(CinemarrMessage message,FriendlyByteBuf buffer){if(message instanceof ClientHello value)value.write(buffer);else if(message instanceof ServerHello value)value.write(buffer);else if(message instanceof TimeSyncRequest value)value.write(buffer);else if(message instanceof TimeSyncResponse value)value.write(buffer);else if(message instanceof ErrorMessage value)value.write(buffer);else throw new IllegalArgumentException("Unknown Cinemarr message type: "+message.getClass().getName());}
    private CinemarrPayloads(){}
}
