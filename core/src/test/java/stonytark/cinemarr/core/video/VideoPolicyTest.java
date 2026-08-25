package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoPolicyTest {
    @Test void fitLetterboxesAndFillCropsWhileStretchUsesEveryPixel() {
        PresentationTransform fit = PresentationTransform.create(16, 9, 4, 4, PresentationMode.FIT);
        assertFalse(fit.samplesSource(2.0, 0.0, 16, 9));
        assertTrue(fit.samplesSource(2.0, 2.0, 16, 9));
        PresentationTransform fill = PresentationTransform.create(16, 9, 4, 4, PresentationMode.FILL);
        assertTrue(fill.sourceX(0) > 0);
        PresentationTransform stretch = PresentationTransform.create(16, 9, 2000, 20, PresentationMode.STRETCH);
        assertEquals(125.0, stretch.scaleX());
        assertEquals(20.0 / 9.0, stretch.scaleY());
    }

    @Test void renditionDimensionsAreEvenAndDoNotUpscaleSource() {
        RenditionPolicy.Dimensions tiny = RenditionPolicy.choose(4, 4, 1920, 1080, 1920, 1080);
        assertEquals(320, tiny.width());
        assertEquals(180, tiny.height());
        RenditionPolicy.Dimensions odd = RenditionPolicy.choose(2000, 20, 1919, 1079, 1920, 1080);
        assertEquals(1918, odd.width());
        assertEquals(1078, odd.height());
    }

    @Test void mediaSegmentsAreHashedBoundedAndSeekFromKeyframes() {
        byte[] data = new byte[MediaSegment.MAX_PAYLOAD_BYTES * 2 + 7];
        MediaSegment segment = new MediaSegment(5_000, true, data);
        assertEquals(3, segment.payloads().size());
        assertEquals(7, segment.payloads().get(2).length);
        assertEquals(64, segment.sha256().length());
        KeyframeIndex index = new KeyframeIndex(Arrays.asList(0L, 2_000L, 4_000L, 8_000L));
        assertEquals(4_000, index.atOrBefore(7_999));
    }
}
