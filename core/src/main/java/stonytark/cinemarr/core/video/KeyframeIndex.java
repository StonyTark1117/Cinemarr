package stonytark.cinemarr.core.video;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable seek index that always starts a late join or seek at a decodable keyframe. */
public final class KeyframeIndex {
    private final List<Long> times;

    public KeyframeIndex(List<Long> keyframeTimesMs) {
        if (keyframeTimesMs == null || keyframeTimesMs.isEmpty() || keyframeTimesMs.get(0) != 0L) {
            throw new IllegalArgumentException("Keyframe index must begin at zero");
        }
        List<Long> copy = new ArrayList<Long>(keyframeTimesMs.size());
        long previous = -1;
        for (Long time : keyframeTimesMs) {
            if (time == null || time <= previous) throw new IllegalArgumentException("Keyframes must be strictly ordered");
            copy.add(time);
            previous = time;
        }
        times = Collections.unmodifiableList(copy);
    }

    public long atOrBefore(long targetMs) {
        if (targetMs <= 0) return 0;
        int low = 0;
        int high = times.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (times.get(middle) <= targetMs) low = middle + 1;
            else high = middle - 1;
        }
        return times.get(Math.max(0, high));
    }
}
