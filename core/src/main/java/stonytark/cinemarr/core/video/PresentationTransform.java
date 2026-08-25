package stonytark.cinemarr.core.video;

/** Exact destination raster transform after Plex supplies a practical codec-safe rendition. */
public final class PresentationTransform {
    private final double scaleX;
    private final double scaleY;
    private final double offsetX;
    private final double offsetY;

    private PresentationTransform(double scaleX, double scaleY, double offsetX, double offsetY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public static PresentationTransform create(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight,
                                               PresentationMode mode) {
        if (sourceWidth < 1 || sourceHeight < 1 || targetWidth < 1 || targetHeight < 1 || mode == null) {
            throw new IllegalArgumentException("Positive dimensions and mode are required");
        }
        double x = targetWidth / (double) sourceWidth;
        double y = targetHeight / (double) sourceHeight;
        if (mode == PresentationMode.STRETCH) return new PresentationTransform(x, y, 0, 0);
        double scale = mode == PresentationMode.FIT ? Math.min(x, y) : Math.max(x, y);
        return new PresentationTransform(scale, scale,
                (targetWidth - sourceWidth * scale) / 2.0,
                (targetHeight - sourceHeight * scale) / 2.0);
    }

    public double sourceX(double targetX) { return (targetX - offsetX) / scaleX; }
    public double sourceY(double targetY) { return (targetY - offsetY) / scaleY; }
    public boolean samplesSource(double targetX, double targetY, int sourceWidth, int sourceHeight) {
        double x = sourceX(targetX);
        double y = sourceY(targetY);
        return x >= 0 && y >= 0 && x < sourceWidth && y < sourceHeight;
    }
    public double scaleX() { return scaleX; }
    public double scaleY() { return scaleY; }
    public double offsetX() { return offsetX; }
    public double offsetY() { return offsetY; }
}
