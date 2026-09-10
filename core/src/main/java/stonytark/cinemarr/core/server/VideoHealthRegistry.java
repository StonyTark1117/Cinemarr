package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

/** Advisory telemetry owned by each current client/TV stream, with bounded retention. */
public final class VideoHealthRegistry {
    public enum Result { ACCEPTED, IGNORE_STALE, INVALID, AT_CAPACITY }

    public static final class Report {
        private final UUID client;
        private final VideoPackets.ClientHealth value;
        private final long receivedAt;

        private Report(UUID client, VideoPackets.ClientHealth value, long receivedAt) {
            this.client = client; this.value = value; this.receivedAt = receivedAt;
        }
        public UUID client() { return client; }
        public VideoPackets.ClientHealth value() { return value; }
    }

    private final long timeoutMs;
    private final int maximumStreamsPerClient;
    private final Map<UUID, Map<UUID, Report>> reports = new LinkedHashMap<UUID, Map<UUID, Report>>();

    public VideoHealthRegistry(long timeoutMs, int maximumStreamsPerClient) {
        if (timeoutMs < 1 || maximumStreamsPerClient < 1)
            throw new IllegalArgumentException("Positive health retention limits are required");
        this.timeoutMs = timeoutMs; this.maximumStreamsPerClient = maximumStreamsPerClient;
    }

    /** The authority callback checks both timeline/stream generations and current viewer membership. */
    public synchronized Result record(UUID client, VideoPackets.ClientHealth value, long nowMs,
                                      BiPredicate<VideoStreamIdentity, UUID> currentViewer) {
        if (client == null || currentViewer == null) throw new IllegalArgumentException("Health ownership is required");
        VideoHealthPolicy.Decision decision = VideoHealthPolicy.classify(value,
                () -> currentViewer.test(value.identity(), client));
        if (decision == VideoHealthPolicy.Decision.INVALID) return Result.INVALID;
        if (decision == VideoHealthPolicy.Decision.IGNORE_STALE) return Result.IGNORE_STALE;
        Map<UUID, Report> streams = reports.get(client);
        if (streams != null) pruneStreams(streams, nowMs, currentViewer);
        if (streams == null) {
            streams = new LinkedHashMap<UUID, Report>(); reports.put(client, streams);
        }
        UUID stream = value.identity().streamId();
        if (!streams.containsKey(stream) && streams.size() >= maximumStreamsPerClient) return Result.AT_CAPACITY;
        streams.put(stream, new Report(client, value, nowMs));
        return Result.ACCEPTED;
    }

    private void pruneStreams(Map<UUID, Report> streams, long nowMs,
                              BiPredicate<VideoStreamIdentity, UUID> currentViewer) {
        Iterator<Report> iterator = streams.values().iterator();
        while (iterator.hasNext()) {
            Report report = iterator.next();
            if (nowMs - report.receivedAt >= timeoutMs || !currentViewer.test(report.value.identity(), report.client))
                iterator.remove();
        }
    }

    /** Called each server tick, including while no new health reports arrive. */
    public synchronized void prune(long nowMs, BiPredicate<VideoStreamIdentity, UUID> currentViewer) {
        Iterator<Map<UUID, Report>> iterator = reports.values().iterator();
        while (iterator.hasNext()) {
            Map<UUID, Report> streams = iterator.next();
            pruneStreams(streams, nowMs, currentViewer);
            if (streams.isEmpty()) iterator.remove();
        }
    }

    public synchronized void retain(UUID client, Set<VideoStreamIdentity> tracked) {
        Map<UUID, Report> streams = reports.get(client);
        if (streams == null) return;
        Iterator<Report> iterator = streams.values().iterator();
        while (iterator.hasNext()) if (!tracked.contains(iterator.next().value.identity())) iterator.remove();
        if (streams.isEmpty()) reports.remove(client);
    }

    /** Immutable reports in a detached snapshot; callers may aggregate or identify each stream. */
    public synchronized List<Report> currentReports(long nowMs, BiPredicate<VideoStreamIdentity, UUID> currentViewer) {
        prune(nowMs, currentViewer);
        List<Report> result = new ArrayList<Report>();
        for (Map<UUID, Report> streams : reports.values()) result.addAll(streams.values());
        return result;
    }

    public synchronized void remove(UUID client) { reports.remove(client); }
    public synchronized void clear() { reports.clear(); }
    public synchronized int size() {
        int count = 0; for (Map<UUID, Report> streams : reports.values()) count += streams.size(); return count;
    }
    public synchronized int clients() { return reports.size(); }
}
