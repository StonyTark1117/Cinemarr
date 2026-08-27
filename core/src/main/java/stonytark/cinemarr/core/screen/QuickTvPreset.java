package stonytark.cinemarr.core.screen;

import java.util.Locale;

/**
 * Named quick-build televisions.
 *
 * <p>The rendition dimensions are exact video targets. The physical dimensions deliberately stay
 * bounded: a literal 8K wall would contain more than thirty-three million blocks and conflict with
 * Cinemarr's normal construction limits. Quick televisions are dense prefabs rendered through the
 * same single texture and mask as a hand-built screen.</p>
 */
public enum QuickTvPreset {
    P144("144p", 256, 144, 16, 9, 1),
    P240("240p", 426, 240, 24, 14, 2),
    P480("480p", 854, 480, 32, 18, 3),
    P720("720p", 1280, 720, 48, 27, 4),
    P1080("1080p", 1920, 1080, 64, 36, 5),
    P1440("1440p", 2560, 1440, 80, 45, 6),
    P4K("4k", 3840, 2160, 96, 54, 7),
    P8K("8k", 7680, 4320, 128, 72, 8);

    private final String id;
    private final int renditionWidth;
    private final int renditionHeight;
    private final int physicalWidth;
    private final int physicalHeight;
    private final int resourceTier;

    QuickTvPreset(String id, int renditionWidth, int renditionHeight, int physicalWidth, int physicalHeight,
                  int resourceTier) {
        this.id = id;
        this.renditionWidth = renditionWidth;
        this.renditionHeight = renditionHeight;
        this.physicalWidth = physicalWidth;
        this.physicalHeight = physicalHeight;
        this.resourceTier = resourceTier;
    }

    public String id() { return id; }
    public int renditionWidth() { return renditionWidth; }
    public int renditionHeight() { return renditionHeight; }
    public int physicalWidth() { return physicalWidth; }
    public int physicalHeight() { return physicalHeight; }
    public int physicalPixels() { return physicalWidth * physicalHeight; }
    public int resourceTier() { return resourceTier; }
    public String configKey() { return "quickTv" + id.toUpperCase(Locale.ROOT).replace("P", "p") + "Enabled"; }

    public static QuickTvPreset byId(String id) {
        if (id != null) for (QuickTvPreset value : values()) if (value.id.equalsIgnoreCase(id.trim())) return value;
        throw new IllegalArgumentException("Unknown quick TV preset: " + id);
    }
}
