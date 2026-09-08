package stonytark.cinemarr.core.protocol;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolLimitsTest {
    @Test void browsePressureRequiresAllOptInsAndNeverRunsOnLeader() {
        System.setProperty("cinemarr.acceptance.browsePressureProbe", "true");
        assertEquals(false, ProtocolLimits.browsePressureProbeEnabled());
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        assertEquals(false, ProtocolLimits.browsePressureProbeEnabled());
        System.setProperty(ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY, "true");
        assertEquals(true, ProtocolLimits.browsePressureProbeEnabled());
        System.setProperty(ProtocolLimits.ACCEPTANCE_VIDEO_LEADER_PROPERTY, "true");
        assertEquals(false, ProtocolLimits.browsePressureProbeEnabled());
    }
    @Test void worldChangeCommandRequiresExplicitVideoModeOperatorAndExactTestTarget() {
        System.setProperty("cinemarr.acceptance.worldChangeProbe", "true");
        assertEquals(false, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoB", -1, true));
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        assertEquals(false, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoB", -1, true));
        System.setProperty(ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY, "true");
        assertEquals(true, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoB", -1, true));
        assertEquals(true, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoB", 0, true));
        assertEquals(false, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoB", 1, true));
        assertEquals(false, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoA", -1, true));
        assertEquals(false, ProtocolLimits.worldChangeProbeAllows("ordinaryPlayer", -1, true));
        assertEquals(false, ProtocolLimits.worldChangeProbeAllows("CinemarrVideoB", -1, false));
    }
    @Test void videoProbeRejectsDeadPlayersAndObscuringScreens() {
        assertEquals(true, ProtocolLimits.videoProbeViewReady(true, false));
        assertEquals(false, ProtocolLimits.videoProbeViewReady(false, false));
        assertEquals(false, ProtocolLimits.videoProbeViewReady(true, true));
        assertEquals(false, ProtocolLimits.videoProbeViewReady(false, true));
    }

    @Test void videoProbeCamerasStayDistinctWhenFirstArrivalReconnects() {
        // B arrives first, A second, then B disconnects and rejoins. The old
        // player-list-index camera put both participants at +1.5 after rejoin.
        double firstB = ProtocolLimits.videoProbeCameraX("CinemarrVideoB");
        double nextA = ProtocolLimits.videoProbeCameraX("CinemarrVideoA");
        double rejoinedB = ProtocolLimits.videoProbeCameraX("CinemarrVideoB");
        assertEquals(1.5D, firstB);
        assertEquals(-1.5D, nextA);
        assertEquals(firstB, rejoinedB);
        assertEquals(3.0D, rejoinedB - nextA);
    }

    @Test void videoProbeCamerasDoNotDependOnWhichParticipantArrivesFirst() {
        assertEquals(-1.5D, ProtocolLimits.videoProbeCameraX("CinemarrVideoA"));
        assertEquals(1.5D, ProtocolLimits.videoProbeCameraX("CinemarrVideoB"));
        assertEquals(-1.5D, ProtocolLimits.videoProbeCameraX("CinemarrVideoA"));
    }

    @AfterEach void clearAcceptanceProperties() {
        System.clearProperty("cinemarr.acceptance.browsePressureProbe");
        System.clearProperty(ProtocolLimits.ACCEPTANCE_VIDEO_LEADER_PROPERTY);
        System.clearProperty("cinemarr.acceptance.worldChangeProbe");
        System.clearProperty(ProtocolLimits.ACCEPTANCE_VIDEO_PROBE_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_CLIENT_PROTOCOL_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_SUPPRESS_HELLO_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_COMMAND_PROBE_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_AUDIO_PROBE_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_AUDIO_LEADER_PROPERTY);
        System.clearProperty(ProtocolLimits.ACCEPTANCE_AUDIO_CONTROL_FILE_PROPERTY);
    }

    @Test void productionClientHelloIsAlwaysCurrentProtocol() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_CLIENT_PROTOCOL_PROPERTY, "4");
        assertEquals(ProtocolLimits.VERSION, ProtocolLimits.clientHelloVersion());
    }

    @Test void explicitAcceptanceGateCanOfferAnIncompatibleProtocol() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_CLIENT_PROTOCOL_PROPERTY, "4");
        assertEquals(4, ProtocolLimits.clientHelloVersion());
    }

    @Test void invalidAcceptanceOverridesFailClosedToCurrentProtocol() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_CLIENT_PROTOCOL_PROPERTY, "not-a-number");
        assertEquals(ProtocolLimits.VERSION, ProtocolLimits.clientHelloVersion());
        System.setProperty(ProtocolLimits.ACCEPTANCE_CLIENT_PROTOCOL_PROPERTY, "-1");
        assertEquals(ProtocolLimits.VERSION, ProtocolLimits.clientHelloVersion());
    }

    @Test void helloSuppressionRequiresTheExplicitAcceptanceGate() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_SUPPRESS_HELLO_PROPERTY, "true");
        assertEquals(false, ProtocolLimits.clientHelloSuppressed());
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        assertEquals(true, ProtocolLimits.clientHelloSuppressed());
    }

    @Test void commandProbeRequiresTheExplicitAcceptanceGate() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_COMMAND_PROBE_PROPERTY, "true");
        assertEquals(false, ProtocolLimits.commandProbeEnabled());
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        assertEquals(true, ProtocolLimits.commandProbeEnabled());
    }

    @Test void audioProbeAndLeaderRequireTheExplicitAcceptanceGate() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_AUDIO_PROBE_PROPERTY, "true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_AUDIO_LEADER_PROPERTY, "true");
        assertEquals(false, ProtocolLimits.audioProbeEnabled());
        assertEquals(false, ProtocolLimits.audioProbeLeader());
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        assertEquals(true, ProtocolLimits.audioProbeEnabled());
        assertEquals(true, ProtocolLimits.audioProbeLeader());
    }

    @Test void audioControlFileRequiresTheExplicitAudioGate() {
        System.setProperty(ProtocolLimits.ACCEPTANCE_AUDIO_CONTROL_FILE_PROPERTY, "/tmp/probe");
        assertEquals("", ProtocolLimits.audioControlFile());
        System.setProperty(ProtocolLimits.ACCEPTANCE_ENABLED_PROPERTY, "true");
        System.setProperty(ProtocolLimits.ACCEPTANCE_AUDIO_PROBE_PROPERTY, "true");
        assertEquals("/tmp/probe", ProtocolLimits.audioControlFile());
    }
}
