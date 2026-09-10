package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.QueuedVideo;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.core.video.PresentationMode;
import stonytark.cinemarr.network.VideoPayloads;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinemarrVideoClientStateTest {

    @Test void manifestRefreshPreservesInFlightAndDeferredTransfers() throws Exception {
        for (boolean inFlight : new boolean[] { true, false }) {
            VideoPackets.SessionState session = resumeState(true);
            CinemarrVideoClientState.StreamState stream = new CinemarrVideoClientState.StreamState(new CinemarrVideoClientState.StreamKey(session.sessionId(), session.generation()));
            stream.session(session);
            VideoPackets.SegmentManifest original = recoveryManifest(session, 0, 200_000);
            setTransferField(stream, "manifest", original);
            setTransferField(stream, "lastCompletedSegment", 9);
            setTransferField(stream, "requestedSegment", inFlight ? 10 : -1);
            setTransferField(stream, "deferredSegment", inFlight ? -1 : 10);
            setTransferField(stream, "requestId", 12L);
            setTransferField(stream, "currentWindowStart", 8);
            setTransferField(stream, "totalChunks", 26);
            seedCompletedSegment(stream, session);
            stream.manifest(recoveryManifest(session, 8, 200_000));
            assertEquals(1, ((java.util.Queue<?>) transferField(stream, "ready")).size(), "a same-generation refresh must preserve queued media");
            assertTrue(transferField(stream, "manifest") == original, "keep the descriptors owning the current transfer");
            assertEquals(inFlight ? 10 : -1, transferField(stream, "requestedSegment"));
            assertEquals(inFlight ? -1 : 10, transferField(stream, "deferredSegment"));
            assertEquals(12L, transferField(stream, "requestId"));
            assertEquals(8, transferField(stream, "currentWindowStart"));
            assertEquals(26, transferField(stream, "totalChunks"));
        }
    }

    @Test void manifestRecoveryContinuesAfterCompletedPrefetchInsteadOfReplayingIt() throws Exception {
        VideoPackets.SessionState session = resumeState(true);
        CinemarrVideoClientState.StreamState stream = new CinemarrVideoClientState.StreamState(new CinemarrVideoClientState.StreamKey(session.sessionId(), session.generation()));
        stream.session(session);
        setTransferField(stream, "lastCompletedSegment", 9);
        VideoPackets.SegmentManifest manifest = recoveryManifest(session, 0, 0);
        seedCompletedSegment(stream, session);
        assertEquals(10, stream.resumeSegment(manifest, 100_000), "completed prefetch is ahead of the paused clock and must not be requested again");
        stream.manifest(manifest);
        assertEquals(10, transferField(stream, "deferredSegment"));
        assertEquals(1, ((java.util.Queue<?>) transferField(stream, "ready")).size());
        setTransferField(stream, "lastCompletedSegment", 15);
        assertEquals(16, stream.resumeSegment(manifest, 100_000), "recovery at a page boundary requests the next page");
    }

    private static VideoPackets.SegmentManifest recoveryManifest(VideoPackets.SessionState session, int first, long offset) {
        java.util.List<VideoPackets.SegmentDescriptor> segments = new java.util.ArrayList<>();
        for (int i = first; i < first + 16; i++) segments.add(new VideoPackets.SegmentDescriptor(i, offset + i * 8_000L, 8_000, true, 0, ""));
        return new VideoPackets.SegmentManifest(session.sessionId(), session.generation(), 256, 144, "mpegts", "h264", "aac", 1_000_000, true, segments);
    }

    @SuppressWarnings("unchecked")
    private static void seedCompletedSegment(Object stream, VideoPackets.SessionState session) throws Exception {
        byte[] bytes = new byte[] { 42 };
        String sha = stonytark.cinemarr.core.network.Hashing.sha256(bytes);
        stonytark.cinemarr.core.client.VideoSegmentAssembler assembler = new stonytark.cinemarr.core.client.VideoSegmentAssembler();
        assembler.begin(session.sessionId(), session.generation(), 11, 9, 1, sha, 72_000, true);
        Object complete = assembler.accept(session.sessionId(), session.generation(), 11, 9, 0, 1, sha, 72_000, true, bytes).get();
        ((java.util.Queue<Object>) transferField(stream, "ready")).add(complete);
        setTransferField(stream, "readyBytes", 1L);
    }

    private static Object transferField(Object stream, String name) throws Exception {
        java.lang.reflect.Field field = stream.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(stream);
    }
    private static void setTransferField(Object stream, String name, Object value) throws Exception {
        java.lang.reflect.Field field = stream.getClass().getDeclaredField(name); field.setAccessible(true); field.set(stream, value);
    }

    @Test void timeoutRecoverySelectsCurrentTimelineInsteadOfRewindingTheManifest() throws Exception {
        CinemarrClientState clockState = CinemarrClientState.INSTANCE;
        java.lang.reflect.Field clockField = CinemarrClientState.class.getDeclaredField("clock");
        clockField.setAccessible(true);
        stonytark.cinemarr.core.client.ClockSynchronizer clock =
                (stonytark.cinemarr.core.client.ClockSynchronizer) clockField.get(clockState);
        clock.reset();
        clock.accept(90_000, 100_000, 90_000);
        java.util.List<VideoPackets.SegmentDescriptor> segments = new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++)
            segments.add(new VideoPackets.SegmentDescriptor(index, index * 8_000L, 8_000, true, 0, ""));
        VideoPackets.SessionState playing = resumeState(false);
        CinemarrVideoClientState.StreamState stream = new CinemarrVideoClientState.StreamState(
                new CinemarrVideoClientState.StreamKey(playing.sessionId(), playing.generation()));
        stream.session(playing);
        VideoPackets.SegmentManifest manifest = new VideoPackets.SegmentManifest(playing.sessionId(),
                playing.generation(), 256, 144, "mpegts", "h264", "aac", 60_000, true, segments);
        try {
            assertEquals(3, stream.resumeSegment(manifest, 115_000),
                    "25 seconds elapsed on the synchronized clock: resume at 30 seconds, not the original 5 seconds");
            assertEquals(7, stream.resumeSegment(manifest, 155_000),
                    "an old manifest must resume at its last available keyframe before fetching the next page");
            stream.session(resumeState(true));
            assertEquals(0, stream.resumeSegment(manifest, 115_000), "paused replacement retains its cursor");
        } finally { clock.reset(); }
    }

    private static VideoPackets.SessionState resumeState(boolean paused) {
        return new VideoPackets.SessionState(UUID.randomUUID(), 1, UUID.randomUUID(), 2,
                paused ? VideoPackets.SessionStatus.PAUSED : VideoPackets.SessionStatus.PLAYING,
                new VideoMediaItem(MediaKind.MOVIE, "1", "Movie", "", "PG", 0, 60_000),
                5_000, 60_000, paused, PresentationMode.FIT, 4, 4, new byte[]{(byte)255,(byte)255},
                ScreenFacing.NORTH, 0, 0, 0, java.util.List.of(), -1, -1, 100_000, true, "");
    }

    @Test void segmentPrefetchStaysNearPlaybackInsteadOfExhaustingTheServerLeadWindow() {
        assertTrue(CinemarrVideoClientState.StreamState.withinPrefetchLead(30_000,10_000),
                "real Plex HLS transfer and decode jitter needs more than one segment of runway");
        assertFalse(CinemarrVideoClientState.StreamState.withinPrefetchLead(30_001,10_000));
    }

    @Test void independentTvStreamsRetainTheSharedQueueAcrossReplacementAndRemoval() {
        CinemarrVideoClientState state = CinemarrVideoClientState.INSTANCE;
        state.reset();
        UUID party = UUID.randomUUID(), firstTv = UUID.randomUUID(), secondTv = UUID.randomUUID();
        UUID firstStream = UUID.randomUUID(), secondStream = UUID.randomUUID();
        try {
            state.accept(new VideoPayloads.SessionState(session(1, firstTv, firstStream, 4, true).withTimeline(party, 12)));
            state.accept(new VideoPayloads.SessionState(session(2, secondTv, secondStream, 7, true).withTimeline(party, 12)));
            state.accept(new VideoPayloads.SessionQueue(new VideoPackets.SessionQueue(party, 12,
                    java.util.List.of(new QueuedVideo("movies", new VideoMediaItem(MediaKind.MOVIE, "2", "Queued", "", "PG", 0, 60_000))))));
            assertEquals("Queued", state.queue(1).getFirst().item().title());
            assertEquals("Queued", state.queue(2).getFirst().item().title());
            assertEquals(2, state.streamStates().size());
            var sibling = state.stream(new CinemarrVideoClientState.StreamKey(new stonytark.cinemarr.core.protocol.VideoStreamIdentity(party, 12, secondStream, 7)));
            state.accept(new VideoPayloads.SessionState(session(1, firstTv, firstStream, 5, true).withTimeline(party, 12)));
            assertEquals(2, state.streamStates().size());
            assertNotNull(sibling);
            assertTrue(sibling == state.stream(new CinemarrVideoClientState.StreamKey(new stonytark.cinemarr.core.protocol.VideoStreamIdentity(party, 12, secondStream, 7))));
            assertEquals("Queued", state.queue(1).getFirst().item().title());
            state.accept(new VideoPayloads.TelevisionRemoved(new VideoPackets.TelevisionRemoved(1)));
            assertEquals("Queued", state.queue(2).getFirst().item().title());
            assertEquals(1, state.streamStates().size());
            // A paused or capacity-waiting TV retains its shared queue without owning media.
            state.accept(new VideoPayloads.SessionState(session(2, secondTv, new UUID(0, 0), 0, false).withTimeline(party, 12)));
            assertEquals("Queued", state.queue(2).getFirst().item().title());
            assertEquals(0, state.streamStates().size());
            state.accept(new VideoPayloads.TelevisionRemoved(new VideoPackets.TelevisionRemoved(2)));
            state.accept(new VideoPayloads.SessionState(session(3, UUID.randomUUID(), UUID.randomUUID(), 1, true).withTimeline(party, 12)));
            assertTrue(state.queue(3).isEmpty(), "the last removal must evict the cached party queue");
        } finally { state.reset(); }
    }

    @Test void sharedTimelineRevisionRetiresTheOldPipelineEvenIfStreamFieldsMatch() {
        CinemarrVideoClientState state = CinemarrVideoClientState.INSTANCE; state.reset();
        UUID party = UUID.randomUUID(), tv = UUID.randomUUID(), stream = UUID.randomUUID();
        VideoPackets.SessionState before = session(1, tv, stream, 7, true).withTimeline(party, 41);
        VideoPackets.SessionState after = session(1, tv, stream, 7, true).withTimeline(party, 42);
        try {
            state.accept(new VideoPayloads.SessionState(before));
            var oldKey = new CinemarrVideoClientState.StreamKey(before.identity());
            assertNotNull(state.stream(oldKey));
            state.accept(new VideoPayloads.SessionState(after));
            assertEquals(1, state.streamStates().size());
            assertTrue(state.stream(oldKey) == null);
            assertNotNull(state.stream(new CinemarrVideoClientState.StreamKey(after.identity())));
            assertEquals(0, state.televisionsForStream(oldKey).size());
        } finally { state.reset(); }
    }

    private static VideoPackets.SessionState session(long controller,UUID tv,UUID session,long generation,boolean playing){
        return new VideoPackets.SessionState(tv,controller,session,generation,playing?VideoPackets.SessionStatus.PLAYING:VideoPackets.SessionStatus.IDLE,
                playing?new VideoMediaItem(MediaKind.MOVIE,"1","Movie","","PG",0,60_000):null,0,60_000,false,PresentationMode.FIT,
                4,4,new byte[]{(byte)255,(byte)255},ScreenFacing.NORTH,0,0,0,java.util.List.of(),-1,-1,System.currentTimeMillis(),true,"");
    }
}
