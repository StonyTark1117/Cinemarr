package stonytark.cinemarr.core.screen;

import java.util.UUID;
import stonytark.cinemarr.core.protocol.ProtocolLimits;
import stonytark.cinemarr.core.video.TvDisplaySettings;

/** Sanitized acceptance evidence emitted only after a saved registration is restored. */
public final class DisplayPersistenceProbe {
    private DisplayPersistenceProbe() {}

    public static void restored(UUID television, long controller, TvDisplaySettings display,
                                int width, int height) {
        if (!ProtocolLimits.displayProbeEnabled()) return;
        System.out.println("Acceptance display restored: controller=" + controller + " tv=" + television
                + " revision=" + display.revision() + " origin=" + display.origin()
                + " layout=" + display.layout() + " mapping=" + display.mapping()
                + " requested=" + display.resolution() + " width=" + width + " height=" + height);
    }
}
