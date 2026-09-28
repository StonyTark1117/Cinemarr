package stonytark.cinemarr.core.client.runtime;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Resolves only local, release-pinned files. This class has no network or native-loading code. */
public final class LocalRuntimeResolver {
    public static final String RUNTIME_VERSION = "ffmpeg-8.1.2-javacpp-1.5.14";
    private static final String[] ARTIFACTS = {"javacpp", "ffmpeg"};
    private final String platform;
    private final Properties manifest;

    public LocalRuntimeResolver(String platform) throws IOException {
        if (!Arrays.asList("linux-x86_64", "linux-arm64", "windows-x86_64").contains(platform))
            throw new IOException("Unsupported decoder platform: " + platform);
        this.platform = platform;
        manifest = new Properties();
        try (InputStream in = LocalRuntimeResolver.class.getResourceAsStream(
                "/stonytark/cinemarr/runtime/" + platform + ".properties")) {
            if (in == null) throw new IOException("Cinemarr runtime manifest is missing");
            manifest.load(in);
        }
    }

    public static String currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String cpu = arch.equals("amd64") || arch.equals("x86_64") ? "x86_64"
                : arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : arch;
        return (os.startsWith("windows") ? "windows" : os.equals("linux") ? "linux" : os) + "-" + cpu;
    }

    public Path cacheDirectory(Path gameDirectory) {
        return gameDirectory.resolve("cinemarr-libraries/.runtime/" + RUNTIME_VERSION + "/" + platform);
    }

    /** Explicit override wins even when invalid; automatic candidates are complete installations only. */
    public synchronized List<Path> resolve(Path gameDirectory, String override, List<Path> installedLocations)
            throws IOException {
        gameDirectory = gameDirectory.toAbsolutePath().normalize();
        if (override != null && !override.trim().isEmpty()) {
            Path explicit = gameDirectory.resolve(override.trim()).normalize();
            return resolveCandidate(explicit, gameDirectory);
        }
        List<Path> candidates = new ArrayList<Path>();
        candidates.add(cacheDirectory(gameDirectory));
        candidates.add(gameDirectory.resolve("cinemarr-libraries"));
        candidates.addAll(installedLocations);
        IOException last = null;
        for (Path candidate : new LinkedHashSet<Path>(candidates)) {
            if (!Files.isDirectory(candidate)) continue;
            try { return resolveCandidate(candidate, gameDirectory); }
            catch (IOException ex) { last = ex; }
        }
        throw new IOException("Install matching JavaCPP 1.5.14 and FFmpeg 8.1.2-1.5.14 libraries in "
                + gameDirectory.resolve("cinemarr-libraries") + " or set nativeRuntimeDirectory. "
                + (last == null ? "No compatible local runtime found." : last.getMessage()));
    }

    public static List<Path> installedLocations() {
        List<Path> roots = new ArrayList<Path>();
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) {
            roots.add(Paths.get(home, ".javacpp", "cache"));
            roots.add(Paths.get(home, ".m2", "repository"));
        }
        String configuredCache = System.getProperty("org.bytedeco.javacpp.cachedir", "");
        if (!configuredCache.isEmpty()) roots.add(Paths.get(configuredCache));
        for (String part : System.getProperty("java.library.path", "").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            if (!part.isEmpty()) roots.add(Paths.get(part));
        return roots;
    }

    private List<Path> resolveCandidate(Path root, Path gameDirectory) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("Library directory does not exist: " + root);
        try { return validateExtracted(root); }
        catch (IOException missingExtracted) {
            Map<String, Path> archives = new LinkedHashMap<String, Path>();
            for (String artifact : ARTIFACTS) {
                String[] pin = manifest.getProperty("archive." + artifact).split(",");
                Path archive = root.resolve(pin[0]);
                if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
                    String version = artifact.equals("javacpp") ? "1.5.14" : "8.1.2-1.5.14";
                    archive = root.resolve("org/bytedeco/" + artifact + "/" + version + "/" + pin[0]);
                }
                if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) throw missingExtracted;
                verify(archive, Long.parseLong(pin[1]), pin[2]);
                archives.put(artifact, archive);
            }
            return importArchives(archives, gameDirectory);
        }
    }

    private List<Path> validateExtracted(Path root) throws IOException {
        List<Path> directories = new ArrayList<Path>();
        for (String artifact : ARTIFACTS) {
            String jarName = manifest.getProperty("archive." + artifact).split(",")[0];
            List<Path> options = Arrays.asList(root.resolve(artifact), root,
                    root.resolve("org/bytedeco/" + artifact + "/" + platform),
                    root.resolve(jarName + "/org/bytedeco/" + artifact + "/" + platform));
            Path found = null;
            for (Path option : options) {
                if (!Files.isDirectory(option)) continue;
                try {
                    for (String key : keys(artifact)) {
                        String[] pin = manifest.getProperty(key).split(",");
                        verify(option.resolve(baseName(pin[0])), Long.parseLong(pin[1]), pin[2]);
                    }
                    found = option.toRealPath(); break;
                } catch (IOException ignored) { /* try the next documented layout */ }
            }
            if (found == null) throw new IOException("Missing or mismatched " + artifact + " libraries for " + platform + " in " + root);
            directories.add(found);
        }
        return Collections.unmodifiableList(directories);
    }

    private List<Path> importArchives(Map<String, Path> archives, Path gameDirectory) throws IOException {
        Path cache = cacheDirectory(gameDirectory);
        Path parent = cache.getParent();
        Files.createDirectories(parent);
        Path lockPath = parent.resolve(platform + ".lock");
        if (Files.isSymbolicLink(lockPath)) throw new IOException("Runtime lock must not be a symbolic link");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = tryLock(channel)) {
            if (Files.exists(cache)) return validateExtracted(cache);
            Path staging = Files.createTempDirectory(parent, platform + ".staging-");
            try {
                for (String artifact : ARTIFACTS) {
                    Path directory = Files.createDirectory(staging.resolve(artifact));
                    try (ZipFile zip = new ZipFile(archives.get(artifact).toFile())) {
                        for (String key : keys(artifact)) {
                            String[] pin = manifest.getProperty(key).split(",");
                            ZipEntry entry = zip.getEntry(pin[0]);
                            long size = Long.parseLong(pin[1]);
                            if (entry == null || entry.isDirectory() || entry.getSize() != size)
                                throw new IOException("Missing or invalid local archive entry: " + pin[0]);
                            Path destination = directory.resolve(baseName(pin[0]));
                            try (InputStream in = zip.getInputStream(entry);
                                 OutputStream out = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                                byte[] buffer = new byte[32768]; long count = 0; int n;
                                while ((n = in.read(buffer)) != -1) {
                                    count += n;
                                    if (count > size) throw new IOException("Runtime entry exceeds pinned size");
                                    out.write(buffer, 0, n);
                                }
                            }
                            verify(destination, size, pin[2]);
                        }
                    }
                }
                validateExtracted(staging);
                try { Files.move(staging, cache, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException ex) { Files.move(staging, cache); }
                return validateExtracted(cache);
            } finally { deleteStaging(staging); }
        }
    }

    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock != null) return lock;
        } catch (java.nio.channels.OverlappingFileLockException ignored) { }
        throw new IOException("Another client is importing libraries; rescan when it finishes");
    }

    public Set<Path> verifiedFiles(List<Path> directories) {
        Set<Path> files = new HashSet<Path>();
        for (int i = 0; i < ARTIFACTS.length; i++) {
            for (String key : keys(ARTIFACTS[i]))
                files.add(directories.get(i).resolve(baseName(manifest.getProperty(key).split(",")[0])));
        }
        return Collections.unmodifiableSet(files);
    }

    private List<String> keys(String artifact) {
        List<String> keys = new ArrayList<String>();
        for (String key : manifest.stringPropertyNames()) if (key.startsWith("file." + artifact + ".")) keys.add(key);
        Collections.sort(keys);
        return keys;
    }

    private static String baseName(String resource) { return resource.substring(resource.lastIndexOf('/') + 1); }

    private static void verify(Path path, long size, String hash) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != size)
            throw new IOException("Missing or wrong-size runtime file: " + path.getFileName());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[32768]; int n; long count = 0;
                while ((n = in.read(buffer)) != -1) {
                    count += n;
                    if (count > size) throw new IOException("Runtime file changed while reading");
                    digest.update(buffer, 0, n);
                }
                if (count != size) throw new IOException("Runtime file changed while reading");
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", value & 255));
            if (!actual.toString().equals(hash)) throw new IOException("Runtime checksum mismatch: " + path.getFileName());
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void deleteStaging(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) deleteStaging(child);
                else Files.delete(child);
            }
        }
        Files.delete(root);
    }
}
