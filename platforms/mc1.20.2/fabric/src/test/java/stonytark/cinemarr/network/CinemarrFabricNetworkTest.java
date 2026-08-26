package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.ProtocolGoldenVectors;
import stonytark.cinemarr.core.protocol.ProtocolLimits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinemarrFabricNetworkTest {
    @Test void acceptsOnlyCurrentVideoProtocol() {
        assertEquals(ProtocolLimits.VERSION, CinemarrNetwork.PROTOCOL);
        assertTrue(CinemarrNetwork.protocolMatches(ProtocolLimits.VERSION));
        assertFalse(CinemarrNetwork.protocolMatches(ProtocolLimits.VERSION-1));
        assertFalse(CinemarrNetwork.protocolMatches(ProtocolLimits.VERSION+1));
    }

    @Test void stationCodecMatchesTheSharedGoldenVector() {
        FriendlyByteBuf buffer = buffer();
        CinemarrPayloads.StationRequest value = new CinemarrPayloads.StationRequest(
                CinemarrPayloads.StationAction.START_NOW, CinemarrPayloads.StationType.SONIC_ADVENTURE,
                false, 12, List.of(new CinemarrPayloads.StationSeed(
                CinemarrPayloads.ItemKind.TRACK, "42", "Song", "Artist")));
        value.write(buffer);
        assertEquals(ProtocolGoldenVectors.STATION_REQUEST, hex(buffer));
        buffer.readerIndex(0);
        assertEquals(value, CinemarrPayloads.StationRequest.read(buffer));
    }

    @Test void browseCodecMatchesTheSharedGoldenVector() {
        FriendlyByteBuf buffer = buffer();
        CinemarrPayloads.BrowseRequest value = new CinemarrPayloads.BrowseRequest(
                CinemarrPayloads.BrowseKind.SEARCH, "A&B", 2);
        value.write(buffer);
        assertEquals(ProtocolGoldenVectors.BROWSE_REQUEST, hex(buffer));
        buffer.readerIndex(0);
        assertEquals(value, CinemarrPayloads.BrowseRequest.read(buffer));
    }

    @Test void malformedOversizedStationListIsRejected() {
        FriendlyByteBuf buffer = buffer();
        buffer.writeVarInt(CinemarrPayloads.StationAction.START.ordinal());
        buffer.writeVarInt(CinemarrPayloads.StationType.SONIC_MIX.ordinal());
        buffer.writeBoolean(false);
        buffer.writeVarLong(1);
        buffer.writeVarInt(6);
        buffer.readerIndex(0);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> CinemarrPayloads.StationRequest.read(buffer));
    }

    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }

    private static String hex(FriendlyByteBuf buffer) {
        byte[] value = new byte[buffer.readableBytes()];
        buffer.getBytes(0, value);
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
