package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayResolutionPolicyTest {
    @Test void explicitQualityBypassesTinyAutoAndClampsWithoutUpscaling() {
        assertEquals(320, choose(ResolutionChoice.AUTO, 1920, 1080, 1920, 1080).width());
        assertEquals(1920, choose(ResolutionChoice.preset("1080p"), 1920, 1080, 1920, 1080).width());
        assertEquals(1280, choose(ResolutionChoice.preset("8k"), 1920, 1080, 1280, 720).width());
        assertEquals(640, choose(ResolutionChoice.preset("8k"), 640, 360, 1920, 1080).width());
        assertEquals(16, choose(ResolutionChoice.custom(17, 11), 1920, 1080, 1920, 1080).width());
    }
    private RenditionPolicy.Dimensions choose(ResolutionChoice choice, int sw, int sh, int mw, int mh) {
        return RenditionPolicy.chooseForDisplay(4, 4, choice, sw, sh, mw, mh);
    }
}
