package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import static org.junit.jupiter.api.Assertions.*;

class AtomicScreenshotFileTest {
    @TempDir Path directory;

    @Test void overlappingEncodersCannotLeaveTheLongerImagesTail() throws Exception {
        AtomicScreenshotFile longer = AtomicScreenshotFile.create(directory.toFile(), "capture.png");
        AtomicScreenshotFile shorter = AtomicScreenshotFile.create(directory.toFile(), "capture.png");
        Path screenshots = directory.resolve("screenshots");
        Path output = screenshots.resolve("capture.png");
        byte[] previous = {9, 9, 9};
        byte[] large = {1, 2, 3, 4, 5, 6};
        byte[] small = {7, 8};
        Files.write(output, previous);
        // Match NativeImage's overlapping WRITE/CREATE/TRUNCATE_EXISTING channels.
        try (SeekableByteChannel a = Files.newByteChannel(screenshots.resolve(longer.fileName()),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             SeekableByteChannel b = Files.newByteChannel(screenshots.resolve(shorter.fileName()),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            a.write(ByteBuffer.wrap(large));
            b.write(ByteBuffer.wrap(small));
            assertArrayEquals(previous, Files.readAllBytes(output));
        }
        assertEquals("Saved capture.png", longer.publish("Saved " + longer.fileName()));
        assertArrayEquals(large, Files.readAllBytes(output));
        assertEquals("Saved capture.png", shorter.publish("Saved " + shorter.fileName()));
        assertArrayEquals(small, Files.readAllBytes(output));
        assertFalse(Files.exists(screenshots.resolve(longer.fileName())));
        assertFalse(Files.exists(screenshots.resolve(shorter.fileName())));
    }

    @Test void encoderFailurePreservesPreviousCaptureAndFailedStagingEvidence() throws Exception {
        AtomicScreenshotFile capture = AtomicScreenshotFile.create(directory.toFile(), "capture.png");
        Path output = directory.resolve("screenshots/capture.png");
        Files.write(output, new byte[]{4, 5});
        assertTrue(capture.publish("Encoder failed").startsWith("Acceptance screenshot publication failed:"));
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(output));
        assertTrue(Files.exists(directory.resolve("screenshots").resolve(capture.fileName())));
    }

    @Test void failedAtomicReplacementDoesNotRemoveItsEncodedInput() throws Exception {
        AtomicScreenshotFile capture = AtomicScreenshotFile.create(directory.toFile(), "capture.png");
        Path output = directory.resolve("screenshots/capture.png");
        Files.createDirectory(output);
        Files.write(output.resolve("keep"), new byte[]{1});
        Path encoded = directory.resolve("screenshots").resolve(capture.fileName());
        Files.write(encoded, new byte[]{2, 3});
        assertTrue(capture.publish("Saved").startsWith("Acceptance screenshot publication failed:"));
        assertArrayEquals(new byte[]{2, 3}, Files.readAllBytes(encoded));
        assertTrue(Files.exists(output.resolve("keep")));
    }
}
