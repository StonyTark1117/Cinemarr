package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.video.RenditionPolicy;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.protocol.VideoPackets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One immutable manifest and bounded segment cache, shared by every server adapter. */
public final class ActiveVideoMedia {
    private final PlexVideoService plex;
    private final PlexVideoService.VideoSession session;
    private final PlexVideoService.MediaPlaylist playlist;
    private final List<SegmentReference> segments;
    public final RenditionPolicy.Dimensions dimensions;
    public final long durationMs;
    public final List<VideoStreamOption> options;
    public final int audioId, subtitleId;
    private volatile long fetchRetries, fetchFailures;
    private final BoundedByteCache<Integer, SegmentData> cache =
            new BoundedByteCache<>(16, 64L * 1024L * 1024L, value -> value.bytes.length);

    public ActiveVideoMedia(PlexVideoService plex, PlexVideoService.VideoSession session,
                            PlexVideoService.MediaPlaylist playlist, List<SegmentReference> segments,
                            RenditionPolicy.Dimensions dimensions, long durationMs,
                            List<VideoStreamOption> options, int audioId, int subtitleId) {
        this.plex = plex;
        this.session = session;
        this.playlist = playlist;
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.dimensions = dimensions;
        this.durationMs = durationMs;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.audioId = audioId;
        this.subtitleId = subtitleId;
    }

    // Downloads serialize on this instance. Server-thread metadata reads must
    // never acquire that monitor across Plex I/O or materialization retries.
    // The cache has its own short-held monitor; it never performs network I/O.
    public int segmentCount() { return segments.size(); }
    public int cachedSegments() { return cache.size(); }
    public long cachedBytes() { return cache.retainedBytes(); }
    public long fetchRetries() { return fetchRetries; }
    public long fetchFailures() { return fetchFailures; }
    public long presentationTime(int index) { return segments.get(index).pts; }

    public int segmentAt(long position) {
        int first = 0;
        for (int index = 0; index < segments.size(); index++) {
            if (segments.get(index).pts > position) break;
            first = index;
        }
        return first;
    }

    public List<VideoPackets.SegmentDescriptor> descriptors(int first, int maximum) {
        if (first < 0 || first >= segments.size() || maximum <= 0) return Collections.emptyList();
        int end = first + Math.min(segments.size() - first, maximum);
        List<VideoPackets.SegmentDescriptor> values = new ArrayList<>();
        for (int index = first; index < end; index++) {
            SegmentReference reference = segments.get(index);
            values.add(new VideoPackets.SegmentDescriptor(index, reference.pts, reference.duration, true, 0, ""));
        }
        return values;
    }

    public synchronized SegmentData segment(int index) throws IOException {
        SegmentData value = cache.get(index);
        if (value != null) return value;
        SegmentReference reference = segments.get(index);
        try {
            byte[] bytes = PlexSegmentFetch.fetch(() -> plex.fetch(session, playlist, reference.source),
                    delayMs -> fetchRetries++);
            value = new SegmentData(reference, bytes);
            cache.put(index, value);
            return value;
        } catch (IOException failure) {
            fetchFailures++;
            throw failure;
        }
    }

    public static final class SegmentReference {
        private final HlsPlaylist.MediaSegment source;
        public final String uri;
        public final long pts, duration;

        public SegmentReference(HlsPlaylist.MediaSegment source) {
            this.source = source;
            this.uri = source.uri();
            this.pts = source.presentationTimeMs();
            this.duration = source.durationMs();
        }
    }

    public static final class SegmentData {
        public final SegmentReference reference;
        public final byte[] bytes;
        public final String sha;

        private SegmentData(SegmentReference reference, byte[] bytes) {
            this.reference = reference;
            this.bytes = bytes;
            this.sha = Hashing.sha256(bytes);
        }
    }
}
