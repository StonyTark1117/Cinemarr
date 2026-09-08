package stonytark.cinemarr.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.ProtocolException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LegacyEnvelopeMalformedTest {
    @Test void everyTruncatedHelloIsRejectedAtEnvelopeOrPayloadBoundary() {
        LegacyEnvelope valid = LegacyEnvelope.encode(LegacyPacketTypes.CLIENT_HELLO,
                new LegacyPacketTypes.ClientHello(10));
        ByteBuf complete = Unpooled.buffer();
        try {
            valid.toBytes(complete);
            for (int length = 0; length < complete.readableBytes(); length++) {
                ByteBuf truncated = complete.copy(0, length);
                try {
                    LegacyEnvelope incoming = new LegacyEnvelope();
                    assertThrows(RuntimeException.class, () -> incoming.fromBytes(truncated));
                    assertEquals(0, incoming.payload().length, "Incomplete envelope must not publish a payload");
                } finally { truncated.release(); }
            }
            byte[] payload = valid.payload();
            for (int length = 0; length < payload.length; length++) {
                LegacyEnvelope truncated = new LegacyEnvelope(valid.messageId(), Arrays.copyOf(payload, length));
                assertThrows(ProtocolException.class, () -> truncated.decode(LegacyPacketTypes.Direction.SERVERBOUND));
            }
        } finally { complete.release(); }
    }

    @Test void seededOversizedAndNegativeLengthsNeverAllocateOrPublishPayloads() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            Random random = new Random(0xC1E0A11L);
            for (int sample = 0; sample < 512; sample++) {
                int length = sample % 2 == 0 ? -1 - random.nextInt(Integer.MAX_VALUE)
                        : LegacyEnvelope.MAX_ENVELOPE_BYTES + 1 + random.nextInt(1_000_000);
                ByteBuf bytes = Unpooled.buffer();
                try {
                    ByteBufUtils.writeVarInt(bytes, LegacyPacketTypes.CLIENT_HELLO.id(), 5);
                    ByteBufUtils.writeVarInt(bytes, length, 5);
                    bytes.writeZero(random.nextInt(64));
                    LegacyEnvelope incoming = new LegacyEnvelope();
                    assertThrows(ProtocolException.class, () -> incoming.fromBytes(bytes));
                    assertEquals(0, incoming.payload().length);
                } finally { bytes.release(); }
            }
        });
    }

    @Test void seededPayloadFuzzAcrossEveryRegisteredTypeIsBoundedAndDirectionChecked() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            Random random = new Random(0x71DE0L);
            int rejected = 0, accepted = 0;
            for (LegacyPacketTypes.Type<?> type : LegacyPacketTypes.TYPES.values()) {
                for (int sample = 0; sample < 32; sample++) {
                    byte[] payload = new byte[sample == 0 ? 0 : random.nextInt(256) + 1];
                    random.nextBytes(payload);
                    LegacyEnvelope incoming = new LegacyEnvelope(type.id(), payload);
                    LegacyPacketTypes.Direction wrong = type.direction() == LegacyPacketTypes.Direction.SERVERBOUND
                            ? LegacyPacketTypes.Direction.CLIENTBOUND : LegacyPacketTypes.Direction.SERVERBOUND;
                    assertThrows(ProtocolException.class, () -> incoming.decode(wrong));
                    try {
                        assertNotNull(incoming.decode(type.direction()));
                        accepted++;
                    } catch (ProtocolException malformed) { rejected++; }
                    assertArrayEquals(payload, incoming.payload(), "Decode must not mutate the received bytes");
                }
            }
            assertTrue(rejected > 0);
            assertTrue(accepted > 0, "Empty request types should still accept their valid empty payload");
        });
    }

    @Test void unterminatedVarIntHeadersFailWithoutConsumingAnUnboundedStream() {
        for (int size : new int[]{1, 5, 6, 32, 1024}) {
            ByteBuf bytes = Unpooled.buffer();
            try {
                for (int i = 0; i < size; i++) bytes.writeByte(0x80);
                assertThrows(RuntimeException.class, () -> new LegacyEnvelope().fromBytes(bytes));
                assertTrue(bytes.readerIndex() <= 6);
            } finally { bytes.release(); }
        }
    }
}
