package stonytark.cinemarr.core.platform;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.model.RestartMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CinemarrSettingsTest {
    @Test void validatesLoaderProvidedValuesAtTheSharedBoundary() {
        CinemarrSettings.installServer(new CinemarrSettings.ServerValues() {
            @Override public String plexUrl() { return null; }
            @Override public String plexToken() { return " token "; }
            @Override public String musicLibrary() { return null; }
            @Override public RestartMode restartMode() { return null; }
            @Override public boolean pauseWhenEmpty() { return true; }
            @Override public int operatorPermissionLevel() { return 99; }
            @Override public int queueLimit() { return 0; }
            @Override public int audioBitrateKbps() { return 1_000; }
            @Override public long cacheSizeMiB() { return 1; }
            @Override public boolean stationMetadataFallbackEnabled() { return false; }
        });
        assertEquals("http://127.0.0.1:32400", CinemarrSettings.plexUrl());
        assertEquals("", CinemarrSettings.musicLibrary());
        assertEquals(RestartMode.RESTART_TRACK, CinemarrSettings.restartMode());
        assertEquals(4, CinemarrSettings.operatorPermissionLevel());
        assertEquals(1, CinemarrSettings.queueLimit());
        assertEquals(320, CinemarrSettings.audioBitrateKbps());
        assertEquals(64, CinemarrSettings.cacheSizeMiB());
    }
}
