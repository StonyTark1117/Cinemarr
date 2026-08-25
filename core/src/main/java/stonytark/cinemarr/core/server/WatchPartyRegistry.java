package stonytark.cinemarr.core.server;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Maps many TVs onto one transcode/timeline per named playback session. */
public final class WatchPartyRegistry {
    private final int maximumActiveSessions;
    private final Map<String, Session> sessions = new LinkedHashMap<String, Session>();

    public WatchPartyRegistry(int maximumActiveSessions) {
        if (maximumActiveSessions < 1) throw new IllegalArgumentException("At least one session is required");
        this.maximumActiveSessions = maximumActiveSessions;
    }

    public synchronized Session tune(String name, UUID televisionId) {
        if (name == null || name.trim().isEmpty() || televisionId == null) throw new IllegalArgumentException("Session and TV are required");
        String canonical = name.trim();
        Session current = sessions.get(canonical);
        if (current == null) {
            if (sessions.size() >= maximumActiveSessions) throw new IllegalStateException("Maximum active TV sessions reached");
            current = new Session(UUID.randomUUID(), canonical);
            sessions.put(canonical, current);
        }
        current.televisions.add(televisionId);
        return current.snapshot();
    }

    public synchronized boolean untune(String name, UUID televisionId) {
        Session current = sessions.get(name);
        if (current == null || !current.televisions.remove(televisionId)) return false;
        if (current.televisions.isEmpty()) sessions.remove(name);
        return true;
    }

    public synchronized int activeSessions() { return sessions.size(); }

    public static final class Session {
        private final UUID id;
        private final String name;
        private final Set<UUID> televisions = new LinkedHashSet<UUID>();
        private Session(UUID id, String name) { this.id = id; this.name = name; }
        private Session snapshot() {
            Session value = new Session(id, name);
            value.televisions.addAll(televisions);
            return value;
        }
        public UUID id() { return id; }
        public String name() { return name; }
        public Set<UUID> televisions() { return Collections.unmodifiableSet(televisions); }
    }
}
