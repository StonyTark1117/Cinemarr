package stonytark.cinemarr.core.screen;

import java.util.Locale;

/** Controls whether Quick TVs use safe display-sized prefabs or literal video-pixel walls. */
public enum QuickTvBuildMode {
    BOUNDED("bounded"),
    LITERAL("literal");

    private final String configValue;

    QuickTvBuildMode(String configValue) { this.configValue = configValue; }

    public String configValue() { return configValue; }

    public static QuickTvBuildMode parse(String value) {
        if (value != null) {
            String candidate = value.trim().toLowerCase(Locale.ROOT);
            for (QuickTvBuildMode mode : values()) if (mode.configValue.equals(candidate)) return mode;
        }
        return BOUNDED;
    }
}
