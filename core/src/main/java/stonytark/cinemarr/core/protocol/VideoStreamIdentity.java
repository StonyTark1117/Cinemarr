package stonytark.cinemarr.core.protocol;

import java.util.UUID;

/** Identifies both the shared playback timeline and one TV's media incarnation. */
public final class VideoStreamIdentity {
    private final UUID timelineId, streamId;
    private final long timelineGeneration, streamGeneration;

    public VideoStreamIdentity(UUID timelineId, long timelineGeneration, UUID streamId, long streamGeneration) {
        if (timelineId == null || streamId == null || timelineGeneration < 0 || streamGeneration < 0)
            throw new IllegalArgumentException("Invalid video stream identity");
        this.timelineId = timelineId; this.timelineGeneration = timelineGeneration;
        this.streamId = streamId; this.streamGeneration = streamGeneration;
    }

    public UUID timelineId() { return timelineId; }
    public long timelineGeneration() { return timelineGeneration; }
    public UUID streamId() { return streamId; }
    public long streamGeneration() { return streamGeneration; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof VideoStreamIdentity)) return false;
        VideoStreamIdentity value = (VideoStreamIdentity) other;
        return timelineId.equals(value.timelineId) && timelineGeneration == value.timelineGeneration
                && streamId.equals(value.streamId) && streamGeneration == value.streamGeneration;
    }
    @Override public int hashCode() {
        int hash = timelineId.hashCode();
        hash = 31 * hash + Long.valueOf(timelineGeneration).hashCode();
        hash = 31 * hash + streamId.hashCode();
        return 31 * hash + Long.valueOf(streamGeneration).hashCode();
    }
}
