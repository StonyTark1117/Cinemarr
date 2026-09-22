package stonytark.cinemarr.core.video;

/** UI-independent draft used by every Display Settings page implementation. */
public final class DisplaySettingsDraft {
    private final TvDisplaySettings original;
    private PresentationMode layout;
    private PixelMapping mapping;
    private ResolutionChoice resolution;
    private String error = "";

    public DisplaySettingsDraft(TvDisplaySettings current) {
        if (current == null) throw new IllegalArgumentException("Current display settings are required");
        original = current; layout = current.layout(); mapping = current.mapping(); resolution = current.resolution();
    }
    public TvDisplaySettings original() { return original; }
    public PresentationMode layout() { return layout; }
    public PixelMapping mapping() { return mapping; }
    public ResolutionChoice resolution() { return resolution; }
    public String error() { return error; }
    public boolean dirty() { return layout != original.layout() || mapping != original.mapping() || !resolution.equals(original.resolution()); }

    public DisplaySettingsDraft layout(PresentationMode value) { if (value == null) throw new IllegalArgumentException("Layout is required"); layout=value; error=""; return this; }
    public DisplaySettingsDraft mapping(PixelMapping value) {
        if (value == null) throw new IllegalArgumentException("Pixel mapping is required");
        if (original.origin() == TvDisplaySettings.Origin.QUICK && value != PixelMapping.DETAILED) { error="Quick TV mapping is locked to Detailed"; return this; }
        mapping=value; error=""; return this;
    }
    public DisplaySettingsDraft resolution(ResolutionChoice value) {
        if (value == null) throw new IllegalArgumentException("Resolution is required");
        if (original.origin() == TvDisplaySettings.Origin.QUICK && !value.equals(original.resolution())) { error="Quick TV resolution is locked to its preset"; return this; }
        resolution=value; error=""; return this;
    }
    /** Build a command payload carrying the expected revision; only the server advances it. */
    public TvDisplaySettings apply() {
        if (!error.isEmpty()) throw new IllegalStateException(error);
        try {
            original.apply(original.revision(), layout, mapping, resolution); // Validate without committing.
            return new TvDisplaySettings(original.origin(), layout, mapping, resolution, original.revision());
        }
        catch (RuntimeException failure) { error=failure.getMessage()==null?"Invalid display settings":failure.getMessage(); throw failure; }
    }
    public void cancel() { layout=original.layout(); mapping=original.mapping(); resolution=original.resolution(); error=""; }
}
