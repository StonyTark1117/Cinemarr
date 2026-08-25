package stonytark.cinemarr.core.screen;

/** The outward-facing normal of a planar block screen. */
public enum ScreenFacing {
    NORTH, SOUTH, EAST, WEST, UP, DOWN;

    int plane(ScreenPixel pixel) {
        switch (this) {
            case NORTH:
            case SOUTH: return pixel.z();
            case EAST:
            case WEST: return pixel.x();
            default: return pixel.y();
        }
    }

    int u(ScreenPixel pixel) {
        switch (this) {
            case EAST:
            case WEST: return pixel.z();
            default: return pixel.x();
        }
    }

    int v(ScreenPixel pixel) {
        switch (this) {
            case UP:
            case DOWN: return pixel.z();
            default: return pixel.y();
        }
    }
}
