package stonytark.cinemarr.screen;

/** Modern-compatible packed block positions without depending on post-1.7 Minecraft classes. */
public final class LegacyBlockPos {
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (long) y & 0xfffL;
    }

    public static int x(long packed) { return (int) (packed >> 38); }
    public static int y(long packed) { return (int) (packed << 52 >> 52); }
    public static int z(long packed) { return (int) (packed << 26 >> 38); }

    private LegacyBlockPos() {}
}
