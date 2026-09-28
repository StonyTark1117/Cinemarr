package stonytark.cinemarr.core.client.runtime;

import org.bytedeco.javacpp.ClassProperties;
import org.bytedeco.javacpp.Loader;
import stonytark.cinemarr.core.platform.CinemarrSettings;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

/** Client-only entry point; call on a decoder worker before any FFmpeg class initialization. */
public final class NativeRuntime {
    public enum State { NOT_CHECKED, CHECKING, READY, UNAVAILABLE, RESTART_REQUIRED, DISABLED }
    private static volatile State state = State.NOT_CHECKED;
    private static volatile String message = "Local decoder libraries have not been checked";
    private static boolean nativeLoadAttempted;
    private static String loadedConfiguration;
    private static String failedConfiguration;

    public static State state() { return state; }
    public static String message() { return message; }

    public static String statusLabel() {
        switch (state) {
            case READY: return "Decoder libraries ready";
            case CHECKING: return "Checking local decoder libraries";
            case UNAVAILABLE: return "Install decoder libraries; see installation instructions";
            case RESTART_REQUIRED: return "Decoder libraries require a Minecraft restart";
            case DISABLED: return "Local video decoding disabled";
            default: return "Decoder libraries will be checked on playback";
        }
    }

    public static void openLibraryDirectory() {
        Thread worker = new Thread(() -> {
            try {
                Path folder = CinemarrSettings.gameDirectory().resolve(CinemarrSettings.nativeRuntimeDirectory().isEmpty()
                        ? "cinemarr-libraries" : CinemarrSettings.nativeRuntimeDirectory()).toAbsolutePath().normalize();
                Files.createDirectories(folder);
                if (!java.awt.Desktop.isDesktopSupported()) throw new IOException("Open this folder manually: " + folder);
                java.awt.Desktop.getDesktop().open(folder.toFile());
            } catch (IOException | RuntimeException ex) { message = ex.getMessage(); }
        }, "Cinemarr open local library folder");
        worker.setDaemon(true);
        worker.start();
    }

    /** A failed local lookup can be rescanned. A failed JNI initialization requires a restart. */
    public static void requestRescan() {
        Thread worker = new Thread(NativeRuntime::rescan, "Cinemarr retry local libraries");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void rescan() {
        if (!nativeLoadAttempted) { failedConfiguration = null; state = State.NOT_CHECKED; message = "Local libraries will be checked on next playback"; }
        else if (state != State.READY) { state = State.RESTART_REQUIRED; }
    }

    public static void ensureReady() throws IOException { ensureReady(CinemarrSettings.videoDecoderBackend()); }

    public static synchronized void ensureReady(stonytark.cinemarr.core.platform.VideoDecoderBackend backend) throws IOException {
        String mode = CinemarrSettings.nativeRuntimeMode();
        String override = CinemarrSettings.nativeRuntimeDirectory();
        Path game = CinemarrSettings.gameDirectory().toAbsolutePath().normalize();
        String configuration = mode + "\n" + game + "\n" + override;
        if (mode.equals("disabled")) { state = State.DISABLED; message = "Local video decoding is disabled in nativeRuntimeMode"; throw new IOException(message); }
        if (!mode.equals("local-only")) throw unavailable("nativeRuntimeMode must be local-only or disabled", configuration);
        if (nativeLoadAttempted) {
            if (state == State.READY && configuration.equals(loadedConfiguration)) return;
            state = State.RESTART_REQUIRED;
            message = "Restart Minecraft before changing or retrying initialized native libraries";
            throw new IOException(message);
        }
        if (configuration.equals(failedConfiguration)) throw new IOException(message);
        state = State.CHECKING;
        message = "Checking user-installed decoder libraries";
        final List<Path> directories;
        final LocalRuntimeResolver resolver;
        try {
            resolver = new LocalRuntimeResolver(LocalRuntimeResolver.currentPlatform());
            directories = resolver.resolve(game, override, LocalRuntimeResolver.installedLocations());
        } catch (IOException | RuntimeException ex) {
            throw unavailable(ex.getMessage(), configuration);
        }
        try {
            nativeLoadAttempted = true;
            preloadHostGraphicsLibraries(backend);
            loadLibraries(directories, resolver.verifiedFiles(directories));
            loadedConfiguration = configuration;
            state = State.READY;
            message = "Local decoder libraries ready";
        } catch (Exception | LinkageError ex) {
            state = State.RESTART_REQUIRED;
            message = "Unable to initialize local decoder libraries; check host dependencies and restart Minecraft. " + ex.getMessage();
            throw new IOException(message, ex);
        }
    }

    private static IOException unavailable(String reason, String configuration) {
        state = State.UNAVAILABLE; message = reason; failedConfiguration = configuration;
        return new IOException(reason);
    }

    private static void loadLibraries(List<Path> directories, Set<Path> verifiedFiles) throws Exception {
        // Use per-call properties and explicit URLs. Never mutate JavaCPP's global properties or classpath.
        Properties properties = new Properties(); properties.putAll(Loader.loadProperties());
        StringBuilder paths = new StringBuilder();
        for (Path directory : directories) {
            if (paths.length() > 0) paths.append(File.pathSeparator);
            paths.append(directory);
        }
        properties.setProperty("platform.preloadpath", paths.toString());
        String[] classes = {"org.bytedeco.javacpp.presets.javacpp", "org.bytedeco.ffmpeg.global.avutil",
                "org.bytedeco.ffmpeg.global.swresample", "org.bytedeco.ffmpeg.global.avcodec",
                "org.bytedeco.ffmpeg.global.avformat", "org.bytedeco.ffmpeg.global.swscale",
                "org.bytedeco.ffmpeg.global.avfilter", "org.bytedeco.ffmpeg.global.avdevice"};
        for (String name : classes) {
            Class<?> type = Class.forName(name, false, NativeRuntime.class.getClassLoader());
            ClassProperties cp = Loader.loadProperties(type, properties, true);
            List<String> libraries = new ArrayList<String>();
            libraries.addAll(cp.get("platform.preload")); libraries.addAll(cp.get("platform.link"));
            libraries.addAll(cp.get("platform.library"));
            for (String library : libraries) {
                URL selected = null;
                for (URL url : Loader.findLibrary(type, cp, library, true)) {
                    if (!url.getProtocol().equals("file")) continue;
                    Path candidate = Paths.get(url.toURI()).toRealPath();
                    if (verifiedFiles.contains(candidate)) { selected = url; break; }
                }
                if (selected == null) {
                    if (cp.get("platform.library").contains(library)) throw new IOException("Missing JNI library: " + library);
                    continue; // Optional system/GPU dependencies are not arbitrary fallback runtime candidates.
                }
                String loaded = Loader.getLoadedLibraries().get(library);
                if (loaded != null && !Paths.get(loaded).toRealPath().equals(Paths.get(selected.toURI()).toRealPath()))
                    throw new IOException("Another mod already loaded a different " + library + " library");
                // JavaCPP's trailing # disables System.loadLibrary fallback while retaining the normal cache key.
                // If a pinned file cannot load, do not substitute an unverified system library.
                String result = Loader.loadLibrary(type, new URL[]{selected}, library + "#");
                if (result == null && cp.get("platform.library").contains(library))
                    throw new IOException("Cannot load pinned JNI library " + library + "; check host dependencies");
            }
        }
        // Initialize only after the entire matching set is registered with JavaCPP.
        for (String name : classes) Class.forName(name, true, NativeRuntime.class.getClassLoader());
    }

    private static void preloadHostGraphicsLibraries(stonytark.cinemarr.core.platform.VideoDecoderBackend backend) {
        if (backend != stonytark.cinemarr.core.platform.VideoDecoderBackend.AUTO
                && backend != stonytark.cinemarr.core.platform.VideoDecoderBackend.VAAPI
                && backend != stonytark.cinemarr.core.platform.VideoDecoderBackend.QSV) return;
        if (!System.getProperty("os.name", "").equalsIgnoreCase("linux")) return;
        // Preserve the existing hardware path: the host libva ABI must precede FFmpeg's portable libraries.
        try {
            for (String library : new String[]{"va", "va-drm"}) {
                boolean loaded = false;
                for (String root : new String[]{"/usr/lib", "/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu"}) {
                    Path path = Paths.get(root, "lib" + library + ".so.2");
                    if (Files.isRegularFile(path)) { System.load(path.toString()); loaded = true; break; }
                }
                if (!loaded) System.loadLibrary(library);
            }
        } catch (UnsatisfiedLinkError ignored) { /* Existing hardware probe reports a software fallback. */ }
    }

    private NativeRuntime() { }
}
