package stonytark.cinemarr.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrConfig {
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = CLIENT_BUILDER
            .comment("Render Cinemarr video screens on this client.")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue VOLUME = CLIENT_BUILDER
            .comment("Additional Cinemarr volume multiplier.")
            .defineInRange("volume", 1.0, 0.0, 1.0);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private static final CinemarrSettings.ClientValues CLIENT_VALUES = new CinemarrSettings.ClientValues() {
        @Override public boolean enabled() { return ENABLED.get(); }
        @Override public void enabled(boolean value) { ENABLED.set(value); }
        @Override public void saveEnabled() { ENABLED.save(); }
        @Override public double volume() { return VOLUME.get(); }
        @Override public void volume(double value) { VOLUME.set(value); }
        @Override public void saveVolume() { VOLUME.save(); }
    };

    private CinemarrConfig() {}

    public static CinemarrSettings.ClientValues clientValues() { return CLIENT_VALUES; }
}
