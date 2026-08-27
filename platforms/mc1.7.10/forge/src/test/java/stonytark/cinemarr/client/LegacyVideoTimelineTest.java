package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyVideoTimelineTest {
    @Test
    void advancesAgainstServerEpochAndFreezesWhilePaused() {
        VideoPackets.SessionState playing = state(false, 5_000, 100_000);
        VideoPackets.SessionState paused = state(true, 5_000, 100_000);
        assertEquals(6_250, LegacyVideoPlayback.authoritativePositionMs(playing, 101_250));
        assertEquals(5_000, LegacyVideoPlayback.authoritativePositionMs(paused, 101_250));
    }

    @Test
    void positionalAudioClampsToNearestPointOnLargeScreen() {
        VideoPackets.SessionState north = state(false, 0, 0);
        assertArrayEquals(new float[] { 11.0F, 22.0F, 30.0F }, LegacyVideoAudio.nearestScreenPoint(north, 11, 22, 100));
        assertArrayEquals(new float[] { 10.0F, 20.0F, 30.0F }, LegacyVideoAudio.nearestScreenPoint(north, -100, -100, 100));
    }

    private static VideoPackets.SessionState state(boolean paused, long position, long epoch) {
        VideoMediaItem item = new VideoMediaItem(MediaKind.MOVIE, "42", "Fixture", "", "PG", 0, 60_000, "", 0);
        return new VideoPackets.SessionState(UUID.fromString("12345678-1234-5678-9abc-def012345678"), 1L,
                UUID.fromString("87654321-4321-8765-cba9-876543210fed"), 2L, paused ? VideoPackets.SessionStatus.PAUSED : VideoPackets.SessionStatus.PLAYING,
                item, position, 60_000, paused, PresentationMode.FIT, 4, 3, new byte[] { 0x7f },
                ScreenFacing.NORTH, 30, 10, 20, Collections.emptyList(), -1, -1, epoch, true, "");
    }
}
