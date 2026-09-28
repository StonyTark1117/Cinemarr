import stonytark.cinemarr.core.client.runtime.*;
import stonytark.cinemarr.core.platform.*;
import org.bytedeco.javacpp.Loader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs with Java-only JARs on the classpath; native archives are supplied as local files. */
public final class ExternalRuntimeSmoke {
    public static void main(String[] args) throws Exception {
        Path game = Paths.get(args[0]);
        Path packages = Paths.get(args[1]);
        boolean legacy = Boolean.parseBoolean(args[2]);
        Path config = Files.createDirectories(game.resolve("config")).resolve("cinemarr-client.toml");
        CinemarrSettings.installClient(CanonicalConfigFiles.loadClient(config));
        String decoderName = "stonytark.cinemarr.client." + (legacy ? "LegacyFfmpegVideoDecoder" : "FfmpegVideoDecoder");
        Object decoder = Class.forName(decoderName).getConstructor().newInstance();
        if (!Loader.getLoadedLibraries().isEmpty()) throw new AssertionError("Decoder construction initialized natives");
        boolean cachedOnly = args.length > 3;
        boolean conflict = cachedOnly && args[3].equals("conflict");
        Properties originalProperties = new Properties(); originalProperties.putAll(Loader.loadProperties());
        if (!cachedOnly) {
            try { NativeRuntime.ensureReady(); throw new AssertionError("Missing runtime accepted"); }
            catch (java.io.IOException expected) {
                if (NativeRuntime.state() != NativeRuntime.State.UNAVAILABLE) throw expected;
            }
            if (!Loader.getLoadedLibraries().isEmpty()) throw new AssertionError("Missing-runtime check initialized natives");
            String platform = LocalRuntimeResolver.currentPlatform();
            Path drop = Files.createDirectories(game.resolve("cinemarr-libraries"));
            for (String name : new String[]{"javacpp-1.5.14-" + platform + ".jar", "ffmpeg-8.1.2-1.5.14-" + platform + ".jar"})
                Files.copy(packages.resolve(name), drop.resolve(name));
            NativeRuntime.rescan();
        }
        if (conflict) {
            LocalRuntimeResolver resolver = new LocalRuntimeResolver(LocalRuntimeResolver.currentPlatform());
            Path library = resolver.resolve(game, "", Collections.<Path>emptyList()).get(0).resolve(System.mapLibraryName("jnijavacpp"));
            Path other = Files.createTempDirectory(game, "another-mod-").resolve(library.getFileName());
            Files.copy(library, other);
            Class<?> type = Class.forName("org.bytedeco.javacpp.presets.javacpp", false, ExternalRuntimeSmoke.class.getClassLoader());
            if (Loader.loadLibrary(type, new java.net.URL[]{other.toUri().toURL()}, "jnijavacpp#") == null)
                throw new AssertionError("Could not establish another mod's JNI registration");
            try { NativeRuntime.ensureReady(); throw new AssertionError("Accepted a different registered library path"); }
            catch (java.io.IOException expected) {
                if (NativeRuntime.state() != NativeRuntime.State.RESTART_REQUIRED || !expected.getMessage().contains("Another mod")) throw expected;
            }
            System.out.println("PASS: conflicting prior JavaCPP registration rejected with restart required");
            return;
        }
        NativeRuntime.ensureReady();
        if (!originalProperties.equals(Loader.loadProperties())) throw new AssertionError("Changed global JavaCPP properties");
        String platform = LocalRuntimeResolver.currentPlatform();
        Path drop = game.resolve("cinemarr-libraries");
        byte[] fixture = DecoderProbeFixture.bytes();
        Object decoded = decoder.getClass().getMethod("decode", byte[].class).invoke(decoder, (Object) fixture);
        int video = ((List<?>) decoded.getClass().getMethod("video").invoke(decoded)).size();
        int audio = ((List<?>) decoded.getClass().getMethod("audio").invoke(decoded)).size();
        if (video < 2 || audio == 0) throw new AssertionError("No decoded audio/video");
        LocalRuntimeResolver resolver = new LocalRuntimeResolver(platform);
        Path cache = resolver.cacheDirectory(game);
        // Cached playback must not depend on the original archives, a Maven cache, or JavaCPP resources.
        try (DirectoryStream<Path> files = Files.newDirectoryStream(drop, "*.jar")) {
            for (Path archive : files) Files.delete(archive);
        }
        List<Path> cached = resolver.resolve(game, "", Collections.<Path>emptyList());
        List<Path> explicit = resolver.resolve(game, cache.toString(), Collections.<Path>emptyList());
        if (!cached.equals(explicit)) throw new AssertionError("Override differs from detected cache");
        try { resolver.resolve(game, "missing-override", Collections.singletonList(cache)); throw new AssertionError("Override fell through"); }
        catch (java.io.IOException expected) { }
        try (java.util.stream.Stream<Path> files = Files.walk(cache)) {
            if (files.filter(Files::isRegularFile).anyMatch(p -> p.getFileName().toString().matches("(?i)ffmpeg(\\.exe)?|ffprobe(\\.exe)?|.*\\.class|.*\\.jar")))
                throw new AssertionError("Import extracted executable or Java payloads");
        }
        Files.write(config, Collections.singletonList("nativeRuntimeDirectory = \"changed-after-initialization\""), StandardCharsets.UTF_8);
        CinemarrSettings.installClient(CanonicalConfigFiles.loadClient(config));
        try { NativeRuntime.ensureReady(); throw new AssertionError("Initialized runtime accepted a replacement"); }
        catch (java.io.IOException expected) {
            if (NativeRuntime.state() != NativeRuntime.State.RESTART_REQUIRED) throw expected;
        }
        Files.write(config, Collections.singletonList("nativeRuntimeDirectory = \"\""), StandardCharsets.UTF_8);
        System.out.println("PASS " + (cachedOnly ? "fresh JVM cache-only, " : "") + (legacy ? "legacy Java 8" : "modern") + (cachedOnly ? ": cached decode; video=" : ": missing runtime, recovery, local import, explicit path; video=") + video + " audio=" + audio);
    }
}
