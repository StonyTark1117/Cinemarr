package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.protocol.VideoPackets;

import java.util.function.BooleanSupplier;

/** Health is advisory telemetry: an ordinary generation race is not a user error. */
public final class VideoHealthPolicy {
    public enum Decision { ACCEPT, IGNORE_STALE, INVALID }

    public static Decision classify(VideoPackets.ClientHealth report, BooleanSupplier currentViewer) {
        if (report == null || report.sessionId() == null || report.generation() < 0
                || !("PLAYING".equals(report.state()) || "BUFFERING".equals(report.state()))
                || report.decoderRecoveries() < 0 || report.videoDrops() < 0 || report.audioUnderruns() < 0
                || report.bufferedMs() < 0 || report.bufferedMs() > 60_000
                || report.driftMs() < -30_000 || report.driftMs() > 30_000) {
            return Decision.INVALID;
        }
        // Validate before consulting the coordinator, including null identifiers
        // and signed bounds (Math.abs(Long.MIN_VALUE) is still negative).
        return currentViewer.getAsBoolean() ? Decision.ACCEPT : Decision.IGNORE_STALE;
    }

    private VideoHealthPolicy() {}
}
