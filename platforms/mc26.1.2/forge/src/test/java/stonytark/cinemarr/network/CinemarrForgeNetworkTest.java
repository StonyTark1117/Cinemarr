package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinemarrForgeNetworkTest {
    @Test void protocolTenHelloAndTimeSyncRoundTrip() {
        assertEquals(10, CinemarrNetwork.PROTOCOL);
        assertTrue(CinemarrNetwork.protocolMatches(10));
        assertFalse(CinemarrNetwork.protocolMatches(8));
        RegistryFriendlyByteBuf hello = buffer();
        var expectedHello = new CinemarrPayloads.ClientHello(10);
        CinemarrPayloads.ClientHello.CODEC.encode(hello, expectedHello);
        assertEquals(expectedHello, CinemarrPayloads.ClientHello.CODEC.decode(hello));
        RegistryFriendlyByteBuf time = buffer();
        var expectedTime = new CinemarrPayloads.TimeSyncResponse(7, 1000, 1025);
        CinemarrPayloads.TimeSyncResponse.CODEC.encode(time, expectedTime);
        assertEquals(expectedTime, CinemarrPayloads.TimeSyncResponse.CODEC.decode(time));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
