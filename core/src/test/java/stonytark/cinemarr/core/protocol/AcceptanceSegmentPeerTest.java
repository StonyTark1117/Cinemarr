package stonytark.cinemarr.core.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceSegmentPeerTest {
    private final AcceptanceSegmentPeer peer = new AcceptanceSegmentPeer();
    private final UUID session = UUID.randomUUID();
    private final List<CinemarrMessage> sent = new ArrayList<CinemarrMessage>();

    @BeforeEach void enable() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY, "true");
        System.setProperty("cinemarr.acceptance.segmentPressurePeer", "true");
    }
    @AfterEach void clear() {
        System.clearProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_VIDEO_LEADER_PROPERTY);
        System.clearProperty("cinemarr.acceptance.segmentPressurePeer");
    }
    private void ready() { peer.session(session, 3, false, true); assertTrue(peer.start(1000)); }
    private void tick(long now) { peer.tick(now, sent::add, ignored -> {}); }
    private VideoPackets.SegmentChunk chunk(UUID id, long generation, long request, int index, int total) {
        return new VideoPackets.SegmentChunk(id, generation, request, 0, index, total, 0, true, "", new byte[4]);
    }

    @Test void requiresEveryOptInAndNeverRunsOnOwnerClient() {
        for (String property : new String[] {ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY,
                ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY, "cinemarr.acceptance.segmentPressurePeer"}) {
            System.clearProperty(property);
            peer.session(session, 3, false, true); assertFalse(peer.start(1000)); tick(1000);
            assertTrue(sent.isEmpty()); System.setProperty(property, "true");
        }
        System.setProperty(ProtocolLimits.ACCEPTANCE_VIDEO_LEADER_PROPERTY, "true");
        peer.session(session, 3, false, true); assertFalse(peer.start(1000));
    }
    @Test void requiresNonOwnerPlayingIdentity() {
        peer.session(session, 3, true, true); assertFalse(peer.start(1000));
        peer.session(session, 3, false, false); assertFalse(peer.start(1000));
        peer.session(new UUID(0, 0), 3, false, true); assertFalse(peer.start(1000));
        peer.session(null, 3, false, true); assertFalse(peer.start(1000));
        peer.session(session, -1, false, true); assertFalse(peer.start(1000));
        ready();
    }
    @Test void sendsValidBoundedRequestsAndDoesNotCatchUpOrRestart() {
        ready(); tick(1000); tick(1050); assertEquals(10, sent.size());
        tick(21000); assertEquals(20, sent.size());
        tick(41000); tick(81000); assertEquals(20, sent.size());
        assertFalse(peer.start(82000));
        for (int i = 0; i < sent.size(); i++) {
            VideoPackets.SegmentRequest value = (VideoPackets.SegmentRequest) sent.get(i);
            assertEquals(session, value.sessionId()); assertEquals(3, value.generation());
            assertEquals(i + 1, value.requestId()); assertEquals(0, value.segmentIndex());
            assertEquals(0, value.firstChunk()); assertEquals(8, value.chunkCount());
        }
        assertTrue(peer.diagnostics().contains("running=false finished=true"));
    }
    @Test void completeFortySecondRunHasHardTrafficBound() {
        ready(); for (long now = 1000; now <= 42000; now++) tick(now);
        assertEquals(4000, sent.size());
        assertTrue(peer.diagnostics().contains("elapsedMs=40000 requests=4000"));
    }
    @Test void acknowledgesOnlyMatchingRequestedWindowsIncludingShortFinalWindow() {
        ready(); tick(1000); sent.clear();
        peer.chunk(chunk(UUID.randomUUID(), 3, 1, 7, 12), sent::add);
        peer.chunk(chunk(session, 4, 1, 7, 12), sent::add);
        peer.chunk(chunk(session, 3, 11, 7, 12), sent::add);
        assertTrue(sent.isEmpty());
        peer.chunk(chunk(session, 3, 1, 0, 12), sent::add); assertTrue(sent.isEmpty());
        peer.chunk(chunk(session, 3, 1, 7, 12), sent::add);
        peer.chunk(chunk(session, 3, 2, 2, 3), sent::add);
        assertEquals(2, sent.size());
        assertEquals(7, ((VideoPackets.SegmentAcknowledgement) sent.get(0)).receivedThroughChunk());
        assertEquals(2, ((VideoPackets.SegmentAcknowledgement) sent.get(1)).receivedThroughChunk());
        assertTrue(peer.diagnostics().contains("chunks=3 bytes=12 acknowledgements=2"));
    }
    @Test void sessionReplacementOrStopAbortsAndCannotRestartWithoutReset() {
        ready(); tick(1000); peer.session(session, 4, false, true); tick(1100);
        assertEquals(10, sent.size()); assertFalse(peer.start(1200));
        assertTrue(peer.diagnostics().contains("unexpectedErrors=1"));
        peer.reset(); sent.clear(); ready(); peer.session(session, 3, false, false); tick(1100);
        assertTrue(sent.isEmpty()); assertFalse(peer.start(1200));
        assertTrue(peer.diagnostics().contains("ready=false running=false finished=true"));
    }
    @Test void distinguishesExpectedRejectionsAndResetDropsAllPriorEvidence() {
        ready(); tick(1000);
        peer.error("Invalid or excessive segment request");
        peer.error("A video transfer window is already awaiting acknowledgement");
        peer.error("Invalid video buffer acknowledgement");
        assertTrue(peer.diagnostics().contains("rateRejections=1 windowRejections=1 unexpectedErrors=1"));
        peer.reset(); sent.clear(); peer.chunk(chunk(session, 3, 1, 7, 8), sent::add); tick(1100);
        assertTrue(sent.isEmpty()); assertTrue(peer.diagnostics().contains("requests=0 chunks=0 bytes=0"));
        ready();
    }
}
