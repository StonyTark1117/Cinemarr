package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-owned, process-local authority for durable television registrations.
 * Platform saved data remains the durable store; this index joins every loaded
 * dimension and provides one removal path even when Plex is unavailable.
 */
public final class TelevisionLifecycle {
    public interface Listener { void removed(UUID televisionId, String sessionName); }
    public interface Removal { void remove(); }

    public enum Validation {
        /** Saved geometry is valid, but at least one relevant chunk is not loaded. */
        SAVED,
        /** Saved geometry and every currently loaded backing block were validated. */
        LOADED
    }

    public static final class Registration {
        private final UUID id;
        private final UUID owner;
        private final String dimension;
        private final long controllerPos;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private final Set<Long> pixels;
        private volatile String sessionName;
        private volatile Validation validation;
        private volatile boolean attached;
        private final Removal removal;

        public Registration(UUID id, UUID owner, String dimension, long controllerPos,
                            int controllerX, int controllerY, int controllerZ,
                            Set<Long> pixels, String sessionName, Validation validation,
                            Removal removal) {
            if (id == null || owner == null || dimension == null || pixels == null || pixels.isEmpty()) {
                throw new IllegalArgumentException("Invalid television registration");
            }
            this.id = id;
            this.owner = owner;
            this.dimension = dimension;
            this.controllerPos = controllerPos;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.pixels = Collections.unmodifiableSet(new HashSet<Long>(pixels));
            this.sessionName = clean(sessionName);
            this.validation = validation == null ? Validation.SAVED : validation;
            this.removal = removal;
        }

        public UUID id() { return id; }
        public UUID owner() { return owner; }
        public String dimension() { return dimension; }
        public long controllerPos() { return controllerPos; }
        public int controllerX() { return controllerX; }
        public int controllerY() { return controllerY; }
        public int controllerZ() { return controllerZ; }
        public int pixelCount() { return pixels.size(); }
        public String sessionName() { return sessionName; }
        public Validation validation() { return validation; }
        public boolean attached() { return attached; }

        private Registration snapshot() {
            Registration value = new Registration(id, owner, dimension, controllerPos, controllerX,
                    controllerY, controllerZ, pixels, sessionName, validation, null);
            value.attached = attached;
            return value;
        }
    }

    private static final Map<UUID, Registration> REGISTRATIONS = new LinkedHashMap<UUID, Registration>();
    private static Listener listener;

    public static synchronized void reset(Listener value) {
        REGISTRATIONS.clear();
        listener = value;
    }

    public static synchronized void listener(Listener value) { listener = value; }

    /** Compatibility entry point for loader-neutral callers that do not have location metadata. */
    public static boolean register(UUID televisionId, UUID owner, int maximumPerOwner) {
        if (televisionId == null || owner == null || maximumPerOwner < 1) return false;
        Set<Long> pixels = new HashSet<Long>();
        pixels.add(televisionId.getLeastSignificantBits());
        return register(new Registration(televisionId, owner, "unknown", 0L, 0, 0, 0,
                pixels, "", Validation.SAVED, null), maximumPerOwner);
    }

    public static synchronized boolean register(Registration value, int maximumPerOwner) {
        if (value == null || maximumPerOwner < 1) return false;
        Registration existing = REGISTRATIONS.get(value.id);
        if (existing != null && !sameLocation(existing, value)) return false;
        if ((existing == null || !existing.owner.equals(value.owner))
                && countInternal(value.owner, value.id) >= maximumPerOwner) return false;
        if (overlapsInternal(value, value.id)) return false;
        if (existing != null) value.attached = existing.attached;
        REGISTRATIONS.put(value.id, value);
        return true;
    }

    /** Compatibility restore entry point. */
    public static boolean restore(UUID televisionId, UUID owner) {
        if (televisionId == null || owner == null) return false;
        Set<Long> pixels = new HashSet<Long>();
        pixels.add(televisionId.getLeastSignificantBits());
        return restore(new Registration(televisionId, owner, "unknown", 0L, 0, 0, 0,
                pixels, "", Validation.SAVED, null));
    }

    /** Restores a durable registration even when an owner is above a newly lowered cap. */
    public static synchronized boolean restore(Registration value) {
        if (value == null) return false;
        Registration existing = REGISTRATIONS.get(value.id);
        if (existing != null && !sameLocation(existing, value)) return false;
        if (overlapsInternal(value, value.id)) return false;
        if (existing != null) value.attached = existing.attached;
        REGISTRATIONS.put(value.id, value);
        return true;
    }

    public static boolean unregister(UUID televisionId) { return unregister(televisionId, null); }

    public static boolean unregister(UUID televisionId, String sessionName) {
        Registration removed;
        Listener current;
        synchronized (TelevisionLifecycle.class) {
            removed = televisionId == null ? null : REGISTRATIONS.remove(televisionId);
            if (removed == null) return false;
            current = listener;
        }
        if (removed.removal != null) removed.removal.remove();
        if (current != null) current.removed(removed.id,
                sessionName == null ? removed.sessionName : clean(sessionName));
        return true;
    }

    public static synchronized void session(UUID televisionId, String sessionName) {
        Registration value = REGISTRATIONS.get(televisionId);
        if (value != null) value.sessionName = clean(sessionName);
    }

    public static synchronized void validation(UUID televisionId, Validation validation) {
        Registration value = REGISTRATIONS.get(televisionId);
        if (value != null && validation != null) value.validation = validation;
    }

    public static synchronized void attachment(UUID televisionId, boolean attached) {
        Registration value = REGISTRATIONS.get(televisionId);
        if (value != null) value.attached = attached;
    }

    public static synchronized Registration registration(UUID televisionId) {
        Registration value = REGISTRATIONS.get(televisionId);
        return value == null ? null : value.snapshot();
    }

    public static synchronized List<Registration> registrations() {
        List<Registration> values = new ArrayList<Registration>(REGISTRATIONS.size());
        for (Registration value : REGISTRATIONS.values()) values.add(value.snapshot());
        return Collections.unmodifiableList(values);
    }

    public static synchronized boolean overlaps(String dimension, Set<Long> pixels, UUID excluding) {
        if (dimension == null || pixels == null || pixels.isEmpty()) return false;
        Registration probe = new Registration(excluding == null ? UUID.randomUUID() : excluding,
                UUID.randomUUID(), dimension, 0L, 0, 0, 0, pixels, "", Validation.SAVED, null);
        return overlapsInternal(probe, excluding);
    }

    public static synchronized int count(UUID owner) { return countInternal(owner, null); }
    public static synchronized int count() { return REGISTRATIONS.size(); }

    public static synchronized int attachedTelevisionCount() {
        int count = 0;
        for (Registration value : REGISTRATIONS.values()) if (value.attached) count++;
        return count;
    }

    public static synchronized int attachedSessionCount() {
        Set<String> names = new HashSet<String>();
        for (Registration value : REGISTRATIONS.values()) {
            if (value.attached && !value.sessionName.isEmpty()) names.add(value.sessionName);
        }
        return names.size();
    }

    private static int countInternal(UUID owner, UUID excluding) {
        if (owner == null) return 0;
        int count = 0;
        for (Registration value : REGISTRATIONS.values()) {
            if ((excluding == null || !excluding.equals(value.id)) && owner.equals(value.owner)) count++;
        }
        return count;
    }

    private static boolean overlapsInternal(Registration candidate, UUID excluding) {
        for (Registration value : REGISTRATIONS.values()) {
            if ((excluding == null || !excluding.equals(value.id))
                    && value.dimension.equals(candidate.dimension)
                    && intersects(value.pixels, candidate.pixels)) return true;
        }
        return false;
    }

    private static boolean sameLocation(Registration first, Registration second) {
        return first.owner.equals(second.owner) && first.dimension.equals(second.dimension)
                && first.controllerPos == second.controllerPos;
    }

    private static boolean intersects(Set<Long> first, Set<Long> second) {
        Set<Long> smaller = first.size() <= second.size() ? first : second;
        Set<Long> larger = smaller == first ? second : first;
        for (Long value : smaller) if (larger.contains(value)) return true;
        return false;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private TelevisionLifecycle() {}
}
