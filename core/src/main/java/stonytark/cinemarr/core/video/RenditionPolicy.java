package stonytark.cinemarr.core.video;

/** Chooses a bounded even H.264 rendition; the client performs the exact final resample. */
public final class RenditionPolicy {
    /**
     * Chooses a rendition for a persisted screen. Quick-TV screens store an
     * explicit named rendition that differs from their bounded physical block
     * dimensions; hand-built screens store their physical dimensions and keep
     * the normal source/max policy.
     */
    public static Dimensions chooseForScreen(int screenWidth, int screenHeight,
                                             int requestedWidth, int requestedHeight,
                                             int sourceWidth, int sourceHeight,
                                             int maximumWidth, int maximumHeight) {
        if (requestedWidth < 1 || requestedHeight < 1) throw new IllegalArgumentException("Invalid requested rendition");
        boolean explicitTarget = requestedWidth != screenWidth || requestedHeight != screenHeight;
        int widthLimit = explicitTarget ? Math.min(maximumWidth, requestedWidth) : maximumWidth;
        int heightLimit = explicitTarget ? Math.min(maximumHeight, requestedHeight) : maximumHeight;
        return choose(screenWidth, screenHeight, sourceWidth, sourceHeight, widthLimit, heightLimit);
    }

    public static Dimensions choose(int screenWidth, int screenHeight, int sourceWidth, int sourceHeight,
                                    int maximumWidth, int maximumHeight) {
        if (screenWidth < 1 || screenHeight < 1 || sourceWidth < 1 || sourceHeight < 1
                || maximumWidth < 2 || maximumHeight < 2) throw new IllegalArgumentException("Invalid rendition dimensions");
        double scale = Math.min(1.0, Math.min(maximumWidth / (double) sourceWidth, maximumHeight / (double) sourceHeight));
        int width = evenFloor(Math.max(2, (int) Math.floor(sourceWidth * scale)));
        int height = evenFloor(Math.max(2, (int) Math.floor(sourceHeight * scale)));
        // Tiny logical screens still receive a conventional decoder-friendly rendition.
        if (screenWidth <= 16 && screenHeight <= 16) {
            double tinyScale = Math.min(1.0, Math.min(Math.min(320.0, maximumWidth) / sourceWidth,
                    Math.min(180.0, maximumHeight) / sourceHeight));
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
