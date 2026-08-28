package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.library.VideoMediaItem;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Authoritative timelines and viewer lifecycle for independent TVs and named watch parties. */
public final class VideoSessionCoordinator implements AutoCloseable {
    public interface MediaFactory { MediaHandle start(UUID sessionId, long generation, VideoMediaItem item, long offsetMs) throws IOException; }
    public interface MediaHandle extends AutoCloseable { @Override void close() throws IOException; }

    private final int maximumStreams;
    private final long inactiveGraceMs;
    private final MediaFactory mediaFactory;
    private final Map<String, Session> sessions = new LinkedHashMap<String, Session>();
    private final Map<UUID, String> televisionSessions = new LinkedHashMap<UUID, String>();

    public VideoSessionCoordinator(int maximumStreams, long inactiveGraceMs, MediaFactory mediaFactory) {
        if (maximumStreams < 1 || inactiveGraceMs < 0 || mediaFactory == null) throw new IllegalArgumentException("Invalid video session policy");
        this.maximumStreams = maximumStreams;
        this.inactiveGraceMs = inactiveGraceMs;
        this.mediaFactory = mediaFactory;
    }

    public synchronized Snapshot tune(UUID televisionId, String requestedName) {
        if (televisionId == null) throw new IllegalArgumentException("televisionId");
        String name = requestedName == null || requestedName.trim().isEmpty() ? "tv-" + televisionId : requestedName.trim();
        String previous = televisionSessions.get(televisionId);
        if (name.equals(previous)) return sessions.get(name).snapshot();
        Session target = sessions.get(name);
        if (target == null) {
            target = new Session(UUID.randomUUID(), name);
            sessions.put(name, target);
        }
        if (previous != null) detachTelevision(televisionId, previous);
        target.televisions.add(televisionId);
        televisionSessions.put(televisionId, name);
        return target.snapshot();
    }

    public synchronized Snapshot play(String name, VideoMediaItem item, long offsetMs, long nowMs) throws IOException {
        return play(name, item, offsetMs, nowMs, -1);
    }

    public synchronized Snapshot play(String name, VideoMediaItem item, long offsetMs, long nowMs,
                                      long expectedGeneration) throws IOException {
        Session session = required(name);
        if (expectedGeneration >= 0 && session.generation != expectedGeneration) {
            throw new IllegalStateException("TV state changed while preparing playback");
        }
        if (session.media == null && activeStreamCountInternal() >= maximumStreams) {
            throw new IllegalStateException("Maximum concurrent Plex streams reached");
        }
        long nextGeneration = session.generation + 1;
        MediaHandle replacement = mediaFactory.start(session.id, nextGeneration, item, Math.max(0, offsetMs));
        try {
            close(session.media);
        } catch (IOException failure) {
            closeUnchecked(replacement);
            throw failure;
        }
        session.generation = nextGeneration;
        session.media = replacement;
        session.item = item;
        session.positionAtStartMs = Math.max(0, offsetMs);
        session.startedAtMs = nowMs;
        session.pausedAtMs = -1;
        session.suspendedAtMs = -1;
        session.noViewersSinceMs = session.viewers.isEmpty() ? nowMs : -1;
        return session.snapshotAt(nowMs);
    }

    public synchronized void viewerEntered(String name, UUID playerId) {
        Session session = required(name);
        session.viewers.add(playerId);
        session.noViewersSinceMs = -1;
    }

    public synchronized void viewerLeft(String name, UUID playerId, long nowMs) {
        // Tracking snapshots can briefly retain a session name after its last
        // television has been removed.  The session is already fully detached
        // in that case, so the delayed viewer-leave notification is a no-op.
        Session session = sessions.get(name);
        if (session == null) return;
        session.viewers.remove(playerId);
        if (session.viewers.isEmpty() && session.media != null && session.noViewersSinceMs < 0) session.noViewersSinceMs = nowMs;
    }

    public synchronized void pause(String name, long nowMs) throws IOException {
        Session value = required(name);
        if (value.pausedAtMs >= 0) return;
        Snapshot frozen = value.snapshotAt(nowMs);
        close(value.media);
        value.media = null;
        value.positionAtStartMs = frozen.positionMs();
        value.startedAtMs = nowMs;
        value.pausedAtMs = nowMs;
        value.suspendedAtMs = nowMs;
        value.noViewersSinceMs = -1;
        value.generation++;
    }
    public synchronized void resume(String name, long nowMs) {
        Session value = required(name);
        if (value.pausedAtMs >= 0) {
            value.startedAtMs += Math.max(0, nowMs - value.pausedAtMs);
            value.pausedAtMs = -1;
            value.suspendedAtMs = -1;
        }
    }
    public synchronized void seek(String name, long positionMs, long nowMs) throws IOException {
        seek(name, positionMs, nowMs, -1);
    }
    public synchronized void seek(String name, long positionMs, long nowMs, long expectedGeneration) throws IOException {
        Session value = required(name);
        if (value.item == null) return;
        play(name, value.item, Math.max(0, positionMs), nowMs, expectedGeneration);
    }

    /** Restores durable playback metadata without contacting Plex until a viewer arrives. */
    public synchronized Snapshot restore(String name, VideoMediaItem item, long positionMs, boolean paused, long nowMs) {
        Session value = required(name);
        if (value.media != null) throw new IllegalStateException("Cannot restore over active playback");
        value.item = item;
        value.positionAtStartMs = Math.max(0, positionMs);
        value.startedAtMs = nowMs;
        value.pausedAtMs = paused ? nowMs : -1;
        value.suspendedAtMs = nowMs;
        return value.snapshotAt(nowMs);
    }

    /** Restarts a restored or inactivity-suspended session at its frozen checkpoint. */
    public synchronized Snapshot restart(String name, long nowMs, long expectedGeneration) throws IOException {
        Session value = required(name);
        if (value.media != null || value.item == null) return value.snapshotAt(nowMs);
        boolean paused = value.pausedAtMs >= 0;
        long position = value.snapshotAt(nowMs).positionMs();
        Snapshot restarted = play(name, value.item, position, nowMs, expectedGeneration);
        if (paused) { pause(name, nowMs); return required(name).snapshotAt(nowMs); }
        return restarted;
    }

    public synchronized Snapshot stop(String name,long nowMs)throws IOException{
        Session value=required(name);close(value.media);value.media=null;value.item=null;value.positionAtStartMs=0;value.startedAtMs=nowMs;value.pausedAtMs=-1;value.suspendedAtMs=-1;value.noViewersSinceMs=-1;value.generation++;return value.snapshotAt(nowMs);
    }

    public synchronized void tick(long nowMs) throws IOException {
        for (Session session : sessions.values()) {
            if (session.media != null && session.viewers.isEmpty() && session.noViewersSinceMs >= 0
                    && nowMs - session.noViewersSinceMs >= inactiveGraceMs) {
                close(session.media);
                session.media = null;
                Snapshot frozen = session.snapshotAt(nowMs);
                session.positionAtStartMs = frozen.positionMs();
                session.startedAtMs = nowMs;
                session.suspendedAtMs = nowMs;
                session.generation++;
            }
        }
    }

    public synchronized Snapshot snapshot(String name, long nowMs) { return required(name).snapshotAt(nowMs); }
    public synchronized Snapshot snapshotIfPresent(String name, long nowMs) {
        Session value = sessions.get(name); return value == null ? null : value.snapshotAt(nowMs);
    }
    public synchronized Snapshot snapshotIfPresent(UUID sessionId, long generation, long nowMs) {
        if (sessionId == null) return null;
        for (Session value : sessions.values()) if (value.id.equals(sessionId) && value.generation == generation) {
            return value.snapshotAt(nowMs);
        }
        return null;
    }
    public synchronized boolean isViewer(UUID sessionId, long generation, UUID playerId) {
        if (sessionId == null || playerId == null) return false;
        for (Session value : sessions.values()) if (value.id.equals(sessionId) && value.generation == generation) {
            return value.viewers.contains(playerId);
        }
        return false;
    }
    public synchronized int sessionCount() { return sessions.size(); }
    public synchronized int activeStreamCount() { return activeStreamCountInternal(); }
    public synchronized Set<String> sessionNames(){return Collections.unmodifiableSet(new HashSet<String>(sessions.keySet()));}

    public synchronized void untune(UUID televisionId) throws IOException {
        String name = televisionSessions.remove(televisionId);
        if (name != null) detachTelevision(televisionId, name);
    }

    private int activeStreamCountInternal() {
        int count = 0;
        for (Session session : sessions.values()) if (session.media != null) count++;
        return count;
    }

    private void detachTelevision(UUID televisionId, String name) {
        Session previous = sessions.get(name);
        if (previous == null) return;
        previous.televisions.remove(televisionId);
        if (previous.televisions.isEmpty()) {
            closeUnchecked(previous.media);
            sessions.remove(name);
        }
    }
    private Session required(String name) {
        Session value = sessions.get(name);
        if (value == null) throw new IllegalArgumentException("Unknown TV session");
        return value;
    }
    @Override public synchronized void close() throws IOException {
        IOException failure = null;
        for (Session session : sessions.values()) try { close(session.media); } catch (IOException error) { failure = error; }
        sessions.clear(); televisionSessions.clear();
        if (failure != null) throw failure;
    }
    private static void close(MediaHandle handle) throws IOException { if (handle != null) handle.close(); }
    private static void closeUnchecked(MediaHandle handle) { try { close(handle); } catch (IOException ignored) {} }

    private static final class Session {
        private final UUID id;
        private final String name;
        private final Set<UUID> televisions = new HashSet<UUID>();
        private final Set<UUID> viewers = new HashSet<UUID>();
        private long generation;
        private MediaHandle media;
        private VideoMediaItem item;
        private long positionAtStartMs;
        private long startedAtMs;
        private long pausedAtMs = -1;
        private long suspendedAtMs = -1;
        private long noViewersSinceMs = -1;
        private Session(UUID id, String name) { this.id = id; this.name = name; }
        private Snapshot snapshot() { return snapshotAt(startedAtMs); }
        private Snapshot snapshotAt(long nowMs) {
            long clock = pausedAtMs >= 0 ? pausedAtMs : suspendedAtMs >= 0 ? suspendedAtMs : nowMs;
            long position = item == null ? 0 : positionAtStartMs + Math.max(0, clock - startedAtMs);
            if (item != null && item.durationMs() > 0) position = Math.min(position, item.durationMs());
            return new Snapshot(id, name, generation, item, position, pausedAtMs >= 0, media != null,
                    televisions, viewers);
        }
    }

    public static final class Snapshot {
        private final UUID id; private final String name; private final long generation; private final VideoMediaItem item;
        private final long positionMs; private final boolean paused; private final boolean transcoding;
        private final Set<UUID> televisions; private final Set<UUID> viewers;
        Snapshot(UUID id, String name, long generation, VideoMediaItem item, long positionMs, boolean paused,
                 boolean transcoding, Set<UUID> televisions, Set<UUID> viewers) {
            this.id = id; this.name = name; this.generation = generation; this.item = item; this.positionMs = positionMs;
            this.paused = paused; this.transcoding = transcoding;
            this.televisions = Collections.unmodifiableSet(new HashSet<UUID>(televisions));
            this.viewers = Collections.unmodifiableSet(new HashSet<UUID>(viewers));
        }
        public UUID id() { return id; } public String name() { return name; } public long generation() { return generation; }
        public VideoMediaItem item() { return item; } public long positionMs() { return positionMs; }
        public boolean paused() { return paused; } public boolean transcoding() { return transcoding; }
        public Set<UUID> televisions() { return televisions; } public Set<UUID> viewers() { return viewers; }
    }
}
