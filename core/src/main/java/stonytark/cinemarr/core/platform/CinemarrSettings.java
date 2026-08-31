package stonytark.cinemarr.core.platform;

import stonytark.cinemarr.core.screen.QuickTvBuildMode;
import stonytark.cinemarr.core.screen.QuickTvPreset;
import stonytark.cinemarr.core.server.TelevisionLifecycle;

/** Loader-neutral validated configuration access installed by each platform adapter. */
public final class CinemarrSettings {
    public interface ServerValues {
        String plexUrl();
        String plexToken();
        int operatorPermissionLevel();
        int queueLimit();
        default int minimumScreenPixels() { return 4; }
        default int maximumScreenPixels() { return 65_536; }
        default int maximumScreenDimension() { return 2_048; }
        default int maximumConcurrentStreams() { return maximumActiveTelevisions(); }
        /** Legacy config bridge retained for old loader adapters and existing files. */
        @Deprecated default int maximumActiveTelevisions() { return 4; }
        default int maximumScreensPerOwner() { return 8; }
        default boolean allowIrregularScreens() { return false; }
        default int inactiveSessionGraceSeconds() { return 30; }
        default boolean quickTvKitsEnabled() { return true; }
        default boolean quickTvPresetEnabled(QuickTvPreset preset) { return true; }
        default QuickTvBuildMode quickTvBuildMode() { return QuickTvBuildMode.BOUNDED; }
        default int maximumVideoWidth() { return 3_840; }
        default int maximumVideoHeight() { return 2_160; }
        default int maximumVideoBitrateKbps() { return 20_000; }
    }

    public interface ClientValues {
        boolean enabled();
        void enabled(boolean value);
        void saveEnabled();
        double volume();
        void volume(double value);
        void saveVolume();
        default VideoDecoderBackend videoDecoderBackend() { return VideoDecoderBackend.SOFTWARE; }
        default void videoDecoderBackend(VideoDecoderBackend value) {}
        default String videoDecoderDevice() { return ""; }
        default void videoDecoderDevice(String value) {}
        default void saveVideoDecoder() {}
    }

    private static volatile ServerValues server = new DefaultServerValues();
    private static volatile ClientValues client = new DefaultClientValues();

    public static void installServer(ServerValues values) {
        if (values == null) throw new IllegalArgumentException("values");
        server = values;
        TelevisionLifecycle.reset(null);
    }

    public static void installClient(ClientValues values) {
        if (values == null) throw new IllegalArgumentException("values");
        client = values;
    }

    public static String plexUrl() { return safe(server.plexUrl(), "http://127.0.0.1:32400"); }
    public static String plexToken() {
        String environment = System.getenv("CINEMARR_PLEX_TOKEN");
        return environment == null || environment.trim().isEmpty() ? safe(server.plexToken(), "").trim() : environment.trim();
    }
    public static int operatorPermissionLevel() { return clamp(server.operatorPermissionLevel(), 0, 4); }
    public static int queueLimit() { return clamp(server.queueLimit(), 1, 500); }
    public static int minimumScreenPixels() { return clamp(server.minimumScreenPixels(), 1, 65_536); }
    public static int maximumScreenPixels() { return clamp(server.maximumScreenPixels(), minimumScreenPixels(), 65_536); }
    public static int maximumScreenDimension() { return clamp(server.maximumScreenDimension(), 1, 2_048); }
    public static int maximumConcurrentStreams() { return clamp(server.maximumConcurrentStreams(), 1, 64); }
    /** @deprecated use {@link #maximumConcurrentStreams()}. */
    @Deprecated public static int maximumActiveTelevisions() { return maximumConcurrentStreams(); }
    public static int maximumScreensPerOwner() { return clamp(server.maximumScreensPerOwner(), 1, 64); }
    public static boolean allowIrregularScreens() { return server.allowIrregularScreens(); }
    public static int inactiveSessionGraceSeconds() { return clamp(server.inactiveSessionGraceSeconds(), 0, 600); }
    public static boolean quickTvKitsEnabled() { return server.quickTvKitsEnabled(); }
    public static boolean quickTvPresetEnabled(QuickTvPreset preset) {
        return preset != null && quickTvKitsEnabled() && server.quickTvPresetEnabled(preset);
    }
    public static QuickTvBuildMode quickTvBuildMode() {
        QuickTvBuildMode value = server.quickTvBuildMode();
        return value == null ? QuickTvBuildMode.BOUNDED : value;
    }
    public static int maximumVideoWidth() { return clamp(server.maximumVideoWidth(), 2, 7_680); }
    public static int maximumVideoHeight() { return clamp(server.maximumVideoHeight(), 2, 4_320); }
    public static int maximumVideoBitrateKbps() { return clamp(server.maximumVideoBitrateKbps(), 128, 100_000); }
    public static boolean enabled() { return client.enabled(); }
    public static void enabled(boolean value) { client.enabled(value); }
    public static void saveEnabled() { client.saveEnabled(); }
    public static double volume() { return clamp(client.volume(), 0.0, 1.0); }
    public static void volume(double value) { client.volume(clamp(value, 0.0, 1.0)); }
    public static void saveVolume() { client.saveVolume(); }
    public static VideoDecoderBackend videoDecoderBackend() {
        VideoDecoderBackend value = client.videoDecoderBackend();
        return value == null ? VideoDecoderBackend.SOFTWARE : value;
    }
    public static void videoDecoderBackend(VideoDecoderBackend value) {
        client.videoDecoderBackend(value == null ? VideoDecoderBackend.SOFTWARE : value);
    }
    public static String videoDecoderDevice() { return safe(client.videoDecoderDevice(), "").trim(); }
    public static void videoDecoderDevice(String value) { client.videoDecoderDevice(safe(value, "").trim()); }
    public static void saveVideoDecoder() { client.saveVideoDecoder(); }

    private static String safe(String value, String fallback) { return value == null ? fallback : value; }
    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static long clamp(long value, long minimum, long maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static double clamp(double value, double minimum, double maximum) { return Math.max(minimum, Math.min(maximum, value)); }

    private static final class DefaultServerValues implements ServerValues {
        @Override public String plexUrl() { return "http://127.0.0.1:32400"; }
        @Override public String plexToken() { return ""; }
        @Override public int operatorPermissionLevel() { return 2; }
        @Override public int queueLimit() { return 500; }
    }

    private static final class DefaultClientValues implements ClientValues {
        private boolean enabled = true;
        private double volume = 1.0;
        private VideoDecoderBackend videoDecoderBackend = VideoDecoderBackend.SOFTWARE;
        private String videoDecoderDevice = "";
        @Override public boolean enabled() { return enabled; }
        @Override public void enabled(boolean value) { enabled = value; }
        @Override public void saveEnabled() {}
        @Override public double volume() { return volume; }
        @Override public void volume(double value) { volume = value; }
        @Override public void saveVolume() {}
        @Override public VideoDecoderBackend videoDecoderBackend() { return videoDecoderBackend; }
        @Override public void videoDecoderBackend(VideoDecoderBackend value) { videoDecoderBackend = value; }
        @Override public String videoDecoderDevice() { return videoDecoderDevice; }
        @Override public void videoDecoderDevice(String value) { videoDecoderDevice = value; }
    }

    private CinemarrSettings() {}
}
