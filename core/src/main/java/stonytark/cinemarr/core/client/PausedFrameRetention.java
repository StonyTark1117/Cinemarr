package stonytark.cinemarr.core.client;

import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoPackets;

/** A displayed frame may move to a newer paused revision of the same program.
 * Decoder work, buffered audio and transfer ownership must never move with it. */
public final class PausedFrameRetention {
    public static boolean permits(UUID previousSession, long previousGeneration, String previousItem,
                                  VideoPackets.SessionState next) {
        return previousSession != null && previousGeneration >= 0
                && previousItem != null && !previousItem.isEmpty() && next != null
                && previousSession.equals(next.sessionId()) && next.generation() > previousGeneration
                && next.paused() && next.status() == VideoPackets.SessionStatus.PAUSED
                && next.item() != null && previousItem.equals(next.item().key());
    }

    private PausedFrameRetention() {}
}
