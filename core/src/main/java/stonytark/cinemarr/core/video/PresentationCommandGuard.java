package stonytark.cinemarr.core.video;

import stonytark.cinemarr.core.protocol.VideoPackets;

/** Validates against the existing attachment before any world/coordinator mutation. */
public final class PresentationCommandGuard {
    private PresentationCommandGuard() {}
    public static void validate(VideoPackets.SessionCommand command, String sessionName, long generation) {
        if (command.expectedGeneration() != generation
                || (!command.sessionName().trim().isEmpty() && !command.sessionName().trim().equals(sessionName)))
            throw new IllegalStateException("TV state changed; refresh before controlling it");
    }
}
