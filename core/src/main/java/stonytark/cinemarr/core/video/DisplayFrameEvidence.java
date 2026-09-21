package stonytark.cinemarr.core.video;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.network.Hashing;

/** Opt-in original decoded frames for independent framebuffer acceptance. */
public final class DisplayFrameEvidence {
    private DisplayFrameEvidence() {}

    public static void retain(byte[] source) {
        if (!ProtocolLimits.displayProbeEnabled() || ProtocolLimits.audioControlFile().isEmpty()) return;
        if (source == null || source.length == 0 || source.length > 64 * 1024 * 1024) throw new IllegalArgumentException("Invalid evidence frame");
        Path directory = Paths.get(ProtocolLimits.audioControlFile() + ".frames");
        String hash = Hashing.sha256(source);
        Path destination = directory.resolve(hash + ".rgba");
        try {
            Files.createDirectories(directory);
            if (Files.isSymbolicLink(directory)) throw new IOException("Frame evidence directory is a symlink");
            if (Files.exists(destination)) {
                if (Files.isSymbolicLink(destination) || Files.size(destination) != source.length
                        || !hash.equals(Hashing.sha256(Files.readAllBytes(destination))))
                    throw new IOException("Existing frame evidence differs");
                return;
            }
            long count = 0, bytes = 0;
            try (Stream<Path> files = Files.list(directory)) {
                java.util.Iterator<Path> iterator = files.iterator();
                while (iterator.hasNext()) { Path file = iterator.next(); count++; bytes += Files.size(file); }
            }
            if (count >= 64 || bytes + source.length > 128L * 1024 * 1024)
                throw new IOException("Frame evidence budget exhausted");
            Files.write(destination, source, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to retain original acceptance frame", failure);
        }
    }
}
