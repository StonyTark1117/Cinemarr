package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoSessionCoordinatorTest {
    @Test void watchPartyUsesOneTranscodeAndStopsAfterLastViewerGrace() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 30_000,
                (session, generation, item, offset) -> { starts.incrementAndGet(); return stops::incrementAndGet; });
        UUID firstTv = UUID.randomUUID(); UUID secondTv = UUID.randomUUID(); UUID viewer = UUID.randomUUID();
        coordinator.tune(firstTv, "party"); coordinator.tune(secondTv, "party");
        coordinator.viewerEntered("party", viewer);
        coordinator.play("party", movie(), 5_000, 1_000);
        assertEquals(1, starts.get());
        assertEquals(2, coordinator.snapshot("party", 2_000).televisions().size());
        coordinator.viewerLeft("party", viewer, 2_000);
        coordinator.tick(31_999);
        assertTrue(coordinator.snapshot("party", 31_999).transcoding());
        coordinator.tick(32_000);
        assertFalse(coordinator.snapshot("party", 32_000).transcoding());
        assertEquals(1, stops.get());
    }

    @Test void pauseResumeSeekAndGenerationRejectStaleStartsByIdentity() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0,
                (session, generation, item, offset) -> { starts.incrementAndGet(); return () -> {}; });
        coordinator.tune(UUID.randomUUID(), "party");
        VideoSessionCoordinator.Snapshot first = coordinator.play("party", movie(), 0, 1_000);
        coordinator.pause("party", 2_000);
        assertEquals(1_000, coordinator.snapshot("party", 5_000).positionMs());
        coordinator.resume("party", 5_000);
        assertEquals(2_000, coordinator.snapshot("party", 6_000).positionMs());
        coordinator.seek("party", 40_000, 7_000);
        VideoSessionCoordinator.Snapshot second = coordinator.snapshot("party", 7_000);
        assertTrue(second.generation() > first.generation());
        assertEquals(40_000, second.positionMs());
        assertEquals(2, starts.get());
    }

    private static VideoMediaItem movie() { return new VideoMediaItem(MediaKind.MOVIE, "1", "Movie", "", "PG", 0, 90_000); }
}
