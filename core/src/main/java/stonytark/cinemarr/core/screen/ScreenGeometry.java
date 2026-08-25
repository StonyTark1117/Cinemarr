package stonytark.cinemarr.core.screen;

import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Validated screen raster and visibility mask; no chunk needs to be loaded to reconstruct it. */
public final class ScreenGeometry {
    private final ScreenFacing facing;
    private final int plane;
    private final int minimumU;
    private final int minimumV;
    private final int width;
    private final int height;
    private final BitSet mask;
    private final Set<ChunkCoordinate> chunks;

    ScreenGeometry(ScreenFacing facing, int plane, int minimumU, int minimumV, int width, int height,
                   BitSet mask, Set<ChunkCoordinate> chunks) {
        this.facing = facing;
        this.plane = plane;
        this.minimumU = minimumU;
        this.minimumV = minimumV;
        this.width = width;
        this.height = height;
        this.mask = (BitSet) mask.clone();
        this.chunks = Collections.unmodifiableSet(new LinkedHashSet<ChunkCoordinate>(chunks));
    }

    public ScreenFacing facing() { return facing; }
    public int plane() { return plane; }
    public int minimumU() { return minimumU; }
    public int minimumV() { return minimumV; }
    public int width() { return width; }
    public int height() { return height; }
    public int pixelCount() { return mask.cardinality(); }
    public boolean visibleAt(int u, int v) {
        return u >= 0 && v >= 0 && u < width && v < height && mask.get(v * width + u);
    }
    public BitSet visibilityMask() { return (BitSet) mask.clone(); }
    public Set<ChunkCoordinate> chunks() { return chunks; }

    public static final class ChunkCoordinate {
        private final int x;
        private final int z;

        public ChunkCoordinate(int x, int z) { this.x = x; this.z = z; }
        public int x() { return x; }
        public int z() { return z; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ChunkCoordinate)) return false;
            ChunkCoordinate value = (ChunkCoordinate) other;
            return x == value.x && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, z); }
    }
}
