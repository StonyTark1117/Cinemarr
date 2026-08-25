package stonytark.cinemarr.core.screen;

import java.util.Objects;

/** Minecraft-independent world coordinate retained in saved screen membership. */
public final class ScreenPixel {
    private final int x;
    private final int y;
    private final int z;
    private final ScreenFacing facing;

    public ScreenPixel(int x, int y, int z, ScreenFacing facing) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = Objects.requireNonNull(facing, "facing");
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public ScreenFacing facing() { return facing; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ScreenPixel)) return false;
        ScreenPixel pixel = (ScreenPixel) other;
        return x == pixel.x && y == pixel.y && z == pixel.z && facing == pixel.facing;
    }

    @Override public int hashCode() { return Objects.hash(x, y, z, facing); }
}
