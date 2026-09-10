package stonytark.cinemarr.core.client;

import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

/** A displayed frame may move to a newer paused revision of the same program.
 * Decoder work, buffered audio and transfer ownership must never move with it. */
public final class PausedFrameRetention {
    public static boolean permits(UUID previousSession, long previousGeneration, String previousItem,
                                  VideoPackets.SessionState next) {
        return previousSession != null && previousGeneration >= 0
                && permits(new VideoStreamIdentity(previousSession, previousGeneration, previousSession, previousGeneration), previousItem, next);
    }

    public static boolean permits(VideoStreamIdentity previous, String previousItem, VideoPackets.SessionState next) {
        return previous != null && previousItem != null && !previousItem.isEmpty() && next != null
                && previous.timelineId().equals(next.timelineId()) && previous.streamId().equals(next.sessionId())
                && next.timelineGeneration() >= previous.timelineGeneration() && next.generation() >= previous.streamGeneration()
                && (next.timelineGeneration() > previous.timelineGeneration() || next.generation() > previous.streamGeneration())
                && next.paused() && next.status() == VideoPackets.SessionStatus.PAUSED
                && next.item() != null && previousItem.equals(next.item().key());
    }

    private PausedFrameRetention() {}
}
