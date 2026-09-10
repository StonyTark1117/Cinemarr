package stonytark.cinemarr.core.video;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.client.DecodedBufferBudget;
import static org.junit.jupiter.api.Assertions.*;

class PresentedFrameTest {
    @Test void rasterIsReusedUntilFrameGeometryOrLayoutChanges() {
        PresentedFrame frame = new PresentedFrame();
        byte[] source = new byte[] {(byte)255, 0, 0, (byte)255};
        frame.accept(source, 1, 1);
        byte[] first = frame.raster(2, 2, PresentationMode.STRETCH);
        assertSame(first, frame.raster(2, 2, PresentationMode.STRETCH));
        assertEquals(20, frame.retainedBytes());
        assertNotSame(first, frame.raster(3, 2, PresentationMode.STRETCH));
        assertNotSame(first, frame.raster(3, 2, PresentationMode.FILL));
    }

    @Test void releaseAndClearDropBothSourceAndDerivedRasterOwnership() {
        PresentedFrame frame = new PresentedFrame();
        frame.accept(new byte[4 * 4 * 4], 4, 4);
        frame.raster(4, 4, PresentationMode.FIT);
        assertEquals(128, frame.retainedBytes());
        frame.releaseRaster(); assertEquals(64, frame.retainedBytes());
        frame.clear(); assertEquals(0, frame.retainedBytes()); assertNull(frame.raster(1, 1, PresentationMode.FIT));
    }

    @Test void fullBoundingRasterBudgetIsCheckedBeforeDerivation() {
        PresentedFrame frame = new PresentedFrame();
        frame.accept(new byte[(int) (DecodedBufferBudget.MAX_VIDEO_FRAME_BYTES / 4)], 4096, 2048);
        assertThrows(IllegalArgumentException.class,
                () -> frame.raster(8192, 4096, PresentationMode.STRETCH));
        assertEquals(DecodedBufferBudget.MAX_VIDEO_FRAME_BYTES / 4, frame.retainedBytes());
        assertThrows(IllegalArgumentException.class, () -> frame.accept(new byte[4], 0, 1));
    }
}
