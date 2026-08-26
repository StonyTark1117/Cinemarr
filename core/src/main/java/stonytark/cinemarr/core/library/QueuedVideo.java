package stonytark.cinemarr.core.library;

/** Server-authoritative queue entry with the allowlist identity needed to revalidate playback. */
public final class QueuedVideo {
    private final String libraryId;
    private final VideoMediaItem item;
    public QueuedVideo(String libraryId,VideoMediaItem item){if(libraryId==null||libraryId.trim().isEmpty()||item==null)throw new IllegalArgumentException("Invalid queued video");this.libraryId=libraryId.trim();this.item=item;}
    public String libraryId(){return libraryId;}public VideoMediaItem item(){return item;}
}
