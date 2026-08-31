package stonytark.cinemarr.core.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import stonytark.cinemarr.core.screen.QuickTvBuildMode;
import stonytark.cinemarr.core.screen.QuickTvPreset;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoServerConfigTest {
    @TempDir Path temporary;

    @Test void writesVideoOnlyDefaults() throws Exception {
        Path file=temporary.resolve("world/serverconfig/cinemarr-server.toml");
        CanonicalConfigFiles.ServerConfig config=CanonicalConfigFiles.loadServer(file);
        String saved=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        assertEquals(QuickTvBuildMode.BOUNDED,config.quickTvBuildMode());
        assertEquals(3_840,config.maximumVideoWidth());
        assertEquals(2_160,config.maximumVideoHeight());
        assertEquals(20_000,config.maximumVideoBitrateKbps());
        assertFalse(saved.contains("musicLibrary"));
        assertFalse(saved.contains("audioBitrateKbps"));
        assertFalse(saved.contains("station"));
        assertFalse(saved.contains("restartMode"));
    }

    @Test void acceptsLegacyKeysOnceButDropsThemFromCanonicalOutput() throws Exception {
        Path file=temporary.resolve("cinemarr-server.toml");
        Files.write(file,("musicLibrary=\"Music\"\nrestartMode=\"resume-position\"\n"
                +"audioBitrateKbps=320\ncacheSizeMiB=4096\nstationMetadataFallbackEnabled=true\n"
                +"queueLimit=17\n").getBytes(StandardCharsets.UTF_8));
        CanonicalConfigFiles.ServerConfig config=CanonicalConfigFiles.loadServer(file);
        String saved=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
        assertEquals(17,config.queueLimit());
        assertFalse(saved.contains("musicLibrary"));
        assertFalse(saved.contains("audioBitrateKbps"));
        assertFalse(saved.contains("stationMetadataFallbackEnabled"));
    }

    @Test void validatesLiteralQuickTvAndTranscodeCaps() throws Exception {
        Path file=temporary.resolve("literal.toml");
        Files.write(file,("quickTvBuildMode=\"literal\"\nmaximumVideoWidth=7680\n"
                +"maximumVideoHeight=4320\nmaximumVideoBitrateKbps=80000\n"
                +"quickTv8KEnabled=false\n").getBytes(StandardCharsets.UTF_8));
        CanonicalConfigFiles.ServerConfig config=CanonicalConfigFiles.loadServer(file);
        assertEquals(QuickTvBuildMode.LITERAL,config.quickTvBuildMode());
        assertEquals(7_680,config.maximumVideoWidth());
        assertEquals(4_320,config.maximumVideoHeight());
        assertEquals(80_000,config.maximumVideoBitrateKbps());
        assertFalse(config.quickTvPresetEnabled(QuickTvPreset.P8K));
    }

    @Test void invalidNewLimitsDoNotRewriteTheInput() throws Exception {
        String[] invalid={"maximumVideoWidth=1\n","maximumVideoHeight=4321\n","maximumVideoBitrateKbps=127\n"};
        for(int index=0;index<invalid.length;index++){
            Path file=temporary.resolve("invalid-"+index+".toml");byte[] original=invalid[index].getBytes(StandardCharsets.UTF_8);Files.write(file,original);
            assertThrows(CanonicalConfigFiles.ConfigValidationException.class,()->CanonicalConfigFiles.loadServer(file));
            assertArrayEquals(original,Files.readAllBytes(file));
        }
    }
}
