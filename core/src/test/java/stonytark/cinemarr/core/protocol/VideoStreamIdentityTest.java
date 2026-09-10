package stonytark.cinemarr.core.protocol;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.server.TransferGrantRegistry;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VideoStreamIdentityTest {
    private final VideoStreamIdentity current = new VideoStreamIdentity(UUID.randomUUID(), 41, UUID.randomUUID(), 7);

    @Test void everyMediaPacketRoundTripsBothIdentitiesAndGenerations() {
        check(VideoPackets.SEGMENT_MANIFEST, new VideoPackets.SegmentManifest(current, 640, 360, "mpegts", "h264", "aac", 90_000, false, Collections.emptyList()));
        check(VideoPackets.SEGMENT_MANIFEST_REQUEST, new VideoPackets.SegmentManifestRequest(current, 8));
        check(VideoPackets.SEGMENT_REQUEST, new VideoPackets.SegmentRequest(current, 3, 8, 0, 8));
        check(VideoPackets.SEGMENT_CHUNK, new VideoPackets.SegmentChunk(current, 3, 8, 0, 1, 16_000, true, Hashing.sha256(new byte[] {1}), new byte[] {1}));
        check(VideoPackets.SEGMENT_ACKNOWLEDGEMENT, new VideoPackets.SegmentAcknowledgement(current, 3, 8, 0, 100));
        check(VideoPackets.CLIENT_HEALTH, new VideoPackets.ClientHealth(current, "PLAYING", 0, 0, 0, 100, 0));
    }

    @Test void staleTimelineOrSiblingIdentityCannotCompleteAnAssembly() {
        byte[] bytes = {1, 2, 3}; String hash = Hashing.sha256(bytes);
        VideoSegmentAssembler assembler = new VideoSegmentAssembler();
        assembler.begin(current, 3, 8, 1, hash, 16_000, true);
        for (VideoStreamIdentity stale : alternatives())
            assertFalse(assembler.accept(stale, 3, 8, 0, 1, hash, 16_000, true, bytes).isPresent());
        VideoSegmentAssembler.CompletedSegment result = assembler.accept(current, 3, 8, 0, 1, hash, 16_000, true, bytes).get();
        assertEquals(current, result.identity()); assertArrayEquals(bytes, result.data());
    }

    @Test void wrongIdentityCannotReleaseAWindowByAckManifestOrCompletion() {
        TransferGrantRegistry grants = new TransferGrantRegistry(1000); UUID client = UUID.randomUUID();
        VideoPackets.SegmentRequest request = new VideoPackets.SegmentRequest(current, 3, 8, 0, 8);
        assertTrue(grants.tryAcquire(client, request, 0));
        for (VideoStreamIdentity stale : alternatives()) {
            grants.release(client, new VideoPackets.SegmentRequest(stale, 3, 8, 0, 8));
            assertFalse(grants.restartManifest(client, stale));
            assertFalse(grants.acknowledge(client, new VideoPackets.SegmentAcknowledgement(stale, 3, 8, 7, 0), 1));
            assertTrue(grants.owns(client, request, 1));
        }
        assertTrue(grants.acknowledge(client, new VideoPackets.SegmentAcknowledgement(current, 3, 8, 7, 0), 1));
    }

    @Test void equalityIncludesEveryIdentityComponent() {
        VideoStreamIdentity copy = new VideoStreamIdentity(current.timelineId(), 41, current.streamId(), 7);
        assertEquals(current, copy); assertEquals(current.hashCode(), copy.hashCode());
        for (VideoStreamIdentity other : alternatives()) assertNotEquals(current, other);
        assertThrows(IllegalArgumentException.class, () -> new VideoStreamIdentity(null, 0, current.streamId(), 0));
        assertThrows(IllegalArgumentException.class, () -> new VideoStreamIdentity(current.timelineId(), -1, current.streamId(), 0));
        assertThrows(IllegalArgumentException.class, () -> new VideoStreamIdentity(current.timelineId(), 0, current.streamId(), -1));
    }

    @Test void malformedWireGenerationsAreRejectedBeforeTelemetryFields() {
        for (boolean timeline : new boolean[] {false, true}) {
            ByteArrayWireOutput output = new ByteArrayWireOutput();
            output.writeUuid(current.timelineId()); output.writeVarLong(timeline ? -1 : 41);
            output.writeUuid(current.streamId()); output.writeVarLong(timeline ? 7 : -1);
            // No telemetry follows: the failure must identify the invalid identity itself.
            ProtocolException error = assertThrows(ProtocolException.class,
                    () -> VideoPackets.CLIENT_HEALTH.decode(new ByteArrayWireInput(output.toByteArray())));
            assertEquals("Invalid video stream identity", error.getMessage());
        }
    }

    private VideoStreamIdentity[] alternatives() {
        return new VideoStreamIdentity[] {
                new VideoStreamIdentity(UUID.randomUUID(), 41, current.streamId(), 7),
                new VideoStreamIdentity(current.timelineId(), 40, current.streamId(), 7),
                new VideoStreamIdentity(current.timelineId(), 41, UUID.randomUUID(), 7),
                new VideoStreamIdentity(current.timelineId(), 41, current.streamId(), 6)};
    }
    private <T extends VideoPackets.StreamMessage> void check(WireCodec<T> codec, T value) {
        ByteArrayWireOutput output = new ByteArrayWireOutput(); codec.encode(output, value);
        T decoded = codec.decode(new ByteArrayWireInput(output.toByteArray()));
        assertEquals(current, decoded.identity());
        assertEquals(current.timelineId(), decoded.timelineId()); assertEquals(41, decoded.timelineGeneration());
        assertEquals(current.streamId(), decoded.sessionId()); assertEquals(7, decoded.generation());
    }
}
