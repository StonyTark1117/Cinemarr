package stonytark.cinemarr.core.client;

import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/** Generation-safe, bounded reassembly and SHA-256 verification for one compressed video segment. */
public final class VideoSegmentAssembler {
    public static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;
    private VideoStreamIdentity identity;
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
        begin(new VideoStreamIdentity(sessionId, generation, sessionId, generation), requestId, segmentIndex, totalChunks,
                expectedSha256, presentationTimeMs, keyframe);
    }

    public synchronized void begin(VideoStreamIdentity identity, long requestId, int segmentIndex, int totalChunks,
                                   String expectedSha256, long presentationTimeMs, boolean keyframe) {
        if (identity == null || requestId < 1 || segmentIndex < 0 || totalChunks < 1
                || totalChunks > MAX_SEGMENT_BYTES / ProtocolLimits.MAX_VIDEO_CHUNK_BYTES + 1
                || expectedSha256 == null || expectedSha256.length() != 64 || presentationTimeMs < 0) {
            throw new IllegalArgumentException("Invalid video segment assembly");
        }
        this.identity = identity; this.requestId = requestId;
        this.segmentIndex = segmentIndex; this.totalChunks = totalChunks; this.expectedSha256 = expectedSha256;
        this.presentationTimeMs = presentationTimeMs; this.keyframe = keyframe;
        this.chunks = new byte[totalChunks][]; this.received = 0; this.totalBytes = 0;
    }

    public synchronized Optional<CompletedSegment> accept(UUID sessionId, long generation, long requestId,
                                                           int segmentIndex, int chunkIndex, int totalChunks,
                                                           String sha256, long presentationTimeMs, boolean keyframe,
                                                           byte[] data) {
        if (sessionId == null || generation < 0) return Optional.empty();
        return accept(new VideoStreamIdentity(sessionId, generation, sessionId, generation), requestId, segmentIndex,
                chunkIndex, totalChunks, sha256, presentationTimeMs, keyframe, data);
    }

    public synchronized Optional<CompletedSegment> accept(VideoStreamIdentity identity, long requestId,
                                                          int segmentIndex, int chunkIndex, int totalChunks,
                                                          String sha256, long presentationTimeMs, boolean keyframe, byte[] data) {
        if (this.identity == null || !this.identity.equals(identity)
                || requestId != this.requestId || this.segmentIndex != segmentIndex || this.totalChunks != totalChunks
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
        CompletedSegment value = new CompletedSegment(this.identity, this.segmentIndex,
                this.presentationTimeMs, this.keyframe, complete, this.expectedSha256);
        reset();
        return Optional.of(value);
    }

    public synchronized void reset() {
        identity = null; requestId = 0; segmentIndex = -1; totalChunks = 0;
        chunks = null; received = 0; expectedSha256 = null; presentationTimeMs = 0; keyframe = false; totalBytes = 0;
    }

    public static final class CompletedSegment {
        private final VideoStreamIdentity identity; private final int segmentIndex;
        private final long presentationTimeMs; private final boolean keyframe; private final byte[] data; private final String sha256;
        CompletedSegment(VideoStreamIdentity identity,int segmentIndex,long presentationTimeMs,boolean keyframe,byte[] data,String sha256){this.identity=identity;this.segmentIndex=segmentIndex;this.presentationTimeMs=presentationTimeMs;this.keyframe=keyframe;this.data=data;this.sha256=sha256;}
        public VideoStreamIdentity identity(){return identity;} public UUID sessionId(){return identity.streamId();} public long generation(){return identity.streamGeneration();} public int segmentIndex(){return segmentIndex;}
        public long presentationTimeMs(){return presentationTimeMs;} public boolean keyframe(){return keyframe;} public int byteLength(){return data.length;} public byte[] data(){return Arrays.copyOf(data,data.length);} public String sha256(){return sha256;}
    }
}
