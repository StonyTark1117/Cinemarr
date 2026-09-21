package stonytark.cinemarr.core.video;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stonytark.cinemarr.core.network.Hashing;
import static org.junit.jupiter.api.Assertions.*;

class DisplayFrameEvidenceTest {
    @TempDir Path temporary;

    @Test void retainedOriginalIsOptInContentAddressedAndNeverOverwritten() throws Exception {
        Map<String,String> previous = new HashMap<String,String>();
        String[] keys = {"cinemarr.acceptance.enabled", "cinemarr.acceptance.videoProbe",
                "cinemarr.acceptance.displayProbe", "cinemarr.acceptance.audioControlFile"};
        for (String key : keys) previous.put(key, System.getProperty(key));
        try {
            System.setProperty(keys[0], "true"); System.setProperty(keys[1], "true");
            System.setProperty(keys[2], "false"); System.setProperty(keys[3], temporary.resolve("control").toString());
            byte[] source = {0, 12, 45, (byte)255};
            Path directory = temporary.resolve("control.frames");
            DisplayFrameEvidence.retain(source);
            assertFalse(Files.exists(directory));
            System.setProperty(keys[2], "true");
            DisplayFrameEvidence.retain(source);
            Path file = directory.resolve(Hashing.sha256(source) + ".rgba");
            assertArrayEquals(source, Files.readAllBytes(file));
            DisplayFrameEvidence.retain(source);
            Files.write(file, new byte[]{1, 2, 3, 4});
            assertThrows(UncheckedIOException.class, () -> DisplayFrameEvidence.retain(source));
            assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(file));
            Files.delete(file);
            for (int i=0; i<64; i++) Files.write(directory.resolve("existing-" + i), new byte[]{0});
            assertThrows(UncheckedIOException.class, () -> DisplayFrameEvidence.retain(source));
            assertFalse(Files.exists(file));
        } finally {
            for (String key : keys) {
                if (previous.get(key) == null) System.clearProperty(key);
                else System.setProperty(key, previous.get(key));
            }
        }
    }
}
