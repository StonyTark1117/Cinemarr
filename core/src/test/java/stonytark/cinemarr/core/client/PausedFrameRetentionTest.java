package stonytark.cinemarr.core.client;

import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;
import static org.junit.jupiter.api.Assertions.*;

class PausedFrameRetentionTest {
    private final UUID session = UUID.randomUUID();

    private VideoPackets.SessionState state(UUID id, long generation, String item,
                                             boolean paused, VideoPackets.SessionStatus status) {
        return new VideoPackets.SessionState(UUID.randomUUID(), 0, id, generation, status,
                item == null ? null : new VideoMediaItem(MediaKind.MOVIE, item, "Movie", "", "", 0, 300_000),
                30_000, 300_000, paused, PresentationMode.FIT, 16, 9, new byte[0],
                ScreenFacing.NORTH, 0, 0, 0, Collections.emptyList(), -1, -1, 1_000, true, "");
    }

    @Test void preservesOnlyNewerPausedRevisionsOfTheSameProgram() {
        VideoPackets.SessionState next = state(session, 2, "movie", true, VideoPackets.SessionStatus.PAUSED);
        assertTrue(PausedFrameRetention.permits(session, 1, "movie", next));
        assertFalse(PausedFrameRetention.permits(session, 2, "movie", next));
        assertFalse(PausedFrameRetention.permits(session, 3, "movie", next));
        assertFalse(PausedFrameRetention.permits(session, -1, "movie", next));
        assertFalse(PausedFrameRetention.permits(UUID.randomUUID(), 1, "movie", next));
        assertFalse(PausedFrameRetention.permits(session, 1, "other", next));
        assertFalse(PausedFrameRetention.permits(session, 1, "", next));
        assertFalse(PausedFrameRetention.permits(session, 1, null, next));
        assertFalse(PausedFrameRetention.permits(null, 1, "movie", next));
        assertFalse(PausedFrameRetention.permits(session, 1, "movie", null));
    }

    @Test void neverRetainsAcrossResumeStopErrorOrMissingProgram() {
        for (VideoPackets.SessionStatus status : VideoPackets.SessionStatus.values()) {
            assertEquals(status == VideoPackets.SessionStatus.PAUSED,
                    PausedFrameRetention.permits(session, 1, "movie", state(session, 2, "movie", true, status)));
            assertFalse(PausedFrameRetention.permits(session, 1, "movie", state(session, 2, "movie", false, status)));
        }
        assertFalse(PausedFrameRetention.permits(session, 1, "movie", state(session, 2, null, true, VideoPackets.SessionStatus.PAUSED)));
        assertFalse(PausedFrameRetention.permits(session, 1, "movie", state(null, 2, "movie", true, VideoPackets.SessionStatus.PAUSED)));
    }
}
