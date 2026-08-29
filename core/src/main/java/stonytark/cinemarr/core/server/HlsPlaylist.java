package stonytark.cinemarr.core.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small, loader-independent helpers for parsing HLS media playlists. */
public final class HlsPlaylist {
    private static final String EXTINF = "#EXTINF:";
    private static final String STREAM_INF = "#EXT-X-STREAM-INF:";
    private static final String MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE:";

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

    /** Returns whether this is a media playlist rather than a master playlist. */
    public static boolean isMediaPlaylist(String playlist) {
        if (playlist == null) return false;
        for (String line : playlist.split("\\r?\\n")) {
            if (line.trim().startsWith(EXTINF)) return true;
        }
        return false;
    }

    /** Selects the first variant URI from an HLS master playlist. */
    public static String firstVariantReference(String playlist) {
        if (playlist == null) throw new IllegalArgumentException("HLS playlist is required");
        boolean variantFollows = false;
        for (String line : playlist.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith(STREAM_INF)) {
                variantFollows = true;
            } else if (variantFollows && !value.isEmpty() && !value.startsWith("#")) {
                return value;
            }
        }
        throw new IllegalArgumentException("HLS master playlist has no playable variant");
    }

    /**
     * Returns the playable suffix of a Plex HLS playlist for a transcode
     * started at {@code offsetMs}. Plex retains pre-seek entries in the
     * playlist but serves those URLs as one-packet placeholders.
     */
    public static List<MediaSegment> mediaSegments(String playlist, long offsetMs) {
        if (playlist == null) throw new IllegalArgumentException("HLS playlist is required");
        long offset = Math.max(0, offsetMs), timeline = 0, duration = -1, mediaSequence = -1;
        List<ParsedSegment> parsed = new ArrayList<ParsedSegment>();
        for (String line : playlist.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith(EXTINF)) {
                duration = durationMillis(value);
            } else if (value.startsWith(MEDIA_SEQUENCE)) {
                try { mediaSequence = Long.parseLong(value.substring(MEDIA_SEQUENCE.length()).trim()); }
                catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("HLS media sequence is invalid", invalid);
                }
                if (mediaSequence < 0) throw new IllegalArgumentException("HLS media sequence is negative");
            } else if (!value.isEmpty() && !value.startsWith("#")) {
                if (duration <= 0) throw new IllegalArgumentException("HLS media segment has no positive EXTINF duration");
                long end = timeline + duration;
                parsed.add(new ParsedSegment(value, end, duration));
                timeline = end;
                duration = -1;
            }
        }
        int first = 0;
        // A non-zero media sequence is authoritative evidence that Plex has
        // already returned a seek-relative window. A zero/absent sequence may
        // still describe the complete VOD timeline with pre-seek placeholders.
        if (!(offset > 0 && mediaSequence > 0)) {
            while (first < parsed.size() && parsed.get(first).endMs <= offset) first++;
        }
        // Some Plex versions return the complete pre-seek timeline with tiny
        // placeholder segments; others return a playlist already based at the
        // requested offset. Do not discard an entire seek-relative playlist.
        if (first == parsed.size() && offset > 0 && !parsed.isEmpty()) first = 0;
        long presentation = offset;
        List<MediaSegment> segments = new ArrayList<MediaSegment>();
        for (int index = first; index < parsed.size(); index++) {
            ParsedSegment value = parsed.get(index);
            segments.add(new MediaSegment(value.uri, presentation, value.durationMs));
            presentation += value.durationMs;
        }
        return Collections.unmodifiableList(segments);
    }

    private static final class ParsedSegment {
        private final String uri;
        private final long endMs;
        private final long durationMs;

        private ParsedSegment(String uri, long endMs, long durationMs) {
            this.uri = uri;
            this.endMs = endMs;
            this.durationMs = durationMs;
        }
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
