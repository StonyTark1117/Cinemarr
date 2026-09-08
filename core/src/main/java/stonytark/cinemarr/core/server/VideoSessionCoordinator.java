package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.library.VideoMediaItem;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Authoritative timelines and viewer lifecycle for independent TVs and named watch parties. */
public final class VideoSessionCoordinator implements AutoCloseable {
    public interface MediaFactory { MediaHandle start(UUID sessionId, long generation, VideoMediaItem item, long offsetMs) throws IOException; }
    public interface MediaHandle extends AutoCloseable { @Override void close() throws IOException; }

    private final int maximumStreams;
    private final long inactiveGraceMs;
    private final MediaFactory mediaFactory;
    private final Map<String, Session> sessions = new LinkedHashMap<String, Session>();
    private final Map<UUID, String> televisionSessions = new LinkedHashMap<UUID, String>();
    private final Set<StartAttempt> starting = new HashSet<>();
    private final Set<RetiredMedia> retiring = new HashSet<>();
    private final int maximumOwnedMedia;
    private final ThreadPoolExecutor closer;
    private boolean closed;
    private long closeFailures;
    private IOException closeFailure, unreportedCloseFailure;

    public VideoSessionCoordinator(int maximumStreams, long inactiveGraceMs, MediaFactory mediaFactory) {
        this(maximumStreams, inactiveGraceMs, mediaFactory, false);
    }

    /** Production adapters use bounded asynchronous retirement; direct mode is useful for synchronous embedders. */
    public VideoSessionCoordinator(int maximumStreams, long inactiveGraceMs, MediaFactory mediaFactory,
                                   boolean asynchronousRetirement) {
        if (maximumStreams < 1 || maximumStreams > Integer.MAX_VALUE / 2 || inactiveGraceMs < 0
                || mediaFactory == null) throw new IllegalArgumentException("Invalid video session policy");
        this.maximumStreams = maximumStreams;
        this.inactiveGraceMs = inactiveGraceMs;
        this.mediaFactory = mediaFactory;
        this.maximumOwnedMedia = maximumStreams * 2;
        closer = asynchronousRetirement ? new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(maximumOwnedMedia), runnable -> {
                    Thread thread = new Thread(runnable, "Cinemarr media cleanup");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy()) : null;
    }

    public Snapshot tune(UUID televisionId, String requestedName) {
        if (televisionId == null) throw new IllegalArgumentException("televisionId");
        String name = requestedName == null || requestedName.trim().isEmpty() ? "tv-" + televisionId : requestedName.trim();
        RetiredMedia detached = null;
        Snapshot result;
        synchronized (this) {
        requireOpen();
        String previous = televisionSessions.get(televisionId);
        if (name.equals(previous)) return sessions.get(name).snapshot();
        Session target = sessions.get(name);
        if (target == null) {
            target = new Session(UUID.randomUUID(), name);
            sessions.put(name, target);
        }
        if (previous != null) detached = detachTelevision(televisionId, previous);
        target.televisions.add(televisionId);
        televisionSessions.put(televisionId, name);
        result = target.snapshot();
        }
        retireUnchecked(detached);
        return result;
    }

    public Snapshot play(String name, VideoMediaItem item, long offsetMs, long nowMs) throws IOException {
        return play(name, item, offsetMs, nowMs, -1);
    }

    public Snapshot play(String name, VideoMediaItem item, long offsetMs, long nowMs,
                                      long expectedGeneration) throws IOException {
        if (item == null) throw new IllegalArgumentException("item");
        final StartAttempt attempt;
        synchronized (this) {
            requireOpen();
            Session session = required(name);
            if (expectedGeneration >= 0 && session.generation != expectedGeneration) throw staleStart();
            if (session.pending != null) throw new IllegalStateException("TV playback is already being prepared");
            Set<Session> occupied = new HashSet<>();
            for (Session value : sessions.values()) if (value.media != null || value.pending != null) occupied.add(value);
            if (!occupied.contains(session) && occupied.size() >= maximumStreams) {
                throw new IllegalStateException("Maximum concurrent Plex streams reached");
            }
            if (activeStreamCountInternal() + starting.size() + retiring.size() >= maximumOwnedMedia) {
                throw new IllegalStateException("Plex media cleanup is busy; retry playback shortly");
            }
            attempt = new StartAttempt(session, session.generation, ++session.sequence);
            session.pending = attempt;
            starting.add(attempt);
        }
        MediaHandle replacement = null;
        RetiredMedia previous = null;
        Snapshot result;
        try {
            replacement = mediaFactory.start(attempt.session.id, attempt.generation, item, Math.max(0, offsetMs));
            if (replacement == null) throw new IOException("Plex media factory returned no handle");
            synchronized (this) {
                Session session = attempt.session;
                if (closed || sessions.get(name) != session || session.pending != attempt
                        || session.generation != attempt.expectedGeneration) throw staleStart();
                previous = detachMedia(session);
                session.generation = attempt.generation;
                session.playbackGeneration = attempt.generation;
                session.media = replacement;
                replacement = null;
                session.item = item;
                session.positionAtStartMs = Math.max(0, offsetMs);
                session.startedAtMs = nowMs;
                session.pausedAtMs = -1;
                session.suspendedAtMs = -1;
                session.noViewersSinceMs = session.viewers.isEmpty() ? nowMs : -1;
                result = session.snapshotAt(nowMs);
            }
        } finally {
            RetiredMedia rejected;
            synchronized (this) {
                rejected = registerRetirement(replacement);
                if (attempt.session.pending == attempt) attempt.session.pending = null;
                starting.remove(attempt);
                finishCleanupIfClosed();
                notifyAll();
            }
            retireUnchecked(rejected);
        }
        retire(previous);
        return result;
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

    public void pause(String name, long nowMs) throws IOException {
        RetiredMedia detached;
        synchronized (this) {
        Session value = required(name);
        if (value.pausedAtMs >= 0 && value.pending == null) return;
        Snapshot frozen = value.snapshotAt(nowMs);
        detached = detachMedia(value);
        value.positionAtStartMs = frozen.positionMs();
        value.startedAtMs = nowMs;
        value.pausedAtMs = nowMs;
        value.suspendedAtMs = nowMs;
        value.noViewersSinceMs = -1;
        invalidate(value);
        }
        retire(detached);
    }
    public synchronized void resume(String name, long nowMs) {
        Session value = required(name);
        if (value.pausedAtMs >= 0) {
            value.startedAtMs += Math.max(0, nowMs - value.pausedAtMs);
            value.pausedAtMs = -1;
            value.suspendedAtMs = -1;
        }
    }
    public Snapshot seek(String name, long positionMs, long nowMs) throws IOException {
        return seek(name, positionMs, nowMs, -1);
    }
    public Snapshot seek(String name, long positionMs, long nowMs, long expectedGeneration) throws IOException {
        VideoMediaItem item;
        synchronized (this) {
            requireOpen();
            Session value = required(name);
            if (value.item == null) return value.snapshotAt(nowMs);
            if (expectedGeneration >= 0 && value.generation != expectedGeneration) throw staleStart();
            expectedGeneration = value.generation;
            item = value.item;
            // Paused playback owns no transcode. Moving its cursor must not
            // start media or silently resume it; resume will start at this cursor.
            if (value.pausedAtMs >= 0) {
                invalidate(value);
                value.positionAtStartMs = Math.max(0, positionMs);
                value.startedAtMs = nowMs;
                value.pausedAtMs = nowMs;
                value.suspendedAtMs = nowMs;
                return value.snapshotAt(nowMs);
            }
        }
        return play(name, item, Math.max(0, positionMs), nowMs, expectedGeneration);
    }

    /** Restores durable playback metadata without contacting Plex until a viewer arrives. */
    public synchronized Snapshot restore(String name, VideoMediaItem item, long positionMs, boolean paused, long nowMs) {
        Session value = required(name);
        if (value.media != null || value.pending != null) throw new IllegalStateException("Cannot restore over active playback");
        value.item = item;
        value.playbackGeneration = value.generation;
        value.positionAtStartMs = Math.max(0, positionMs);
        value.startedAtMs = nowMs;
        value.pausedAtMs = paused ? nowMs : -1;
        value.suspendedAtMs = nowMs;
        return value.snapshotAt(nowMs);
    }

    /** Replaces stream configuration at the live cursor, retaining pause without media I/O. */
    public Snapshot reconfigure(String name, long nowMs, long expectedGeneration) throws IOException {
        long position;
        synchronized (this) {
            requireOpen();
            Session value = required(name);
            if (expectedGeneration >= 0 && value.generation != expectedGeneration) throw staleStart();
            if (value.item == null) throw new IllegalStateException("No video is selected");
            expectedGeneration = value.generation;
            position = value.snapshotAt(nowMs).positionMs();
        }
        return seek(name, position, nowMs, expectedGeneration);
    }

    /** Restarts a restored or inactivity-suspended session at its frozen checkpoint. */
    public Snapshot restart(String name, long nowMs, long expectedGeneration) throws IOException {
        VideoMediaItem item;
        long position;
        synchronized (this) {
            Session value = required(name);
            if (expectedGeneration >= 0 && value.generation != expectedGeneration) throw staleStart();
            // A paused restored session stays dormant until explicitly resumed.
            if (value.media != null || value.item == null || value.pausedAtMs >= 0) return value.snapshotAt(nowMs);
            expectedGeneration = value.generation;
            position = value.snapshotAt(nowMs).positionMs();
            item = value.item;
        }
        return play(name, item, position, nowMs, expectedGeneration);
    }

    public Snapshot stop(String name, long nowMs) throws IOException {
        RetiredMedia detached;
        Snapshot result;
        synchronized (this) {
            Session value = required(name);
            detached = detachMedia(value);
            invalidate(value);
            value.item = null;
            value.positionAtStartMs = 0;
            value.startedAtMs = nowMs;
            value.pausedAtMs = -1;
            value.suspendedAtMs = -1;
            value.noViewersSinceMs = -1;
            result = value.snapshotAt(nowMs);
        }
        retire(detached);
        return result;
    }

    public void tick(long nowMs) throws IOException {
        List<RetiredMedia> detached = new ArrayList<>();
        IOException failure;
        synchronized (this) {
        for (Session session : sessions.values()) {
            if (session.media != null && session.viewers.isEmpty() && session.noViewersSinceMs >= 0
                    && nowMs - session.noViewersSinceMs >= inactiveGraceMs) {
                detached.add(suspendInternal(session, nowMs));
            }
        }
        failure = unreportedCloseFailure;
        unreportedCloseFailure = null;
        }
        retireAll(detached);
        if (failure != null) throw failure;
    }

    /** Immediately freezes playback and releases its media handle without marking it user-paused. */
    public Snapshot suspend(String name, long nowMs) throws IOException {
        RetiredMedia detached;
        Snapshot result;
        synchronized (this) {
            Session value = required(name);
            detached = suspendInternal(value, nowMs);
            result = value.snapshotAt(nowMs);
        }
        retire(detached);
        return result;
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
    /** In-flight traffic from an older revision is not an ownership error.
     * This never grants media access; callers still validate packet bounds. */
    public synchronized boolean isSupersededViewer(UUID sessionId, long generation, UUID playerId) {
        if (closed || sessionId == null || playerId == null || generation < 0) return false;
        for (Session value : sessions.values()) if (value.id.equals(sessionId)) {
            return generation < value.generation && value.viewers.contains(playerId);
        }
        return false;
    }
    public synchronized int sessionCount() { return sessions.size(); }
    public synchronized int activeStreamCount() { return activeStreamCountInternal(); }
    public synchronized Set<String> sessionNames(){return Collections.unmodifiableSet(new HashSet<String>(sessions.keySet()));}

    public void untune(UUID televisionId) throws IOException {
        RetiredMedia detached;
        synchronized (this) {
        String name = televisionSessions.remove(televisionId);
        if (name == null) return;
        detached = detachTelevision(televisionId, name);
        }
        retire(detached);
    }

    private int activeStreamCountInternal() {
        int count = 0;
        for (Session session : sessions.values()) if (session.media != null) count++;
        return count;
    }

    private RetiredMedia detachTelevision(UUID televisionId, String name) {
        Session previous = sessions.get(name);
        if (previous == null) return null;
        previous.televisions.remove(televisionId);
        if (previous.televisions.isEmpty()) {
            invalidate(previous);
            sessions.remove(name);
            return detachMedia(previous);
        }
        return null;
    }
    private RetiredMedia suspendInternal(Session value, long nowMs) {
        if (value.media == null && value.pending == null) return null;
        Snapshot frozen = value.snapshotAt(nowMs);
        RetiredMedia detached = detachMedia(value);
        value.positionAtStartMs = frozen.positionMs();
        value.startedAtMs = nowMs;
        value.suspendedAtMs = nowMs;
        value.noViewersSinceMs = -1;
        invalidate(value);
        return detached;
    }
    private Session required(String name) {
        Session value = sessions.get(name);
        if (value == null) throw new IllegalArgumentException("Unknown TV session");
        return value;
    }
    public synchronized int pendingStarts() { return starting.size(); }
    public synchronized int retiringMedia() { return retiring.size(); }
    public synchronized long closeFailures() { return closeFailures; }

    /** Linearizes a short metadata-only completion with generation changes. Never perform I/O in action. */
    public synchronized boolean applyIfCurrent(Snapshot expected, Runnable action) {
        if (closed || expected == null || snapshotIfPresent(expected.id(), expected.generation(), 0) == null) return false;
        action.run();
        return true;
    }

    @Override public void close() throws IOException {
        List<RetiredMedia> detached = new ArrayList<>();
        List<Thread> starters = new ArrayList<>();
        synchronized (this) {
            closed = true;
            for (Session session : sessions.values()) {
                invalidate(session);
                detached.add(detachMedia(session));
            }
            sessions.clear();
            televisionSessions.clear();
            for (StartAttempt attempt : starting) starters.add(attempt.thread);
            finishCleanupIfClosed();
        }
        for (Thread thread : starters) if (thread != Thread.currentThread()) thread.interrupt();
        for (RetiredMedia value : detached) retireUnchecked(value);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try {
            synchronized (this) {
                while (!starting.isEmpty() || !retiring.isEmpty()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new IOException("Timed out draining Plex media lifecycle");
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                }
            }
            if (closer != null && !closer.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IOException("Plex media cleanup executor did not stop");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while draining Plex media lifecycle", interrupted);
        }
        synchronized (this) { if (closeFailure != null) throw closeFailure; }
    }

    private void requireOpen() { if (closed) throw new IllegalStateException("Video session coordinator is closed"); }
    private static IllegalStateException staleStart() {
        return new IllegalStateException("TV state changed while preparing playback");
    }
    private static void invalidate(Session session) {
        session.generation = ++session.sequence;
        session.pending = null;
    }
    private RetiredMedia detachMedia(Session session) {
        RetiredMedia value = registerRetirement(session.media);
        session.media = null;
        return value;
    }
    private RetiredMedia registerRetirement(MediaHandle handle) {
        if (handle == null) return null;
        RetiredMedia value = new RetiredMedia(handle);
        retiring.add(value);
        return value;
    }
    private void retireAll(List<RetiredMedia> values) throws IOException {
        IOException failure = null;
        for (RetiredMedia value : values) try { retire(value); } catch (IOException error) { failure = error; }
        if (failure != null) throw failure;
    }
    private void retireUnchecked(RetiredMedia value) {
        try { retire(value); } catch (IOException recorded) { /* surfaced by tick/close and diagnostics */ }
    }
    private void retire(RetiredMedia value) throws IOException {
        if (value == null) return;
        // Every enqueued close owns one of maximumOwnedMedia leases. New starts
        // are refused before capacity is exhausted, so cleanup cannot be dropped
        // or run on the game thread by a caller-runs rejection policy.
        if (closer != null) closer.execute(() -> finishRetirement(value));
        else {
            IOException failure = finishRetirement(value);
            if (failure != null) throw failure;
        }
    }
    private IOException finishRetirement(RetiredMedia value) {
        IOException failure = null;
        try { value.handle.close(); }
        catch (IOException error) { failure = error; }
        catch (RuntimeException error) { failure = new IOException("Plex media cleanup failed", error); }
        finally {
            synchronized (this) {
                if (failure != null) { closeFailures++; closeFailure = failure; unreportedCloseFailure = failure; }
                retiring.remove(value);
                finishCleanupIfClosed();
                notifyAll();
            }
        }
        return failure;
    }
    private void finishCleanupIfClosed() {
        if (closed && starting.isEmpty() && retiring.isEmpty() && closer != null) closer.shutdown();
    }
    private static final class RetiredMedia {
        final MediaHandle handle;
        RetiredMedia(MediaHandle handle) { this.handle = handle; }
    }
    private static final class StartAttempt {
        final Session session;
        final long expectedGeneration, generation;
        final Thread thread = Thread.currentThread();
        StartAttempt(Session session, long expectedGeneration, long generation) {
            this.session = session; this.expectedGeneration = expectedGeneration; this.generation = generation;
        }
    }

    private static final class Session {
        private final UUID id;
        private final String name;
        private final Set<UUID> televisions = new HashSet<UUID>();
        private final Set<UUID> viewers = new HashSet<UUID>();
        private long generation, sequence, playbackGeneration;
        private StartAttempt pending;
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
            return new Snapshot(id, name, generation, playbackGeneration, item, position, nowMs, pausedAtMs >= 0, media != null,
                    televisions, viewers);
        }
    }

    public static final class Snapshot {
        private final UUID id; private final String name; private final long generation, playbackGeneration; private final VideoMediaItem item;
        private final long positionMs; private final long serverEpochMs; private final boolean paused; private final boolean transcoding;
        private final Set<UUID> televisions; private final Set<UUID> viewers;
        Snapshot(UUID id, String name, long generation, long playbackGeneration, VideoMediaItem item, long positionMs, long serverEpochMs, boolean paused,
                 boolean transcoding, Set<UUID> televisions, Set<UUID> viewers) {
            this.id = id; this.name = name; this.generation = generation; this.item = item; this.positionMs = positionMs;
            this.playbackGeneration = playbackGeneration;
            this.serverEpochMs = serverEpochMs;
            this.paused = paused; this.transcoding = transcoding;
            this.televisions = Collections.unmodifiableSet(new HashSet<UUID>(televisions));
            this.viewers = Collections.unmodifiableSet(new HashSet<UUID>(viewers));
        }
        public UUID id() { return id; } public String name() { return name; } public long generation() { return generation; }
        /** Changes on media replacement, not on pause/suspend; prevents checkpointing a new item with old stream options. */
        public long playbackGeneration() { return playbackGeneration; }
        public VideoMediaItem item() { return item; } public long positionMs() { return positionMs; }
        public long serverEpochMs() { return serverEpochMs; }
        public boolean paused() { return paused; } public boolean transcoding() { return transcoding; }
        public Set<UUID> televisions() { return televisions; } public Set<UUID> viewers() { return viewers; }
    }
}
