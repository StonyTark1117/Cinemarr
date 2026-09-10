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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyVideoTimelineTest {

    @Test void manifestRefreshPreservesInFlightAndDeferredTransfers() throws Exception {
        for (boolean inFlight : new boolean[] { true, false }) {
            VideoPackets.SessionState session = state(true, 5_000, 100_000);
            LegacyVideoClientState.StreamState stream = new LegacyVideoClientState.StreamState(new LegacyVideoClientState.StreamKey(session.sessionId(), session.generation()));
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
        VideoPackets.SessionState session = state(true, 5_000, 100_000);
        LegacyVideoClientState.StreamState stream = new LegacyVideoClientState.StreamState(new LegacyVideoClientState.StreamKey(session.sessionId(), session.generation()));
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
        LegacyClientState clockState = LegacyClientState.INSTANCE;
        java.lang.reflect.Field clockField = LegacyClientState.class.getDeclaredField("clock");
        clockField.setAccessible(true);
        stonytark.cinemarr.core.client.ClockSynchronizer clock =
                (stonytark.cinemarr.core.client.ClockSynchronizer) clockField.get(clockState);
        clock.reset();
        clock.accept(90_000, 100_000, 90_000);
        java.util.List<VideoPackets.SegmentDescriptor> segments = new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++)
            segments.add(new VideoPackets.SegmentDescriptor(index, index * 8_000L, 8_000, true, 0, ""));
        VideoPackets.SessionState playing = state(false, 5_000, 100_000);
        LegacyVideoClientState.StreamState stream = new LegacyVideoClientState.StreamState(
                new LegacyVideoClientState.StreamKey(playing.sessionId(), playing.generation()));
        stream.session(playing);
        VideoPackets.SegmentManifest manifest = new VideoPackets.SegmentManifest(playing.sessionId(),
                playing.generation(), 256, 144, "mpegts", "h264", "aac", 60_000, true, segments);
        try {
            assertEquals(3, stream.resumeSegment(manifest, 115_000),
                    "25 seconds elapsed on the synchronized clock: resume at 30 seconds, not the original 5 seconds");
            assertEquals(7, stream.resumeSegment(manifest, 155_000),
                    "an old manifest must resume at its last available keyframe before fetching the next page");
            stream.session(state(true, 5_000, 100_000));
            assertEquals(0, stream.resumeSegment(manifest, 115_000), "paused replacement retains its cursor");
        } finally { clock.reset(); }
    }

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

    @Test
    void physicalSourceDelayIsRemovedFromTheSharedMediaSchedule() {
        long targetUs = 1_300_000L;
        long scheduledUs = 4_000_000L;
        long prerollUs = 2_000_000L;
        long backendPlayedUs = 300_000L;
        long extraUs = LegacyVideoAudio.additionalSilenceUs(targetUs, scheduledUs, prerollUs, backendPlayedUs);
        assertEquals(1_000_000L, extraUs);
        assertEquals(scheduledUs - targetUs,
                prerollUs - backendPlayedUs + extraUs,
                "remaining queued silence must land program audio on the shared media timestamp");
    }

    @Test
    void scheduledPhysicalBoundaryAnchorsTheReliableAudioTimeline() {
        assertEquals(5_000_000L, LegacyVideoAudio.wallClockAudioMediaUs(5_000_000L, 10_000_000L, 9_900_000L));
        assertEquals(5_500_000L, LegacyVideoAudio.wallClockAudioMediaUs(5_000_000L, 10_000_000L, 10_500_000L));
    }

    @Test
    void nearbyClientsDoNotAmplifyStartupSkewToAWholeSecond() {
        long first = LegacyVideoAudio.scheduledStartUs(961_000L);
        long second = LegacyVideoAudio.scheduledStartUs(1_065_000L);

        assertEquals(3_000_000L, first);
        assertEquals(3_100_000L, second);
        assertTrue(second - first <= 150_000L);
    }

    @Test
    void healthSeparatesFutureBufferRunwayFromPresentedFrameDrift() {
        long targetUs = 25_000_000L;
        assertEquals(9_000L, LegacyVideoPlayback.bufferedMs(targetUs, 34_000_000L));
        assertEquals(200L, LegacyVideoPlayback.presentedDriftMs(targetUs, 25_200_000L));
        assertEquals(-250L, LegacyVideoPlayback.presentedDriftMs(targetUs, 24_750_000L));
    }

    @Test
    void legacyTransportKeepsMultipleRealPlexSegmentsAheadOfDecodeJitter() {
        assertTrue(LegacyVideoClientState.StreamState.withinPrefetchLead(30_000L, 10_000L),
                "an eight-second HLS segment plus a slow decode must not exhaust the audio runway");
        assertFalse(LegacyVideoClientState.StreamState.withinPrefetchLead(30_001L, 10_000L));
    }

    @Test
    void legacyDecodeQueueRetainsTenSecondJitterRunwayWithoutRemovingTheByteCeiling() {
        assertTrue(LegacyVideoPlayback.allowsDecodedVideoBatch(15, 191L * 1024L * 1024L));
        assertFalse(LegacyVideoPlayback.allowsDecodedVideoBatch(16, 0L));
        assertFalse(LegacyVideoPlayback.allowsDecodedVideoBatch(0, 192L * 1024L * 1024L));
        assertFalse(LegacyVideoPlayback.allowsDecodedVideoBatch(-1, 0L));
    }

    private static VideoPackets.SessionState state(boolean paused, long position, long epoch) {
        VideoMediaItem item = new VideoMediaItem(MediaKind.MOVIE, "42", "Fixture", "", "PG", 0, 60_000, "", 0);
        return new VideoPackets.SessionState(UUID.fromString("12345678-1234-5678-9abc-def012345678"), 1L,
                UUID.fromString("87654321-4321-8765-cba9-876543210fed"), 2L, paused ? VideoPackets.SessionStatus.PAUSED : VideoPackets.SessionStatus.PLAYING,
                item, position, 60_000, paused, PresentationMode.FIT, 4, 3, new byte[] { 0x7f },
                ScreenFacing.NORTH, 30, 10, 20, Collections.emptyList(), -1, -1, epoch, true, "");
    }
}
