package stonytark.cinemarr.core.video;

/** Immutable, revisioned settings. UNKNOWN is reserved for unloaded legacy controllers. */
public final class TvDisplaySettings {
    public enum Origin { UNKNOWN, CUSTOM, QUICK }
    private final Origin origin;
    private final PresentationMode layout;
    private final PixelMapping mapping;
    private final ResolutionChoice resolution;
    private final long revision;

    public TvDisplaySettings(Origin origin, PresentationMode layout, PixelMapping mapping,
                             ResolutionChoice resolution, long revision) {
        if (origin == null || layout == null || mapping == null || resolution == null || revision < 0)
            throw new IllegalArgumentException("Invalid display settings");
        if (origin == Origin.QUICK && (mapping != PixelMapping.DETAILED || resolution.kind() != ResolutionChoice.Kind.PRESET))
            throw new IllegalArgumentException("Quick TV display quality is fixed by its preset");
        this.origin = origin; this.layout = layout; this.mapping = mapping; this.resolution = resolution; this.revision = revision;
    }
    public static TvDisplaySettings defaults(PresentationMode layout) {
        return new TvDisplaySettings(Origin.CUSTOM, layout, PixelMapping.DETAILED, ResolutionChoice.AUTO, 0);
    }
    public TvDisplaySettings apply(long expectedRevision, PresentationMode layout, PixelMapping mapping, ResolutionChoice resolution) {
        if (expectedRevision != revision) throw new IllegalStateException("TV display settings changed; refresh before applying");
        if (origin == Origin.UNKNOWN && (mapping != this.mapping || !resolution.equals(this.resolution))) throw new IllegalStateException("TV controller must be loaded before changing display settings");
        if (origin == Origin.QUICK && (mapping != this.mapping || !resolution.equals(this.resolution)))
            throw new IllegalArgumentException("Quick TV display quality is fixed by its preset");
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Display settings revision exhausted");
        return new TvDisplaySettings(origin, layout, mapping, resolution, revision + 1);
    }
    public Origin origin() { return origin; }
    public PresentationMode layout() { return layout; }
    public PixelMapping mapping() { return mapping; }
    public ResolutionChoice resolution() { return resolution; }
    public long revision() { return revision; }
}
