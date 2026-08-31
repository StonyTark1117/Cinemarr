package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CinemarrNeoForgeNetworkTest {
    @Test void protocolNineHelloAndTimeSyncRoundTrip(){
        FriendlyByteBuf hello=new FriendlyByteBuf(Unpooled.buffer());new CinemarrPayloads.ClientHello(9).write(hello);
        assertEquals(new CinemarrPayloads.ClientHello(9),CinemarrPayloads.ClientHello.read(hello));
        FriendlyByteBuf time=new FriendlyByteBuf(Unpooled.buffer());var expected=new CinemarrPayloads.TimeSyncResponse(7,1000,1025);expected.write(time);
        assertEquals(expected,CinemarrPayloads.TimeSyncResponse.read(time));
    }
}
