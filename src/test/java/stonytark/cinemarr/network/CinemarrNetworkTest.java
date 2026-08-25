package stonytark.cinemarr.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.ProtocolGoldenVectors;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CinemarrNetworkTest {
    @Test
    void acceptsOnlyTheCurrentProtocol() {
        assertTrue(CinemarrNetwork.protocolMatches(CinemarrNetwork.PROTOCOL));
        assertFalse(CinemarrNetwork.protocolMatches(CinemarrNetwork.PROTOCOL - 1));
        assertFalse(CinemarrNetwork.protocolMatches(CinemarrNetwork.PROTOCOL + 1));
    }

    @Test void stationRequestCodecRoundTripsAndBoundsSeeds() {
        List<CinemarrPayloads.StationSeed> seeds = IntStream.range(0, 6).mapToObj(i ->
                new CinemarrPayloads.StationSeed(CinemarrPayloads.ItemKind.TRACK, Integer.toString(i), "Track " + i, "Artist")).toList();
        CinemarrPayloads.StationRequest decoded = roundTrip(CinemarrPayloads.StationRequest.CODEC,
                new CinemarrPayloads.StationRequest(CinemarrPayloads.StationAction.START_NOW, CinemarrPayloads.StationType.SONIC_ADVENTURE, false, 12, seeds));
        assertEquals(CinemarrPayloads.StationType.SONIC_ADVENTURE, decoded.stationType()); assertEquals(12, decoded.expectedGeneration());
        assertEquals(5, decoded.seeds().size());
    }

    @Test void neoForgeChunkAdapterMatchesTheSharedProtocolFiveGoldenVector() {
        RegistryFriendlyByteBuf buffer = buffer();
        CinemarrPayloads.ChunkRequest.CODEC.encode(buffer, new CinemarrPayloads.ChunkRequest(
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), 300, 17, 8));
        byte[] encoded = new byte[buffer.readableBytes()]; buffer.getBytes(0, encoded);
        assertEquals(ProtocolGoldenVectors.CHUNK_REQUEST, hex(encoded));
    }

    @Test void neoForgeStationAdapterMatchesTheSharedProtocolFiveGoldenVector() {
        RegistryFriendlyByteBuf buffer = buffer();
        CinemarrPayloads.StationRequest.CODEC.encode(buffer, new CinemarrPayloads.StationRequest(
                CinemarrPayloads.StationAction.START_NOW, CinemarrPayloads.StationType.SONIC_ADVENTURE,
                false, 12, List.of(new CinemarrPayloads.StationSeed(
                        CinemarrPayloads.ItemKind.TRACK, "42", "Song", "Artist"))));
        byte[] encoded = new byte[buffer.readableBytes()];
        buffer.getBytes(0, encoded);
        assertEquals(ProtocolGoldenVectors.STATION_REQUEST, hex(encoded));
    }

    @Test void neoForgeBrowseAdapterMatchesTheSharedProtocolFiveGoldenVector() {
        RegistryFriendlyByteBuf buffer = buffer();
        CinemarrPayloads.BrowseRequest.CODEC.encode(buffer,
                new CinemarrPayloads.BrowseRequest(CinemarrPayloads.BrowseKind.SEARCH, "A&B", 2));
        byte[] encoded = new byte[buffer.readableBytes()];
        buffer.getBytes(0, encoded);
        assertEquals(ProtocolGoldenVectors.BROWSE_REQUEST, hex(encoded));
    }

    @Test void neoForgeStateAdapterMatchesTheSharedProtocolFiveGoldenVector() {
        RegistryFriendlyByteBuf buffer = buffer();
        CinemarrPayloads.QueueEntry entry = new CinemarrPayloads.QueueEntry("1", "T", "A", 1_000,
                CinemarrPayloads.PlaybackOrigin.ADVENTURE, false);
        // QueueEntry is embedded unchanged in PlaybackState, StationState, and AdventurePreview.
        CinemarrPayloads.AdventurePreview.CODEC.encode(buffer,
                new CinemarrPayloads.AdventurePreview(0, "", List.of(entry)));
        byte[] encoded = new byte[buffer.readableBytes()];
        buffer.getBytes(0, encoded);
        assertEquals(ProtocolGoldenVectors.ADVENTURE_PREVIEW_WITH_QUEUE_ENTRY, hex(encoded));
    }

    @Test void stationStateCodecPreservesCapabilitySourceAndPreview() {
        var preview = new CinemarrPayloads.QueueEntry("1", "Track", "Artist", 1000, CinemarrPayloads.PlaybackOrigin.ADVENTURE, false);
        var state = new CinemarrPayloads.StationState(CinemarrPayloads.StationType.SONIC_ADVENTURE, true, false, 3,
                CinemarrPayloads.SonicCapability.ANALYSIS_INCOMPLETE, "Seed is missing analysis", "Adventure",
                List.of(new CinemarrPayloads.StationSeed(CinemarrPayloads.ItemKind.TRACK, "1", "Track", "Artist")), List.of(preview));
        assertEquals(state, roundTrip(CinemarrPayloads.StationState.CODEC, state));
    }

    @Test void playbackStateCodecPreservesTheCurrentNamedSource() {
        var state = new CinemarrPayloads.PlaybackState(CinemarrPayloads.PlaybackStatus.PLAYING, "", "Track", "Artist",
                false, 1_000, 2_000, 3_000, true, CinemarrPayloads.PlaybackOrigin.STATION, "Artist Radio: Seed", List.of());
        assertEquals(state, roundTrip(CinemarrPayloads.PlaybackState.CODEC, state));
    }

    @Test void stationRequestDecoderRejectsOversizedSeedLists() {
        RegistryFriendlyByteBuf buffer = buffer();
        buffer.writeEnum(CinemarrPayloads.StationAction.START);
        buffer.writeEnum(CinemarrPayloads.StationType.SONIC_MIX);
        buffer.writeBoolean(false); buffer.writeVarLong(1); buffer.writeVarInt(6);
        buffer.readerIndex(0);
        assertThrows(io.netty.handler.codec.DecoderException.class, () -> CinemarrPayloads.StationRequest.CODEC.decode(buffer));
    }

    private static <T> T roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = buffer();
        codec.encode(buffer, value); buffer.readerIndex(0); return codec.decode(buffer);
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append("%02x".formatted(item & 0xff));
        return result.toString();
    }
}
