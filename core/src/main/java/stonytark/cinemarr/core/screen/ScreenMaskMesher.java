package stonytark.cinemarr.core.screen;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Greedily merges equal horizontal mask runs into renderable rectangles. */
public final class ScreenMaskMesher {
    public static List<Rectangle> mesh(int width, int height, byte[] packedMask) {
        if (width < 1 || height < 1 || packedMask == null || (long) width * height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid screen mask dimensions");
        }
        BitSet visible = BitSet.valueOf(packedMask);
        List<Rectangle> complete = new ArrayList<Rectangle>();
        Map<Run, Rectangle> active = new HashMap<Run, Rectangle>();
        for (int y = 0; y < height; y++) {
            Map<Run, Rectangle> next = new HashMap<Run, Rectangle>();
            int rowStart = y * width;
            for (int x = 0; x < width;) {
                int set = visible.nextSetBit(rowStart + x);
                if (set < rowStart || set >= rowStart + width) break;
                int start = set - rowStart;
                int clear = visible.nextClearBit(set);
                int end = Math.min(width, clear - rowStart);
                Run run = new Run(start, end - start);
                Rectangle previous = active.remove(run);
                next.put(run, previous == null ? new Rectangle(start, y, run.width, 1)
                        : new Rectangle(previous.x, previous.y, previous.width, previous.height + 1));
                x = end;
            }
            complete.addAll(active.values());
            active = next;
        }
        complete.addAll(active.values());
        return Collections.unmodifiableList(complete);
    }

    public static final class Rectangle {
        private final int x, y, width, height;
        Rectangle(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; }
        public int x(){return x;} public int y(){return y;} public int width(){return width;} public int height(){return height;}
    }
    private static final class Run {
        private final int x,width;
        Run(int x,int width){this.x=x;this.width=width;}
        @Override public boolean equals(Object value){if(this==value)return true;if(!(value instanceof Run))return false;Run other=(Run)value;return x==other.x&&width==other.width;}
        @Override public int hashCode(){return 31*x+width;}
    }
    private ScreenMaskMesher() {}
}
