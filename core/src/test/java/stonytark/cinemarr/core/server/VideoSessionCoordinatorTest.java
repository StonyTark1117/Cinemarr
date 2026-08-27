package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertTrue(coordinator.isViewer(coordinator.snapshot("party",1_000).id(),1,viewer));
        assertFalse(coordinator.isViewer(coordinator.snapshot("party",1_000).id(),1,UUID.randomUUID()));
        assertEquals(1, starts.get());
        assertEquals(2, coordinator.snapshot("party", 2_000).televisions().size());
        coordinator.viewerLeft("party", viewer, 2_000);
        coordinator.tick(31_999);
        assertTrue(coordinator.snapshot("party", 31_999).transcoding());
        coordinator.tick(32_000);
        assertFalse(coordinator.snapshot("party", 32_000).transcoding());
        assertEquals(coordinator.snapshot("party",32_000).positionMs(),coordinator.snapshot("party",60_000).positionMs());
        assertEquals(1, stops.get());
    }

    @Test void delayedViewerLeaveAfterLastTelevisionIsHarmless() throws Exception {
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 30_000,
                (session, generation, item, offset) -> () -> {});
        UUID television = UUID.randomUUID();
        UUID viewer = UUID.randomUUID();
        coordinator.tune(television, "party");
        coordinator.viewerEntered("party", viewer);

        coordinator.untune(television);
        coordinator.viewerLeft("party", viewer, 2_000);

        assertFalse(coordinator.sessionNames().contains("party"));
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

    @Test void staleOrFailedReplacementCannotDisplaceCurrentPlayback() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0,
                (session, generation, item, offset) -> {
                    if (starts.incrementAndGet() == 2) throw new java.io.IOException("replacement failed");
                    return stops::incrementAndGet;
                });
        coordinator.tune(UUID.randomUUID(), "party");
        VideoSessionCoordinator.Snapshot playing = coordinator.play("party", movie(), 0, 1_000);
        assertThrows(IllegalStateException.class,
                () -> coordinator.play("party", movie(), 5_000, 2_000, playing.generation() - 1));
        assertThrows(java.io.IOException.class,
                () -> coordinator.play("party", movie(), 5_000, 2_000, playing.generation()));
        VideoSessionCoordinator.Snapshot retained = coordinator.snapshot("party", 2_000);
        assertEquals(playing.generation(), retained.generation());
        assertTrue(retained.transcoding());
        assertEquals(0, stops.get());
    }

    @Test void restoredSessionStaysFrozenUntilRestartedAtItsCheckpoint() throws Exception {
        AtomicInteger starts=new AtomicInteger();
        VideoSessionCoordinator coordinator=new VideoSessionCoordinator(2,30_000,
                (session,generation,item,offset)->{starts.incrementAndGet();assertEquals(12_345,offset);return ()->{};});
        coordinator.tune(UUID.randomUUID(),"saved");
        VideoSessionCoordinator.Snapshot restored=coordinator.restore("saved",movie(),12_345,false,1_000);
        assertFalse(restored.transcoding());assertEquals(12_345,coordinator.snapshot("saved",50_000).positionMs());
        VideoSessionCoordinator.Snapshot restarted=coordinator.restart("saved",50_000,restored.generation());
        assertTrue(restarted.transcoding());assertEquals(1,starts.get());assertEquals(12_345,restarted.positionMs());
    }

    @Test void stopClearsPlaybackButKeepsEveryTunedTelevision() throws Exception{
        VideoSessionCoordinator coordinator=new VideoSessionCoordinator(2,0,(session,generation,item,offset)->()->{});UUID first=UUID.randomUUID(),second=UUID.randomUUID();coordinator.tune(first,"party");coordinator.tune(second,"party");coordinator.play("party",movie(),0,1_000);
        VideoSessionCoordinator.Snapshot stopped=coordinator.stop("party",2_000);assertFalse(stopped.transcoding());assertEquals(null,stopped.item());assertEquals(2,stopped.televisions().size());assertTrue(coordinator.sessionNames().contains("party"));
    }

    private static VideoMediaItem movie() { return new VideoMediaItem(MediaKind.MOVIE, "1", "Movie", "", "PG", 0, 90_000); }
}
