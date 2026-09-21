package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test void eightKOnASparseLargeBoundingBoxReservesBothSourceAndRaster() {
        for(ResolutionChoice choice:new ResolutionChoice[]{ResolutionChoice.AUTO,ResolutionChoice.preset("8k"),ResolutionChoice.custom(8192,4096)}) {
            RenditionPolicy.Dimensions result=RenditionPolicy.chooseForDisplay(2048,2048,choice,7680,4320,7680,4320);
            assertTrue(result.width()<7680);
            assertTrue(result.height()<4320);
            assertEquals(0,result.width()%2); assertEquals(0,result.height()%2);
            assertTrue(4L*result.width()*result.height()+4L*2048*2048<=PresentedFrame.MAX_RETAINED_BYTES);
            assertTrue(Math.abs(result.width()*4320L-result.height()*7680L)<=2L*7680);
        }
        // Quick TVs remain Detailed with their existing preset policy.
        assertEquals(7680,RenditionPolicy.chooseForScreen(128,72,7680,4320,7680,4320,7680,4320).width());
        assertEquals(7680,RenditionPolicy.chooseForDisplay(4,4,ResolutionChoice.preset("8k"),7680,4320,7680,4320).width());
    }
}
