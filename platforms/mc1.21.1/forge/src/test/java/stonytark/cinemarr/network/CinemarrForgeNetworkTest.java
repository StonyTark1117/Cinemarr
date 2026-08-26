package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.ProtocolGoldenVectors;
import stonytark.cinemarr.core.protocol.ProtocolLimits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinemarrForgeNetworkTest {
    @Test void acceptsOnlyCurrentVideoProtocol() {
        assertEquals(ProtocolLimits.VERSION, CinemarrNetwork.PROTOCOL);
        assertTrue(CinemarrNetwork.protocolMatches(ProtocolLimits.VERSION));
        assertFalse(CinemarrNetwork.protocolMatches(ProtocolLimits.VERSION - 1));
        assertFalse(CinemarrNetwork.protocolMatches(ProtocolLimits.VERSION + 1));
    }

    @Test void stationCodecMatchesTheSharedGoldenVector() {
        RegistryFriendlyByteBuf buffer = buffer();
        CinemarrPayloads.StationRequest.CODEC.encode(buffer, new CinemarrPayloads.StationRequest(
                CinemarrPayloads.StationAction.START_NOW, CinemarrPayloads.StationType.SONIC_ADVENTURE,
                false, 12, List.of(new CinemarrPayloads.StationSeed(
                        CinemarrPayloads.ItemKind.TRACK, "42", "Song", "Artist"))));
        byte[] encoded = new byte[buffer.readableBytes()];
        buffer.getBytes(0, encoded);
        assertEquals(ProtocolGoldenVectors.STATION_REQUEST, hex(encoded));
    }

    @Test void browseCodecMatchesTheSharedGoldenVector() {
        RegistryFriendlyByteBuf buffer = buffer();
        CinemarrPayloads.BrowseRequest.CODEC.encode(buffer,
                new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "A&B", 2));
        byte[] encoded = new byte[buffer.readableBytes()];
        buffer.getBytes(0, encoded);
        assertEquals(ProtocolGoldenVectors.BROWSE_REQUEST, hex(encoded));
    }

    @Test void malformedOversizedStationListIsRejected() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeEnum(CinemarrPayloads.StationAction.START);
        buffer.writeEnum(CinemarrPayloads.StationType.SONIC_MIX);
        buffer.writeBoolean(false);
        buffer.writeVarLong(1);
        buffer.writeVarInt(6);
        buffer.readerIndex(0);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> CinemarrPayloads.StationRequest.CODEC.decode(buffer));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
