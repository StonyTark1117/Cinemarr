package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CinemarrNetworkTest {
    @Test void acceptsOnlyProtocolNine() {
        assertEquals(9, CinemarrNetwork.PROTOCOL);
        assertTrue(CinemarrNetwork.protocolMatches(9));
        assertFalse(CinemarrNetwork.protocolMatches(8));
        assertFalse(CinemarrNetwork.protocolMatches(10));
    }

    @Test void helloAndTimeSyncRoundTrip() {
        assertEquals(new CinemarrPayloads.ClientHello(9), roundTrip(CinemarrPayloads.ClientHello.CODEC,
                new CinemarrPayloads.ClientHello(9)));
        var response = new CinemarrPayloads.TimeSyncResponse(7, 1_000, 1_025);
        assertEquals(response, roundTrip(CinemarrPayloads.TimeSyncResponse.CODEC, response));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        codec.encode(buffer, value); buffer.readerIndex(0); return codec.decode(buffer);
    }
}
