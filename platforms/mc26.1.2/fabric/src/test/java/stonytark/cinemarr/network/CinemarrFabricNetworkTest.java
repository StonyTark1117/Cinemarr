package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.ProtocolGoldenVectors;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinemarrFabricNetworkTest {
    @Test void acceptsOnlyTheCurrentProtocol() {
        assertEquals(stonytark.cinemarr.core.protocol.ProtocolLimits.VERSION, CinemarrNetwork.PROTOCOL);
        assertTrue(CinemarrNetwork.protocolMatches(CinemarrNetwork.PROTOCOL));
        assertFalse(CinemarrNetwork.protocolMatches(CinemarrNetwork.PROTOCOL - 1));
        assertFalse(CinemarrNetwork.protocolMatches(CinemarrNetwork.PROTOCOL + 1));
    }

    @Test void nativeCodecsConsumeTheSharedProtocolFiveVectors() {
        assertEquals(ProtocolGoldenVectors.BROWSE_REQUEST, encode(CinemarrPayloads.BrowseRequest.CODEC,
                new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "A&B", 2)));
        assertEquals(ProtocolGoldenVectors.STATION_REQUEST, encode(CinemarrPayloads.StationRequest.CODEC,
                new CinemarrPayloads.StationRequest(CinemarrPayloads.StationAction.START_NOW,
                        CinemarrPayloads.StationType.SONIC_ADVENTURE, false, 12,
                        List.of(new CinemarrPayloads.StationSeed(
                                CinemarrPayloads.ItemKind.TRACK, "42", "Song", "Artist")))));
        assertEquals(ProtocolGoldenVectors.CHUNK_REQUEST, encode(CinemarrPayloads.ChunkRequest.CODEC,
                new CinemarrPayloads.ChunkRequest(
                        UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), 300, 17, 8)));
    }

    @Test void malformedEnumsAndOversizedListsAreRejected() {
        RegistryFriendlyByteBuf invalidEnum = buffer();
        invalidEnum.writeVarInt(127);
        invalidEnum.writeUtf("");
        invalidEnum.writeVarInt(0);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> CinemarrPayloads.BrowseRequest.CODEC.decode(invalidEnum));

        RegistryFriendlyByteBuf oversized = buffer();
        oversized.writeEnum(CinemarrPayloads.StationAction.START);
        oversized.writeEnum(CinemarrPayloads.StationType.SONIC_MIX);
        oversized.writeBoolean(false);
        oversized.writeVarLong(1);
        oversized.writeVarInt(6);
        assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> CinemarrPayloads.StationRequest.CODEC.decode(oversized));
    }

    private static <T> String encode(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = buffer();
        codec.encode(buffer, value);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(0, bytes);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
