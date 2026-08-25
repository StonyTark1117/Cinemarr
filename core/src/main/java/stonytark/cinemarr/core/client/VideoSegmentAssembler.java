package stonytark.cinemarr.core.client;

import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.protocol.ProtocolLimits;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Generation-safe, bounded reassembly and SHA-256 verification for one compressed video segment. */
public final class VideoSegmentAssembler {
    public static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;
    private UUID sessionId;
    private long generation;
    private long requestId;
    private int segmentIndex = -1;
    private int totalChunks;
    private byte[][] chunks;
    private int received;
    private String expectedSha256;
    private long presentationTimeMs;
    private boolean keyframe;
    private int totalBytes;

    public synchronized void begin(UUID sessionId, long generation, long requestId, int segmentIndex, int totalChunks,
                                   String expectedSha256, long presentationTimeMs, boolean keyframe) {
        if (sessionId == null || generation < 0 || requestId < 1 || segmentIndex < 0 || totalChunks < 1
                || totalChunks > MAX_SEGMENT_BYTES / ProtocolLimits.MAX_VIDEO_CHUNK_BYTES + 1
                || expectedSha256 == null || expectedSha256.length() != 64 || presentationTimeMs < 0) {
            throw new IllegalArgumentException("Invalid video segment assembly");
        }
        this.sessionId = sessionId; this.generation = generation; this.requestId = requestId;
        this.segmentIndex = segmentIndex; this.totalChunks = totalChunks; this.expectedSha256 = expectedSha256;
        this.presentationTimeMs = presentationTimeMs; this.keyframe = keyframe;
        this.chunks = new byte[totalChunks][]; this.received = 0; this.totalBytes = 0;
    }

    public synchronized Optional<CompletedSegment> accept(UUID sessionId, long generation, long requestId,
                                                           int segmentIndex, int chunkIndex, int totalChunks,
                                                           String sha256, long presentationTimeMs, boolean keyframe,
                                                           byte[] data) {
        if (this.sessionId == null || !this.sessionId.equals(sessionId) || this.generation != generation
                || requestId < 1 || this.segmentIndex != segmentIndex || this.totalChunks != totalChunks
                || !this.expectedSha256.equalsIgnoreCase(sha256) || this.presentationTimeMs != presentationTimeMs
                || this.keyframe != keyframe || chunkIndex < 0 || chunkIndex >= totalChunks || data == null
                || data.length == 0 || data.length > ProtocolLimits.MAX_VIDEO_CHUNK_BYTES) return Optional.empty();
        if (chunks[chunkIndex] == null) {
            if ((long) totalBytes + data.length > MAX_SEGMENT_BYTES) { reset(); return Optional.empty(); }
            chunks[chunkIndex] = Arrays.copyOf(data, data.length); totalBytes += data.length; received++;
        } else if (!Arrays.equals(chunks[chunkIndex], data)) { reset(); return Optional.empty(); }
        if (received != totalChunks) return Optional.empty();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(totalBytes);
        for (byte[] chunk : chunks) bytes.write(chunk, 0, chunk.length);
        byte[] complete = bytes.toByteArray();
        if (!Hashing.matchesSha256(complete, expectedSha256)) { reset(); return Optional.empty(); }
        CompletedSegment value = new CompletedSegment(this.sessionId, this.generation, this.segmentIndex,
                this.presentationTimeMs, this.keyframe, complete, this.expectedSha256);
        reset();
        return Optional.of(value);
    }

    public synchronized void reset() {
        sessionId = null; generation = 0; requestId = 0; segmentIndex = -1; totalChunks = 0;
        chunks = null; received = 0; expectedSha256 = null; presentationTimeMs = 0; keyframe = false; totalBytes = 0;
    }

    public static final class CompletedSegment {
        private final UUID sessionId; private final long generation; private final int segmentIndex;
        private final long presentationTimeMs; private final boolean keyframe; private final byte[] data; private final String sha256;
        CompletedSegment(UUID sessionId,long generation,int segmentIndex,long presentationTimeMs,boolean keyframe,byte[] data,String sha256){this.sessionId=sessionId;this.generation=generation;this.segmentIndex=segmentIndex;this.presentationTimeMs=presentationTimeMs;this.keyframe=keyframe;this.data=data;this.sha256=sha256;}
        public UUID sessionId(){return sessionId;} public long generation(){return generation;} public int segmentIndex(){return segmentIndex;}
        public long presentationTimeMs(){return presentationTimeMs;} public boolean keyframe(){return keyframe;} public byte[] data(){return Arrays.copyOf(data,data.length);} public String sha256(){return sha256;}
    }
}
