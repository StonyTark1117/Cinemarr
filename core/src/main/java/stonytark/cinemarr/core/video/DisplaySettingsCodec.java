package stonytark.cinemarr.core.video;

/** Bounded versioned representation shared by NBT adapters and the wire protocol. */
public final class DisplaySettingsCodec {
    public static final int MAX_LENGTH = 192;
    private DisplaySettingsCodec() {}
    public static String encode(TvDisplaySettings value) {
        ResolutionChoice r = value.resolution();
        return "1|" + value.origin() + "|" + value.layout() + "|" + value.mapping() + "|" + r.kind()
                + "|" + r.preset() + "|" + r.width() + "|" + r.height() + "|" + value.revision();
    }
    public static TvDisplaySettings decode(String text) {
        if (text == null || text.length() > MAX_LENGTH) throw new IllegalArgumentException("Invalid display settings size");
        String[] fields = text.split("\\|", -1);
        if (fields.length != 9 || !fields[0].equals("1")) throw new IllegalArgumentException("Invalid display settings version");
        ResolutionChoice.Kind kind = ResolutionChoice.Kind.valueOf(fields[4]);
        int width = Integer.parseInt(fields[6]), height = Integer.parseInt(fields[7]);
        ResolutionChoice resolution = kind == ResolutionChoice.Kind.AUTO ? ResolutionChoice.AUTO
                : kind == ResolutionChoice.Kind.PRESET ? ResolutionChoice.preset(fields[5]) : ResolutionChoice.custom(width, height);
        if (resolution.width() != width || resolution.height() != height || !resolution.preset().equals(fields[5]))
            throw new IllegalArgumentException("Noncanonical resolution");
        return new TvDisplaySettings(TvDisplaySettings.Origin.valueOf(fields[1]), PresentationMode.valueOf(fields[2]),
                PixelMapping.valueOf(fields[3]), resolution, Long.parseLong(fields[8]));
    }
    /** Missing/corrupt saved data remains unclassified until the controller can be inspected. */
    public static TvDisplaySettings load(String text, PresentationMode layout) {
        try { return decode(text); }
        catch (IllegalArgumentException invalid) {
            return new TvDisplaySettings(TvDisplaySettings.Origin.UNKNOWN, layout, PixelMapping.DETAILED, ResolutionChoice.AUTO, 0);
        }
    }
}
