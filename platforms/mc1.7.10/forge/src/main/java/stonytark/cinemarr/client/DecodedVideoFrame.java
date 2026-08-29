package stonytark.cinemarr.client;

/** Java 8 adapter used by the shared low-level hardware decoder. */
final class DecodedVideoFrame {
    private final long presentationTimeUs;
    private final int width;
    private final int height;
    private final byte[] rgba;

    DecodedVideoFrame(long presentationTimeUs, int width, int height, byte[] rgba) {
        this.presentationTimeUs = presentationTimeUs;
        this.width = width;
        this.height = height;
        this.rgba = rgba.clone();
    }

    long presentationTimeUs() { return presentationTimeUs; }
    int width() { return width; }
    int height() { return height; }
    byte[] rgbaView() { return rgba; }
}
