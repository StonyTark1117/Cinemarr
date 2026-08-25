package stonytark.cinemarr.client;

/** One decoder-owned RGBA frame. The byte array is safe to hand to the render thread. */
public record DecodedVideoFrame(long presentationTimeUs, int width, int height, byte[] rgba) {
    public DecodedVideoFrame {
        if (presentationTimeUs < 0 || width < 1 || height < 1 || rgba == null || rgba.length != width * height * 4) {
            throw new IllegalArgumentException("Invalid decoded video frame");
        }
        rgba = rgba.clone();
    }

    @Override public byte[] rgba() { return rgba.clone(); }
    byte[] rgbaView() { return rgba; }
}
