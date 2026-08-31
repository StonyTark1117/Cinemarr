package stonytark.cinemarr.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.protocol.ProtocolException;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoPackets;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyEnvelopeTest {
    @Test void advertisesAndCarriesProtocolNineHello() {
        assertEquals(ProtocolLimits.VERSION, Cinemarr.PROTOCOL);
        LegacyEnvelope outgoing = LegacyEnvelope.encode(LegacyPacketTypes.CLIENT_HELLO,
                new LegacyPacketTypes.ClientHello(9));
        ByteBuf buffer = Unpooled.buffer(); outgoing.toBytes(buffer);
        LegacyEnvelope incoming = new LegacyEnvelope(); incoming.fromBytes(buffer);
        LegacyPacketTypes.ClientHello decoded = (LegacyPacketTypes.ClientHello) incoming.decode(LegacyPacketTypes.Direction.SERVERBOUND);
        assertEquals(LegacyPacketTypes.CLIENT_HELLO.id(), incoming.messageId());
        assertEquals("09", hex(incoming.payload()));
        assertEquals(9, decoded.protocolVersion());
        assertEquals(0, buffer.readableBytes());
    }

    @Test void preservesBoundedVideoChunkFields() {
        UUID session = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        VideoPackets.SegmentChunk outgoing = new VideoPackets.SegmentChunk(session, 3, 11, 4, 1, 2,
                9_000, true, "abcd", new byte[] { 5, 6, 7 });
        LegacyEnvelope envelope = LegacyEnvelope.encode(LegacyPacketTypes.VIDEO_SEGMENT_CHUNK, outgoing);
        VideoPackets.SegmentChunk decoded = (VideoPackets.SegmentChunk) envelope.decode(LegacyPacketTypes.Direction.CLIENTBOUND);
        assertEquals(session, decoded.sessionId()); assertEquals(3, decoded.generation()); assertEquals(11, decoded.requestId());
        assertEquals(4, decoded.segmentIndex()); assertEquals(1, decoded.chunkIndex()); assertEquals(2, decoded.totalChunks());
        assertEquals(9_000, decoded.presentationTimeMs()); assertArrayEquals(new byte[] { 5, 6, 7 }, decoded.data());
    }

    @Test void rejectsWrongDirectionUnknownIdsAndTrailingBytes() {
        LegacyEnvelope hello = LegacyEnvelope.encode(LegacyPacketTypes.CLIENT_HELLO, new LegacyPacketTypes.ClientHello(9));
        assertThrows(ProtocolException.class, () -> hello.decode(LegacyPacketTypes.Direction.CLIENTBOUND));
        ByteBuf unknown = Unpooled.buffer();
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(unknown, 127, 5);
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(unknown, 0, 5);
        assertThrows(ProtocolException.class, () -> new LegacyEnvelope().fromBytes(unknown));
        byte[] validPayload = hello.payload(); ByteBuf trailing = Unpooled.buffer();
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(trailing, hello.messageId(), 5);
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(trailing, validPayload.length, 5);
        trailing.writeBytes(validPayload); trailing.writeByte(99);
        assertThrows(ProtocolException.class, () -> new LegacyEnvelope().fromBytes(trailing));
    }

    @Test void rejectsOversizedDeclaredPayload() {
        ByteBuf oversized = Unpooled.buffer();
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(oversized, LegacyPacketTypes.VIDEO_SEGMENT_CHUNK.id(), 5);
        cpw.mods.fml.common.network.ByteBufUtils.writeVarInt(oversized, LegacyEnvelope.MAX_ENVELOPE_BYTES + 1, 5);
        assertThrows(ProtocolException.class, () -> new LegacyEnvelope().fromBytes(oversized));
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format("%02x", item & 0xff));
        return result.toString();
    }
}
