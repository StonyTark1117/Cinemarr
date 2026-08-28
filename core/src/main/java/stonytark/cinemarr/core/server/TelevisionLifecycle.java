package stonytark.cinemarr.core.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Process-local cross-dimension registration index and removal notification bridge. */
public final class TelevisionLifecycle {
    public interface Listener { void removed(UUID televisionId, String sessionName); }

    private static final Map<UUID, UUID> OWNERS = new LinkedHashMap<UUID, UUID>();
    private static Listener listener;

    public static synchronized void reset(Listener value) {
        OWNERS.clear();
        listener = value;
    }

    public static synchronized void listener(Listener value) { listener = value; }

    public static synchronized boolean register(UUID televisionId, UUID owner, int maximumPerOwner) {
        if (televisionId == null || owner == null || maximumPerOwner < 1) return false;
        UUID existing = OWNERS.get(televisionId);
        if (owner.equals(existing)) return true;
        if (count(owner) >= maximumPerOwner) return false;
        OWNERS.put(televisionId, owner);
        return true;
    }

    /** Restores a durable registration even when an owner is already above a newly lowered cap. */
    public static synchronized void restore(UUID televisionId, UUID owner) {
        if (televisionId != null && owner != null) OWNERS.put(televisionId, owner);
    }

    public static synchronized void unregister(UUID televisionId, String sessionName) {
        if (televisionId == null || OWNERS.remove(televisionId) == null) return;
        Listener current = listener;
        if (current != null) current.removed(televisionId, sessionName == null ? "" : sessionName);
    }

    public static synchronized int count(UUID owner) {
        int count = 0;
        for (UUID value : OWNERS.values()) if (value.equals(owner)) count++;
        return count;
    }

    public static synchronized int count() { return OWNERS.size(); }

    private TelevisionLifecycle() {}
}
