package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class VideoPlaybackPublicationTest {
    @Test void contextualFeedbackCannotDescribePausedSuspendedOrStoppedPlaybackAsPlaying() throws Exception {
        try (VideoSessionCoordinator sessions = coordinator(new AtomicInteger())) {
            VideoSessionCoordinator.Snapshot playing = sessions.play("party", movie(), 0, 1_000);
            assertEquals("Playing next queued video", playing.playbackMessage("Playing next queued video"));
            assertEquals("Continuing with next episode", playing.playbackMessage("Continuing with next episode"));
            assertEquals("Playing", playing.playbackMessage());
            sessions.pause("party", 2_000);
            assertEquals("Paused", sessions.snapshot("party", 2_000).playbackMessage("Playing next queued video"));
            sessions.resume("party", 3_000);
            sessions.suspend("party", 4_000);
            assertEquals("Suspended", sessions.snapshot("party", 4_000).playbackMessage("Continuing with next episode"));
            assertEquals("TV is idle", sessions.stop("party", 5_000).playbackMessage("Playing next queued video"));
            assertThrows(IllegalArgumentException.class, () -> playing.playbackMessage(null));
        }
    }

    private static VideoMediaItem movie() {
        return new VideoMediaItem(MediaKind.MOVIE, "9001", "Fixture", "", "PG", 0, 300_000);
    }
    private static VideoSessionCoordinator coordinator(AtomicInteger closes) {
        VideoSessionCoordinator sessions = new VideoSessionCoordinator(2, 30_000,
                (id, generation, item, offset) -> closes::incrementAndGet);
        sessions.tune(UUID.randomUUID(), "party");
        return sessions;
    }

    @Test void initialSuspensionBeforeCompletionRetainsMetadataAndRestartsAtFrozenCursor() throws Exception {
        AtomicInteger closes = new AtomicInteger();
        try (VideoSessionCoordinator sessions = coordinator(closes)) {
            AtomicReference<String> library = new AtomicReference<>();
            VideoSessionCoordinator.Snapshot prepared = sessions.play("party", movie(), 0, 1_000);
            sessions.suspend("party", 1_023);
            assertEquals(1, closes.get());
            assertEquals(0, sessions.activeStreamCount());
            assertFalse(sessions.applyIfCurrent(prepared, () -> fail("Old wire generation must remain invalid")));
            VideoSessionCoordinator.Snapshot current = sessions.applyPlaybackMetadataIfCurrent(prepared, 5_000,
                    () -> library.set("gate_movies"));
            assertNotNull(current);
            assertEquals("gate_movies", library.get());
            assertNotEquals(prepared.generation(), current.generation());
            assertEquals(prepared.playbackGeneration(), current.playbackGeneration());
            assertEquals(23, current.positionMs());
            assertEquals("Suspended", current.playbackMessage());
            assertFalse(current.transcoding());
            assertFalse(current.paused());
            VideoSessionCoordinator.Snapshot restarted = sessions.restart("party", 6_000, current.generation());
            assertEquals(23, restarted.positionMs());
            assertEquals("Playing", restarted.playbackMessage());
            assertNull(sessions.applyPlaybackMetadataIfCurrent(prepared, 6_000, () -> fail("Replaced playback")));
        }
    }

    @Test void viewerGraceSuspensionBeforeCompletionAlsoPreservesMetadata() throws Exception {
        try (VideoSessionCoordinator sessions = coordinator(new AtomicInteger())) {
            VideoSessionCoordinator.Snapshot prepared = sessions.play("party", movie(), 0, 1_000);
            sessions.tick(31_000);
            VideoSessionCoordinator.Snapshot current = sessions.applyPlaybackMetadataIfCurrent(prepared, 50_000, () -> {});
            assertNotNull(current);
            assertEquals(30_000, current.positionMs());
            assertEquals("Suspended", current.playbackMessage());
        }
    }

    @Test void lateCompletionCannotUndoPauseOrPausedCursorSeek() throws Exception {
        try (VideoSessionCoordinator sessions = coordinator(new AtomicInteger())) {
            VideoSessionCoordinator.Snapshot prepared = sessions.play("party", movie(), 0, 1_000);
            sessions.pause("party", 2_000);
            VideoSessionCoordinator.Snapshot paused = sessions.snapshot("party", 2_000);
            sessions.seek("party", 42_000, 3_000, paused.generation());
            VideoSessionCoordinator.Snapshot current = sessions.applyPlaybackMetadataIfCurrent(prepared, 50_000, () -> {});
            assertNotNull(current);
            assertEquals(42_000, current.positionMs());
            assertTrue(current.paused());
            assertFalse(current.transcoding());
            assertEquals("Paused", current.playbackMessage());
        }
    }

    @Test void pausedStreamReplacementRejectsEarlierOptionsEvenForTheSameItem() throws Exception {
        try (VideoSessionCoordinator sessions = coordinator(new AtomicInteger())) {
            VideoSessionCoordinator.Snapshot prepared = sessions.play("party", movie(), 0, 1_000);
            sessions.pause("party", 2_000);
            VideoSessionCoordinator.Snapshot paused = sessions.snapshot("party", 2_000);
            VideoSessionCoordinator.Snapshot streams = sessions.reconfigure("party", 3_000, paused.generation());
            assertNotEquals(prepared.playbackGeneration(), streams.playbackGeneration());
            assertNull(sessions.applyPlaybackMetadataIfCurrent(prepared, 4_000, () -> fail("Old stream options")));
            assertEquals("Paused", sessions.applyPlaybackMetadataIfCurrent(streams, 4_000, () -> {}).playbackMessage());
            VideoSessionCoordinator.Snapshot newer = sessions.reconfigure("party", 5_000, streams.generation());
            assertNull(sessions.applyPlaybackMetadataIfCurrent(streams, 6_000, () -> fail("Superseded paused options")));
            assertNotNull(sessions.applyPlaybackMetadataIfCurrent(newer, 6_000, () -> {}));
        }
    }

    @Test void playingSeekAndStreamReplacementRejectEarlierCompletions() throws Exception {
        try (VideoSessionCoordinator sessions = coordinator(new AtomicInteger())) {
            VideoSessionCoordinator.Snapshot first = sessions.play("party", movie(), 0, 1_000);
            VideoSessionCoordinator.Snapshot seek = sessions.seek("party", 30_000, 2_000, first.generation());
            assertNull(sessions.applyPlaybackMetadataIfCurrent(first, 3_000, () -> fail("Old seek")));
            VideoSessionCoordinator.Snapshot streams = sessions.reconfigure("party", 4_000, seek.generation());
            assertNull(sessions.applyPlaybackMetadataIfCurrent(seek, 5_000, () -> fail("Old stream options")));
            sessions.suspend("party", 4_023);
            assertEquals(32_023, sessions.applyPlaybackMetadataIfCurrent(streams, 5_000, () -> {}).positionMs());
        }
    }

    @Test void stopRestoreAndRepeatedRestoreRejectOldMetadata() throws Exception {
        try (VideoSessionCoordinator sessions = coordinator(new AtomicInteger())) {
            VideoSessionCoordinator.Snapshot prepared = sessions.play("party", movie(), 0, 1_000);
            VideoSessionCoordinator.Snapshot stopped = sessions.stop("party", 2_000);
            assertEquals("TV is idle", stopped.playbackMessage());
            assertNull(sessions.applyPlaybackMetadataIfCurrent(prepared, 3_000, () -> fail("Stopped")));
            assertNull(sessions.applyPlaybackMetadataIfCurrent(stopped, 3_000, () -> fail("No playback")));
            VideoSessionCoordinator.Snapshot restored = sessions.restore("party", movie(), 10_000, true, 4_000);
            sessions.restore("party", movie(), 20_000, true, 5_000);
            assertNull(sessions.applyPlaybackMetadataIfCurrent(restored, 6_000, () -> fail("Replaced restore")));
        }
    }

    @Test void removalNameReuseAndCloseRejectOldMetadata() throws Exception {
        VideoSessionCoordinator sessions = new VideoSessionCoordinator(1, 0, (id, generation, item, offset) -> () -> {});
        UUID tv = UUID.randomUUID();
        sessions.tune(tv, "party");
        VideoSessionCoordinator.Snapshot prepared = sessions.play("party", movie(), 0, 1_000);
        sessions.untune(tv);
        assertNull(sessions.applyPlaybackMetadataIfCurrent(prepared, 2_000, () -> fail("Removed")));
        sessions.tune(tv, "party");
        VideoSessionCoordinator.Snapshot replacement = sessions.play("party", movie(), 0, 3_000);
        assertNull(sessions.applyPlaybackMetadataIfCurrent(prepared, 4_000, () -> fail("Reused name")));
        sessions.close();
        assertNull(sessions.applyPlaybackMetadataIfCurrent(replacement, 5_000, () -> fail("Closed")));
        assertNull(sessions.applyPlaybackMetadataIfCurrent(null, 5_000, () -> fail("Null")));
    }
}
