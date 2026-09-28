package stonytark.cinemarr.core.client.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class LocalRuntimeResolverTest {
    @TempDir Path temp;

    @Test void unsupportedPlatformRejectedBeforeLookingAtFiles() {
        assertThrows(IOException.class, () -> new LocalRuntimeResolver("windows-arm64"));
        assertThrows(IOException.class, () -> new LocalRuntimeResolver("../../anything"));
    }

    @Test void missingRuntimeGivesManualSetupInstructions() throws Exception {
        IOException error = assertThrows(IOException.class, () -> new LocalRuntimeResolver("linux-x86_64")
                .resolve(temp, "", Collections.<Path>emptyList()));
        assertTrue(error.getMessage().contains("nativeRuntimeDirectory"));
        assertTrue(error.getMessage().contains("JavaCPP 1.5.14"));
        assertFalse(Files.exists(temp.resolve("cinemarr-libraries/.runtime")));
    }

    @Test void explicitMissingDirectoryDoesNotFallThroughToOtherLocations() throws Exception {
        Path detected = Files.createDirectory(temp.resolve("detected"));
        IOException error = assertThrows(IOException.class, () -> new LocalRuntimeResolver("linux-x86_64")
                .resolve(temp, "override", Collections.singletonList(detected)));
        assertTrue(error.getMessage().contains(temp.resolve("override").toString()));
        assertFalse(error.getMessage().contains("detected"));
    }

    @Test void rejectsUnverifiedArchiveBeforeImportingAnything() throws Exception {
        Path supplied = Files.createDirectory(temp.resolve("supplied"));
        Files.write(supplied.resolve("javacpp-1.5.14-linux-x86_64.jar"), new byte[]{'P','K',3,4});
        IOException error = assertThrows(IOException.class, () -> new LocalRuntimeResolver("linux-x86_64")
                .resolve(temp, "supplied", Collections.<Path>emptyList()));
        assertTrue(error.getMessage().contains("wrong-size"));
        assertFalse(Files.exists(temp.resolve("cinemarr-libraries/.runtime")));
    }

    @Test void configRetainsWindowsPathsAndRuntimeModeWhenOtherSettingsAreSaved() throws Exception {
        Path config = Files.createDirectories(temp.resolve("config")).resolve("cinemarr-client.toml");
        Files.write(config, ("nativeRuntimeMode = \"disabled\"\nnativeRuntimeDirectory = \"C:\\\\Media Libraries\\\\cinemarr\"\n")
                .getBytes(StandardCharsets.UTF_8));
        CanonicalConfigFiles.ClientConfig values = CanonicalConfigFiles.loadClient(config);
        assertEquals("C:\\Media Libraries\\cinemarr", values.nativeRuntimeDirectory());
        assertEquals(temp, values.gameDirectory());
        values.volume(0.5); values.saveVolume();
        CanonicalConfigFiles.ClientConfig reloaded = CanonicalConfigFiles.loadClient(config);
        assertEquals("disabled", reloaded.nativeRuntimeMode());
        assertEquals(values.nativeRuntimeDirectory(), reloaded.nativeRuntimeDirectory());
    }

    @Test void existingClientConfigGetsLocalOnlyDefaults() throws Exception {
        Path config = temp.resolve("cinemarr-client.toml");
        Files.write(config, "enabled = false\nvolume = 0.25\n".getBytes(StandardCharsets.UTF_8));
        CanonicalConfigFiles.ClientConfig values = CanonicalConfigFiles.loadClient(config);
        assertEquals("local-only", values.nativeRuntimeMode());
        assertEquals("", values.nativeRuntimeDirectory());
        assertFalse(values.enabled());
        assertEquals(0.25, values.volume());
    }
}
