package stonytark.cinemarr.client;

/** One decoder-owned RGBA frame, safe to transfer to the render thread. */
public final class LegacyDecodedVideoFrame {
    private final long presentationTimeUs;
    private final int width;
    private final int height;
    private final byte[] rgba;

    public LegacyDecodedVideoFrame(long presentationTimeUs, int width, int height, byte[] rgba) {
        if (presentationTimeUs < 0 || width < 1 || height < 1 || rgba == null
                || rgba.length != width * height * 4) {
            throw new IllegalArgumentException("Invalid decoded video frame");
        }
        this.presentationTimeUs = presentationTimeUs;
        this.width = width;
        this.height = height;
        this.rgba = rgba.clone();
    }

    public long presentationTimeUs() { return presentationTimeUs; }
    public int width() { return width; }
    public int height() { return height; }
    public byte[] rgba() { return rgba.clone(); }
}
