package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.network.Hashing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One compressed, hash-addressed media segment split into Minecraft-safe payloads. */
public final class MediaSegment {
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024;
    private final long presentationTimeMs;
    private final boolean keyframe;
    private final String sha256;
    private final byte[] bytes;

    public MediaSegment(long presentationTimeMs, boolean keyframe, byte[] bytes) {
        if (presentationTimeMs < 0 || bytes == null || bytes.length == 0) throw new IllegalArgumentException("Invalid segment");
        this.presentationTimeMs = presentationTimeMs;
        this.keyframe = keyframe;
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.sha256 = Hashing.sha256(this.bytes);
    }

    public long presentationTimeMs() { return presentationTimeMs; }
    public boolean keyframe() { return keyframe; }
    public String sha256() { return sha256; }
    public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    public List<byte[]> payloads() {
        List<byte[]> values = new ArrayList<byte[]>((bytes.length + MAX_PAYLOAD_BYTES - 1) / MAX_PAYLOAD_BYTES);
        for (int offset = 0; offset < bytes.length; offset += MAX_PAYLOAD_BYTES) {
            values.add(Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + MAX_PAYLOAD_BYTES)));
        }
        return Collections.unmodifiableList(values);
    }
}
