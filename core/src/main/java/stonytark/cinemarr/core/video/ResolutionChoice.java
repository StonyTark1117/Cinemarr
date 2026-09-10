package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.client.DecodedBufferBudget;
import stonytark.cinemarr.core.screen.QuickTvPreset;

/** Requested source-aspect-preserving stream bounds, not an encoded-size promise. */
public final class ResolutionChoice {
    public enum Kind { AUTO, PRESET, CUSTOM }
    public static final ResolutionChoice AUTO = new ResolutionChoice(Kind.AUTO, "", 0, 0);
    private final Kind kind;
    private final String preset;
    private final int width, height;

    private ResolutionChoice(Kind kind, String preset, int width, int height) {
        this.kind = kind; this.preset = preset; this.width = width; this.height = height;
    }
    public static ResolutionChoice preset(String id) {
        QuickTvPreset value = QuickTvPreset.byId(id);
        return new ResolutionChoice(Kind.PRESET, value.id(), value.renditionWidth(), value.renditionHeight());
    }
    public static ResolutionChoice custom(int width, int height) {
        if (width < 2 || height < 2 || width > 8192 || height > 8192)
            throw new IllegalArgumentException("Resolution dimensions must be between 2 and 8192");
        DecodedBufferBudget.rgbaBytes(width, height);
        return new ResolutionChoice(Kind.CUSTOM, "", width, height);
    }
    public Kind kind() { return kind; }
    public String preset() { return preset; }
    public int width() { return width; }
    public int height() { return height; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ResolutionChoice)) return false;
        ResolutionChoice value = (ResolutionChoice) other;
        return kind == value.kind && preset.equals(value.preset) && width == value.width && height == value.height;
    }
    @Override public int hashCode() { return ((kind.hashCode() * 31 + preset.hashCode()) * 31 + width) * 31 + height; }
    @Override public String toString() { return kind == Kind.AUTO ? "Auto" : kind == Kind.PRESET ? preset : width + "x" + height; }
}
