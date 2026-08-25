package stonytark.cinemarr.core.library;

import java.util.Objects;

/** Sanitized Plex metadata safe to send to a client. */
public final class VideoMediaItem {
    private final MediaKind kind;
    private final String key;
    private final String title;
    private final String parentTitle;
    private final String contentRating;
    private final int index;
    private final long durationMs;

    public VideoMediaItem(MediaKind kind, String key, String title, String parentTitle, String contentRating,
                          int index, long durationMs) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.key = clean(key);
        this.title = clean(title);
        this.parentTitle = clean(parentTitle);
        this.contentRating = clean(contentRating);
        this.index = Math.max(0, index);
        this.durationMs = Math.max(0, durationMs);
    }

    public MediaKind kind() { return kind; }
    public String key() { return key; }
    public String title() { return title; }
    public String parentTitle() { return parentTitle; }
    public String contentRating() { return contentRating; }
    public int index() { return index; }
    public long durationMs() { return durationMs; }
    private static String clean(String value) { return value == null ? "" : value; }
}
