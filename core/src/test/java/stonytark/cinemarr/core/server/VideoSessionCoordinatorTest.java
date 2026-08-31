package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test void snapshotPositionAndServerEpochShareOneAuthoritativeInstant() throws Exception {
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(2, 0,
                (session, generation, item, offset) -> () -> {});
        coordinator.tune(UUID.randomUUID(), "party");
        coordinator.play("party", movie(), 5_000, 1_000);

        VideoSessionCoordinator.Snapshot snapshot = coordinator.snapshot("party", 8_500);

        assertEquals(8_500, snapshot.serverEpochMs());
        assertEquals(12_500, snapshot.positionMs());
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

    @Test void streamLimitCountsOnlyOpenMediaAndPauseFreesCapacity() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(1, 30_000,
                (session, generation, item, offset) -> { starts.incrementAndGet(); return stops::incrementAndGet; });
        coordinator.tune(UUID.randomUUID(), "first");
        coordinator.tune(UUID.randomUUID(), "second");
        coordinator.tune(UUID.randomUUID(), "third-idle");
        coordinator.play("first", movie(), 0, 1_000);
        assertEquals(1, coordinator.activeStreamCount());
        assertThrows(IllegalStateException.class, () -> coordinator.play("second", movie(), 0, 1_000));
        coordinator.pause("first", 2_000);
        assertEquals(0, coordinator.activeStreamCount());
        assertEquals(1, stops.get());
        coordinator.play("second", movie(), 0, 2_000);
        assertEquals(2, starts.get());
        assertEquals(3, coordinator.sessionCount());
    }

    @Test void fourPlayingSessionsFillDefaultLimitButSharedPausedAndDormantTelevisionsDoNot() throws Exception {
        VideoSessionCoordinator coordinator = new VideoSessionCoordinator(4, 30_000,
                (session, generation, item, offset) -> () -> {});
        UUID first = UUID.randomUUID();
        UUID shared = UUID.randomUUID();
        coordinator.tune(first, "first");
        coordinator.tune(shared, "first");
        for (int index = 2; index <= 4; index++) coordinator.tune(UUID.randomUUID(), "session-" + index);
        coordinator.tune(UUID.randomUUID(), "paused-dormant");
        coordinator.tune(UUID.randomUUID(), "fifth");
        coordinator.play("first", movie(), 0, 1_000);
        for (int index = 2; index <= 4; index++) coordinator.play("session-" + index, movie(), 0, 1_000);
        coordinator.restore("paused-dormant", movie(), 12_345, true, 1_000);
        assertEquals(4, coordinator.activeStreamCount());
        assertEquals(2, coordinator.snapshot("first", 1_000).televisions().size());
        assertFalse(coordinator.snapshot("paused-dormant", 20_000).transcoding());
        assertThrows(IllegalStateException.class, () -> coordinator.play("fifth", movie(), 0, 1_000));
        coordinator.untune(shared);
        assertTrue(coordinator.snapshot("first", 2_000).transcoding());
        coordinator.pause("session-4", 2_000);
        coordinator.play("fifth", movie(), 0, 2_000);
        assertEquals(4, coordinator.activeStreamCount());
    }

    @Test void unloadedSuspensionFreezesPositionAndImmediatelyFreesStreamCapacity() throws Exception {
        AtomicInteger stops=new AtomicInteger();
        VideoSessionCoordinator coordinator=new VideoSessionCoordinator(1,30_000,
                (session,generation,item,offset)->stops::incrementAndGet);
        coordinator.tune(UUID.randomUUID(),"first");coordinator.tune(UUID.randomUUID(),"second");
        coordinator.play("first",movie(),4_000,1_000);
        VideoSessionCoordinator.Snapshot suspended=coordinator.suspend("first",2_000);
        assertFalse(suspended.transcoding());assertFalse(suspended.paused());assertEquals(5_000,suspended.positionMs());
        assertEquals(5_000,coordinator.snapshot("first",50_000).positionMs());assertEquals(0,coordinator.activeStreamCount());assertEquals(1,stops.get());
        assertTrue(coordinator.play("second",movie(),0,2_000).transcoding());
    }

    @Test void removalRacingACompletedMediaStartClosesTheHandleAndLeavesNoSession() throws Exception {
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger stops=new AtomicInteger();
        VideoSessionCoordinator coordinator=new VideoSessionCoordinator(1,0,(session,generation,item,offset)->{started.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.IOException(interrupted);}return stops::incrementAndGet;});
        UUID television=UUID.randomUUID();coordinator.tune(television,"race");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread play=new Thread(()->{try{coordinator.play("race",movie(),0,1_000);}catch(Throwable error){failure.set(error);}});
        Thread remove=new Thread(()->{try{coordinator.untune(television);}catch(Throwable error){failure.set(error);}});
        play.start();assertTrue(started.await(5,TimeUnit.SECONDS));remove.start();release.countDown();play.join(5_000);remove.join(5_000);
        assertFalse(play.isAlive());assertFalse(remove.isAlive());assertEquals(null,failure.get());assertFalse(coordinator.sessionNames().contains("race"));assertEquals(1,stops.get());
    }

    private static VideoMediaItem movie() { return new VideoMediaItem(MediaKind.MOVIE, "1", "Movie", "", "PG", 0, 90_000); }
}
