package stonytark.cinemarr.core.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side ownership and construction-count policy. */
public final class TelevisionPolicy {
    private final int maximumScreensPerOwner;
    private final Map<UUID, Integer> owned = new HashMap<UUID, Integer>();

    public TelevisionPolicy(int maximumScreensPerOwner) {
        if (maximumScreensPerOwner < 1) throw new IllegalArgumentException("maximumScreensPerOwner");
        this.maximumScreensPerOwner = maximumScreensPerOwner;
    }

    public synchronized boolean claim(UUID owner) {
        if (owner == null) return false;
        int count = owned.containsKey(owner) ? owned.get(owner) : 0;
        if (count >= maximumScreensPerOwner) return false;
        owned.put(owner, count + 1);
        return true;
    }

    public synchronized void release(UUID owner) {
        Integer count = owned.get(owner);
        if (count == null) return;
        if (count <= 1) owned.remove(owner);
        else owned.put(owner, count - 1);
    }

    public synchronized int ownedBy(UUID owner) { Integer count = owned.get(owner); return count == null ? 0 : count; }
}
