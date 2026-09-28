import stonytark.cinemarr.core.client.runtime.LocalRuntimeResolver;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Archive/import checks for all packaged platforms; this does not execute foreign native code. */
public final class ExternalRuntimeImportSmoke {
    public static void main(String[] args) throws Exception {
        Path packages = Paths.get(args[0]);
        Path work = Paths.get(args[1]);
        for (String platform : new String[]{"linux-x86_64", "linux-arm64", "windows-x86_64"}) {
            LocalRuntimeResolver resolver = new LocalRuntimeResolver(platform);
            Path game = Files.createDirectories(work.resolve(platform));
            Path cache = resolver.cacheDirectory(game);
            Path parent = Files.createDirectories(cache.getParent());
            Path interrupted = Files.createDirectory(parent.resolve(platform + ".staging-interrupted"));
            Files.write(interrupted.resolve("partial"), new byte[]{1});
            // A live import lock must cause an actionable retry, never a partial ready result.
            try (FileChannel channel = FileChannel.open(parent.resolve(platform + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                try { resolver.resolve(game, packages.toString(), Collections.<Path>emptyList()); throw new AssertionError("Ignored live import lock"); }
                catch (IOException expected) { if (!expected.getMessage().contains("Another client")) throw expected; }
            }
            List<Path> directories = resolver.resolve(game, packages.toString(), Collections.<Path>emptyList());
            if (!Files.exists(interrupted.resolve("partial"))) throw new AssertionError("Deleted unrelated staging directory");
            if (!directories.equals(resolver.resolve(game, "", Collections.<Path>emptyList()))) throw new AssertionError("Cache not detected");
            if (!directories.equals(resolver.resolve(game, cache.toString(), Collections.<Path>emptyList()))) throw new AssertionError("Explicit extracted layout rejected");
            // A changed library must invalidate the complete runtime before JNI can run.
            Path nativeFile = resolver.verifiedFiles(directories).iterator().next();
            byte[] original = Files.readAllBytes(nativeFile);
            byte[] changed = original.clone(); changed[changed.length - 1] ^= 1;
            Files.write(nativeFile, changed);
            try { resolver.resolve(game, cache.toString(), Collections.singletonList(packages)); throw new AssertionError("Corrupt override accepted or substituted"); }
            catch (IOException expected) { }
            Files.write(nativeFile, original);
            resolver.resolve(game, cache.toString(), Collections.<Path>emptyList());
            System.out.println("PASS " + platform + " archive import, lock contention, interrupted staging, explicit path, cache and checksum rejection (no native execution)");
        }
    }
}
