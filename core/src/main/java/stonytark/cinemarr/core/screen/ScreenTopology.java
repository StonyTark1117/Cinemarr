package stonytark.cinemarr.core.screen;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validates a planar screen and produces its logical raster. */
public final class ScreenTopology {
    public static ScreenGeometry analyze(Collection<ScreenPixel> input, ScreenLimits limits) {
        return analyze(input, limits, true);
    }

    /**
     * @param allowIrregular when false, every cell in the rectangular bounds must be present
     */
    public static ScreenGeometry analyze(Collection<ScreenPixel> input, ScreenLimits limits, boolean allowIrregular) {
        if (input == null || limits == null) throw new IllegalArgumentException("Pixels and limits are required");
        Set<ScreenPixel> pixels = new LinkedHashSet<ScreenPixel>(input);
        if (pixels.size() != input.size()) throw new IllegalArgumentException("Duplicate screen pixel");
        if (pixels.size() < limits.minimumPixels()) throw new IllegalArgumentException("Screen has too few pixels");
        if (pixels.size() > limits.maximumPixels()) throw new IllegalArgumentException("Screen has too many pixels");

        ScreenPixel first = pixels.iterator().next();
        ScreenFacing facing = first.facing();
        int plane = facing.plane(first);
        int minimumU = Integer.MAX_VALUE;
        int maximumU = Integer.MIN_VALUE;
        int minimumV = Integer.MAX_VALUE;
        int maximumV = Integer.MIN_VALUE;
        Set<Long> projected = new HashSet<Long>();
        Set<ScreenGeometry.ChunkCoordinate> chunks = new LinkedHashSet<ScreenGeometry.ChunkCoordinate>();
        for (ScreenPixel pixel : pixels) {
            if (pixel.facing() != facing) throw new IllegalArgumentException("Screen pixels face different directions");
            if (facing.plane(pixel) != plane) throw new IllegalArgumentException("Screen pixels are not coplanar");
            int u = facing.u(pixel);
            int v = facing.v(pixel);
            projected.add(pack(u, v));
            minimumU = Math.min(minimumU, u);
            maximumU = Math.max(maximumU, u);
            minimumV = Math.min(minimumV, v);
            maximumV = Math.max(maximumV, v);
            chunks.add(new ScreenGeometry.ChunkCoordinate(Math.floorDiv(pixel.x(), 16), Math.floorDiv(pixel.z(), 16)));
        }

        long widthLong = (long) maximumU - minimumU + 1;
        long heightLong = (long) maximumV - minimumV + 1;
        if (widthLong > limits.maximumDimension() || heightLong > limits.maximumDimension()) {
            throw new IllegalArgumentException("Screen dimension exceeds configured maximum");
        }
        assertConnected(projected);
        int width = (int) widthLong;
        int height = (int) heightLong;
        if (!allowIrregular && (long) projected.size() != widthLong * heightLong) {
            throw new IllegalArgumentException("Screen must be a solid rectangle");
        }
        BitSet mask = new BitSet(width * height);
        for (long packed : projected) {
            int u = unpackFirst(packed);
            int v = unpackSecond(packed);
            mask.set((v - minimumV) * width + (u - minimumU));
        }
        return new ScreenGeometry(facing, plane, minimumU, minimumV, width, height, mask, chunks);
    }

    private static void assertConnected(Set<Long> pixels) {
        Set<Long> visited = new HashSet<Long>();
        ArrayDeque<Long> pending = new ArrayDeque<Long>();
        pending.add(pixels.iterator().next());
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            if (!visited.add(current)) continue;
            int u = unpackFirst(current);
            int v = unpackSecond(current);
            enqueue(pixels, visited, pending, u + 1, v);
            enqueue(pixels, visited, pending, u - 1, v);
            enqueue(pixels, visited, pending, u, v + 1);
            enqueue(pixels, visited, pending, u, v - 1);
        }
        if (visited.size() != pixels.size()) throw new IllegalArgumentException("Screen contains disconnected islands");
    }

    private static void enqueue(Set<Long> pixels, Set<Long> visited, ArrayDeque<Long> pending, int u, int v) {
        long value = pack(u, v);
        if (pixels.contains(value) && !visited.contains(value)) pending.add(value);
    }

    private static long pack(int first, int second) { return ((long) first << 32) ^ (second & 0xffffffffL); }
    private static int unpackFirst(long packed) { return (int) (packed >> 32); }
    private static int unpackSecond(long packed) { return (int) packed; }
    private ScreenTopology() {}
}
