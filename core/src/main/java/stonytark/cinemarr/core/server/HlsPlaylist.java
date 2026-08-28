package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, loader-independent helpers for parsing HLS media playlists. */
public final class HlsPlaylist {
    private static final String EXTINF = "#EXTINF:";

    private HlsPlaylist() {}

    /**
     * Parses the duration from an EXTINF tag. The optional text after the
     * comma is a title, not part of the numeric duration.
     */
    public static long durationMillis(String line) {
        if (line == null || !line.startsWith(EXTINF)) {
            throw new IllegalArgumentException("Expected an HLS EXTINF tag");
        }
        String value = line.substring(EXTINF.length());
        int title = value.indexOf(',');
        if (title >= 0) value = value.substring(0, title);
        try {
            double seconds = Double.parseDouble(value.trim());
            if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
                throw new NumberFormatException("non-finite or negative duration");
            }
            return (long) (seconds * 1_000.0);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("HLS EXTINF tag has an invalid duration", invalid);
        }
    }

    /**
     * Returns the playable suffix of a Plex HLS playlist for a transcode
     * started at {@code offsetMs}. Plex retains pre-seek entries in the
     * playlist but serves those URLs as one-packet placeholders.
     */
    public static List<MediaSegment> mediaSegments(String playlist, long offsetMs) {
        if (playlist == null) throw new IllegalArgumentException("HLS playlist is required");
        long offset = Math.max(0, offsetMs), timeline = 0, presentation = offset, duration = 0;
        List<MediaSegment> segments = new ArrayList<MediaSegment>();
        for (String line : playlist.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith(EXTINF)) {
                duration = durationMillis(value);
            } else if (!value.isEmpty() && !value.startsWith("#")) {
                long end = timeline + duration;
                if (end > offset) {
                    segments.add(new MediaSegment(value, presentation, duration));
                    presentation += duration;
                }
                timeline = end;
                duration = 0;
            }
        }
        return Collections.unmodifiableList(segments);
    }

    public static final class MediaSegment {
        private final String uri;
        private final long presentationTimeMs;
        private final long durationMs;

        MediaSegment(String uri, long presentationTimeMs, long durationMs) {
            this.uri = uri;
            this.presentationTimeMs = presentationTimeMs;
            this.durationMs = durationMs;
        }

        public String uri() { return uri; }
        public long presentationTimeMs() { return presentationTimeMs; }
        public long durationMs() { return durationMs; }
    }
}
