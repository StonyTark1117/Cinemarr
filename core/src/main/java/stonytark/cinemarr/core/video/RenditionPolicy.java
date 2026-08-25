package stonytark.cinemarr.core.video;

/** Chooses a bounded even H.264 rendition; the client performs the exact final resample. */
public final class RenditionPolicy {
    public static Dimensions choose(int screenWidth, int screenHeight, int sourceWidth, int sourceHeight,
                                    int maximumWidth, int maximumHeight) {
        if (screenWidth < 1 || screenHeight < 1 || sourceWidth < 1 || sourceHeight < 1
                || maximumWidth < 2 || maximumHeight < 2) throw new IllegalArgumentException("Invalid rendition dimensions");
        double scale = Math.min(1.0, Math.min(maximumWidth / (double) sourceWidth, maximumHeight / (double) sourceHeight));
        int width = evenFloor(Math.max(2, (int) Math.floor(sourceWidth * scale)));
        int height = evenFloor(Math.max(2, (int) Math.floor(sourceHeight * scale)));
        // Tiny logical screens still receive a conventional decoder-friendly rendition.
        if (screenWidth <= 16 && screenHeight <= 16) {
            double tinyScale = Math.min(320.0 / sourceWidth, 180.0 / sourceHeight);
            width = evenFloor(Math.max(2, (int) Math.floor(sourceWidth * tinyScale)));
            height = evenFloor(Math.max(2, (int) Math.floor(sourceHeight * tinyScale)));
        }
        return new Dimensions(width, height);
    }

    private static int evenFloor(int value) { return value - (value & 1); }

    public static final class Dimensions {
        private final int width;
        private final int height;
        Dimensions(int width, int height) { this.width = width; this.height = height; }
        public int width() { return width; }
        public int height() { return height; }
    }
    private RenditionPolicy() {}
}
