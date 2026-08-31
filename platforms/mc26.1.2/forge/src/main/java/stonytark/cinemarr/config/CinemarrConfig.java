package stonytark.cinemarr.config;

import net.minecraftforge.common.ForgeConfigSpec;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

public final class CinemarrConfig {
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.ConfigValue<String> PLEX_URL = SERVER_BUILDER
            .comment("Plex Media Server base URL, for example http://127.0.0.1:32400")
            .define("plexUrl", "http://127.0.0.1:32400");
    public static final ForgeConfigSpec.ConfigValue<String> PLEX_TOKEN = SERVER_BUILDER
            .comment("Plex token. Prefer the CINEMARR_PLEX_TOKEN environment variable.").define("plexToken", "");
    public static final ForgeConfigSpec.IntValue OP_PERMISSION = SERVER_BUILDER.defineInRange("operatorPermissionLevel", 2, 0, 4);
    public static final ForgeConfigSpec.IntValue QUEUE_LIMIT = SERVER_BUILDER.defineInRange("queueLimit", 500, 1, 500);
    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.BooleanValue ENABLED = CLIENT_BUILDER.define("enabled", true);
    public static final ForgeConfigSpec.DoubleValue VOLUME = CLIENT_BUILDER.defineInRange("volume", 1.0, 0.0, 1.0);
    public static final ForgeConfigSpec.ConfigValue<String> VIDEO_DECODER_BACKEND =
            CLIENT_BUILDER.define("videoDecoderBackend", "software");
    public static final ForgeConfigSpec.ConfigValue<String> VIDEO_DECODER_DEVICE =
            CLIENT_BUILDER.define("videoDecoderDevice", "");
    public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private static final CinemarrSettings.ServerValues SERVER_VALUES = new CinemarrSettings.ServerValues() {
        @Override public String plexUrl() { return PLEX_URL.get(); }
        @Override public String plexToken() { return PLEX_TOKEN.get(); }
        @Override public int operatorPermissionLevel() { return OP_PERMISSION.get(); }
        @Override public int queueLimit() { return QUEUE_LIMIT.get(); }
    };
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

    public static CinemarrSettings.ServerValues serverValues() { return SERVER_VALUES; }
    public static CinemarrSettings.ClientValues clientValues() { return CLIENT_VALUES; }
    private CinemarrConfig() {}
}
