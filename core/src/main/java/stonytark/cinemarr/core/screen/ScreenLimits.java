package stonytark.cinemarr.core.screen;

/** Operator-configurable construction limits. */
public final class ScreenLimits {
    public static final ScreenLimits DEFAULTS = new ScreenLimits(4, 65_536, 2_048);

    private final int minimumPixels;
    private final int maximumPixels;
    private final int maximumDimension;

    public ScreenLimits(int minimumPixels, int maximumPixels, int maximumDimension) {
        if (minimumPixels < 1 || maximumPixels < minimumPixels || maximumDimension < 1) {
            throw new IllegalArgumentException("Invalid screen limits");
        }
        this.minimumPixels = minimumPixels;
        this.maximumPixels = maximumPixels;
        this.maximumDimension = maximumDimension;
    }

    public int minimumPixels() { return minimumPixels; }
    public int maximumPixels() { return maximumPixels; }
    public int maximumDimension() { return maximumDimension; }
}
