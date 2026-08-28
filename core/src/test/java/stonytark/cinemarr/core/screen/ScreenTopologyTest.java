package stonytark.cinemarr.core.screen;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenTopologyTest {
    @Test void preservesAnLShapedVisibilityMask() {
        List<ScreenPixel> pixels = Arrays.asList(
                pixel(10, 20), pixel(11, 20), pixel(12, 20), pixel(10, 21), pixel(10, 22));
        ScreenGeometry result = ScreenTopology.analyze(pixels, ScreenLimits.DEFAULTS);
        assertEquals(3, result.width());
        assertEquals(3, result.height());
        assertEquals(5, result.pixelCount());
        assertTrue(result.visibleAt(0, 2));
        assertFalse(result.visibleAt(2, 2));
    }

    @Test void solidPolicyAcceptsOddRectanglesAndLinesButRejectsHolesAndCorners() {
        assertEquals(5, ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(3, 0), pixel(4, 0)),
                ScreenLimits.DEFAULTS, false).width());
        assertEquals(3, ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(2, 0),
                pixel(0, 1), pixel(1, 1), pixel(2, 1),
                pixel(0, 2), pixel(1, 2), pixel(2, 2)),
                ScreenLimits.DEFAULTS, false).height());
        assertEquals(5, ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(0, 1), pixel(0, 2), pixel(0, 3), pixel(0, 4)),
                ScreenLimits.DEFAULTS, false).height());
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(0, 1), pixel(2, 1),
                pixel(0, 2), pixel(1, 2), pixel(2, 2)), ScreenLimits.DEFAULTS, false));
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(0, 1), pixel(0, 2)), ScreenLimits.DEFAULTS, false));
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(1, 1), pixel(1, 2)), ScreenLimits.DEFAULTS, false));
        assertEquals(5, ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(2, 0), pixel(1, 1), pixel(1, 2)), ScreenLimits.DEFAULTS, true).pixelCount());
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(10, 0), pixel(11, 0)), ScreenLimits.DEFAULTS, true));
    }

    @Test void acceptsTheProposedTwoThousandByTwentyScreenWithoutLoadingChunks() {
        List<ScreenPixel> pixels = new ArrayList<ScreenPixel>(40_000);
        for (int x = -1000; x < 1000; x++) for (int y = 0; y < 20; y++) pixels.add(pixel(x, y));
        ScreenGeometry result = ScreenTopology.analyze(pixels, ScreenLimits.DEFAULTS);
        assertEquals(2_000, result.width());
        assertEquals(20, result.height());
        assertEquals(40_000, result.pixelCount());
        assertEquals(126, result.chunks().size());
    }

    @Test void rejectsDisconnectedNonPlanarAndMixedFacingScreens() {
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(10, 0), pixel(11, 0)), ScreenLimits.DEFAULTS));
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(0, 1), new ScreenPixel(1, 1, 1, ScreenFacing.NORTH)), ScreenLimits.DEFAULTS));
        assertThrows(IllegalArgumentException.class, () -> ScreenTopology.analyze(Arrays.asList(
                pixel(0, 0), pixel(1, 0), pixel(0, 1), new ScreenPixel(1, 1, 0, ScreenFacing.SOUTH)), ScreenLimits.DEFAULTS));
    }

    private static ScreenPixel pixel(int x, int y) { return new ScreenPixel(x, y, 0, ScreenFacing.NORTH); }
}
