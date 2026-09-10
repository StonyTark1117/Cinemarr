package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.protocol.VideoPackets;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static stonytark.cinemarr.core.server.VideoHealthPolicy.Decision.*;

class VideoHealthPolicyTest {
    private static final UUID SESSION = UUID.randomUUID();

    private static VideoPackets.ClientHealth report(long buffered, long drift) {
        return new VideoPackets.ClientHealth(SESSION, 0, "BUFFERING", 0, 0, 0, buffered, drift);
    }

    @Test void acceptsCurrentTelemetryAtInclusiveBounds() {
        for (long buffered : new long[] {0, 60_000}) {
            for (long drift : new long[] {-30_000, 0, 30_000}) {
                assertEquals(ACCEPT, VideoHealthPolicy.classify(report(buffered, drift), () -> true));
                assertEquals(IGNORE_STALE, VideoHealthPolicy.classify(report(buffered, drift), () -> false));
            }
        }
        assertEquals(ACCEPT, VideoHealthPolicy.classify(new VideoPackets.ClientHealth(SESSION, 1,
                "PLAYING", Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0), () -> true));
    }

    @Test void malformedTelemetryIsRejectedBeforeLookingUpItsSession() {
        VideoPackets.ClientHealth[] malformed = {
                null,
                new VideoPackets.ClientHealth(SESSION, 0, "unknown", 0, 0, 0, 0, 0),
                new VideoPackets.ClientHealth(SESSION, 0, "PLAYING", -1, 0, 0, 0, 0),
                new VideoPackets.ClientHealth(SESSION, 0, "PLAYING", 0, -1, 0, 0, 0),
                new VideoPackets.ClientHealth(SESSION, 0, "PLAYING", 0, 0, -1, 0, 0),
                report(-1, 0), report(60_001, 0), report(Long.MAX_VALUE, 0),
                report(0, -30_001), report(0, 30_001),
                report(0, Long.MIN_VALUE), report(0, Long.MAX_VALUE)
        };
        for (VideoPackets.ClientHealth value : malformed) {
            assertEquals(INVALID, VideoHealthPolicy.classify(value, () -> {
                fail("malformed report must not reach the coordinator");
                return false;
            }));
        }
    }

    @Test void restoredStopRestartAndViewerDepartureDiscardLateReports() throws Exception {
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 30_000,
                (session, generation, item, offset) -> () -> {});
        UUID viewer = UUID.randomUUID();
        coordinator.tune(UUID.randomUUID(), "party");
        coordinator.viewerEntered("party", viewer);
        VideoMediaItem item = new VideoMediaItem(MediaKind.MOVIE, "movie", "Movie", "", "", 0, 120_000);
        VideoSessionCoordinator.Snapshot restored = coordinator.restore("party", item, 50_000, true, 1_000);
        VideoPackets.ClientHealth old = new VideoPackets.ClientHealth(restored.id(), restored.generation(),
                "BUFFERING", 0, 0, 0, 0, 0);
        assertEquals(ACCEPT, VideoHealthPolicy.classify(old,
                () -> coordinator.isViewer(old.sessionId(), old.generation(), viewer)));
        coordinator.stop("party", 2_000);
        assertEquals(IGNORE_STALE, VideoHealthPolicy.classify(old,
                () -> coordinator.isViewer(old.sessionId(), old.generation(), viewer)));
        VideoSessionCoordinator.Snapshot restarted = coordinator.play("party", item, 0, 3_000);
        assertEquals(IGNORE_STALE, VideoHealthPolicy.classify(old,
                () -> coordinator.isViewer(old.sessionId(), old.generation(), viewer)));
        VideoPackets.ClientHealth current = new VideoPackets.ClientHealth(restarted.id(), restarted.generation(),
                "PLAYING", 0, 0, 0, 8_000, 0);
        assertEquals(ACCEPT, VideoHealthPolicy.classify(current,
                () -> coordinator.isViewer(current.sessionId(), current.generation(), viewer)));
        coordinator.viewerLeft("party", viewer, 4_000);
        assertEquals(IGNORE_STALE, VideoHealthPolicy.classify(current,
                () -> coordinator.isViewer(current.sessionId(), current.generation(), viewer)));
        coordinator.close();
    }
}
