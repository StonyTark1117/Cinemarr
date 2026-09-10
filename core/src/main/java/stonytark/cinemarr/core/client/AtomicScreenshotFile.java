package stonytark.cinemarr.core.client;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Isolates asynchronous acceptance screenshot writers until their files close. */
public final class AtomicScreenshotFile {
    private final Path temporary;
    private final Path destination;

    private AtomicScreenshotFile(Path temporary, Path destination) {
        this.temporary = temporary;
        this.destination = destination;
    }

    public static AtomicScreenshotFile create(File gameDirectory, String name) {
        try {
            Path directory = new File(gameDirectory, "screenshots").toPath();
            Files.createDirectories(directory);
            Path destination = directory.resolve(name);
            if (!destination.getParent().equals(directory)) {
                throw new IllegalArgumentException("Screenshot name must be a file name");
            }
            return new AtomicScreenshotFile(
                    Files.createTempFile(directory, ".cinemarr-capture-", ".png"), destination);
        } catch (IOException error) {
            throw new UncheckedIOException("Acceptance screenshot staging failed", error);
        }
    }

    public String fileName() { return temporary.getFileName().toString(); }

    /** Called by the encoder only after its file is closed. Failed staging files remain evidence. */
    public synchronized String publish(String encoderResult) {
        try {
            if (Files.size(temporary) == 0) throw new IOException("Encoder left an empty screenshot");
            // Same-directory atomic replacement prevents both interleaved writes and partial reads.
            // Unsupported atomic moves fail closed; the gate retains and rejects the staging file.
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            return encoderResult.replace(fileName(), destination.getFileName().toString());
        } catch (IOException error) {
            return "Acceptance screenshot publication failed: " + error.getClass().getSimpleName();
        }
    }
}
