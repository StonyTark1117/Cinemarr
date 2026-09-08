package stonytark.cinemarr.core.protocol;

import java.util.UUID;
import java.util.function.Consumer;

/** Opt-in network-only stress peer. Never retains media bytes or starts a decoder. */
public final class AcceptanceSegmentPeer {
    private UUID session;
    private long generation, startedAt = -1, nextSend, lastReport, elapsed;
    private long requests, chunks, bytes, acknowledgements, rateRejections, windowRejections, unexpectedErrors;
    private boolean running, finished;

    public void session(UUID id, long value, boolean owner, boolean playing) {
        if (!ProtocolLimits.segmentPressurePeerEnabled()) return;
        boolean eligible = !owner && playing && id != null && !id.equals(new UUID(0, 0)) && value >= 0;
        if (running && (!eligible || !id.equals(session) || value != generation)) {
            unexpectedErrors++; running = false; finished = true;
        }
        if (!eligible) { session = null; return; }
        session = id; generation = value;
    }

    public boolean start(long now) {
        if (!ProtocolLimits.segmentPressurePeerEnabled() || session == null || startedAt >= 0) return false;
        startedAt = now; nextSend = now; running = true; return true;
    }

    public void tick(long now, Consumer<CinemarrMessage> sender, Consumer<String> logger) {
        if (!ProtocolLimits.segmentPressurePeerEnabled()) return;
        if (running) {
            elapsed = Math.max(0, now - startedAt);
            if (elapsed >= 40_000L) { running = false; finished = true; }
            else if (now >= nextSend) {
                // No catch-up loop after a stalled game tick; traffic and total
                // run length stay bounded. All requests use valid wire fields.
                for (int i = 0; i < 10; i++) sender.accept(new VideoPackets.SegmentRequest(session, generation, ++requests, 0, 0, 8));
                nextSend = now + 100L;
            }
        }
        if (now - lastReport >= 1000L) { lastReport = now; logger.accept(diagnostics()); }
    }

    public void chunk(VideoPackets.SegmentChunk value, Consumer<CinemarrMessage> sender) {
        if (!ProtocolLimits.segmentPressurePeerEnabled() || startedAt < 0 || session == null
                || !session.equals(value.sessionId()) || generation != value.generation()
                || value.requestId() < 1 || value.requestId() > requests || value.segmentIndex() != 0) return;
        chunks++; bytes += value.data().length;
        if (value.chunkIndex() == Math.min(7, value.totalChunks() - 1)) {
            sender.accept(new VideoPackets.SegmentAcknowledgement(session, generation, value.requestId(), 0, value.chunkIndex(), 0));
            acknowledgements++;
        }
    }

    public void error(String message) {
        if (!ProtocolLimits.segmentPressurePeerEnabled()) return;
        if ("Invalid or excessive segment request".equals(message)) rateRejections++;
        else if ("A video transfer window is already awaiting acknowledgement".equals(message)) windowRejections++;
        else unexpectedErrors++;
    }

    public String diagnostics() {
        return "Acceptance segment peer: ready=" + (session != null) + " running=" + running + " finished=" + finished
                + " elapsedMs=" + elapsed + " requests=" + requests + " chunks=" + chunks + " bytes=" + bytes
                + " acknowledgements=" + acknowledgements + " rateRejections=" + rateRejections
                + " windowRejections=" + windowRejections + " unexpectedErrors=" + unexpectedErrors;
    }

    public void reset() {
        session = null; generation = 0; startedAt = -1; nextSend = lastReport = elapsed = 0;
        requests = chunks = bytes = acknowledgements = rateRejections = windowRejections = unexpectedErrors = 0;
        running = finished = false;
    }
}
