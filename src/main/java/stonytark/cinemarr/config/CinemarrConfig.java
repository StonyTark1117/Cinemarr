package stonytark.cinemarr.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

public final class CinemarrConfig {
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = CLIENT_BUILDER
            .comment("Render Cinemarr video screens on this client.")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue VOLUME = CLIENT_BUILDER
            .comment("Additional Cinemarr volume multiplier.")
            .defineInRange("volume", 1.0, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<String> VIDEO_DECODER_BACKEND = CLIENT_BUILDER
            .comment("Video decoder: software, auto, or vaapi. Hardware modes are experimental and may be slower.")
            .define("videoDecoderBackend", "software");
    public static final ModConfigSpec.ConfigValue<String> VIDEO_DECODER_DEVICE = CLIENT_BUILDER
            .comment("Optional backend-specific GPU/render-device selector. Blank selects the default device.")
            .define("videoDecoderDevice", "");
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private static final CinemarrSettings.ClientValues CLIENT_VALUES = new CinemarrSettings.ClientValues() {
        @Override public boolean enabled() { return ENABLED.get(); }
        @Override public void enabled(boolean value) { ENABLED.set(value); }
        @Override public void saveEnabled() { ENABLED.save(); }
        @Override public double volume() { return VOLUME.get(); }
        @Override public void volume(double value) { VOLUME.set(value); }
        @Override public void saveVolume() { VOLUME.save(); }
        @Override public VideoDecoderBackend videoDecoderBackend() { return VideoDecoderBackend.parse(VIDEO_DECODER_BACKEND.get()); }
        @Override public void videoDecoderBackend(VideoDecoderBackend value) { VIDEO_DECODER_BACKEND.set(value.configValue()); }
        @Override public String videoDecoderDevice() { return VIDEO_DECODER_DEVICE.get(); }
        @Override public void videoDecoderDevice(String value) { VIDEO_DECODER_DEVICE.set(value == null ? "" : value); }
        @Override public void saveVideoDecoder() { VIDEO_DECODER_BACKEND.save(); VIDEO_DECODER_DEVICE.save(); }
    };

    private CinemarrConfig() {}

    public static CinemarrSettings.ClientValues clientValues() { return CLIENT_VALUES; }
}
