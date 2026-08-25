package stonytark.cinemarr.core.library;

/** One server-owned library allowlist entry. */
public final class LibraryRule {
    private final String id;
    private final String section;
    private final String displayName;
    private final boolean allowMovies;
    private final boolean allowShows;
    private final String maximumContentRating;
    private final int permissionLevel;

    public LibraryRule(String id, String section, String displayName, boolean allowMovies, boolean allowShows,
                       String maximumContentRating, int permissionLevel) {
        this.id = required(id, "id");
        this.section = required(section, "section");
        this.displayName = required(displayName, "displayName");
        if (!allowMovies && !allowShows) throw new IllegalArgumentException("A library must allow movies or shows");
        if (permissionLevel < 0 || permissionLevel > 4) throw new IllegalArgumentException("permissionLevel must be 0 through 4");
        this.allowMovies = allowMovies;
        this.allowShows = allowShows;
        this.maximumContentRating = maximumContentRating == null ? "" : maximumContentRating.trim();
        this.permissionLevel = permissionLevel;
    }

    public String id() { return id; }
    public String section() { return section; }
    public String displayName() { return displayName; }
    public boolean allowMovies() { return allowMovies; }
    public boolean allowShows() { return allowShows; }
    public String maximumContentRating() { return maximumContentRating; }
    public int permissionLevel() { return permissionLevel; }

    public boolean allows(VideoMediaItem item, int playerPermissionLevel) {
        if (item == null || playerPermissionLevel < permissionLevel) return false;
        boolean allowedKind = item.kind() == MediaKind.MOVIE ? allowMovies
                : item.kind() == MediaKind.SHOW || item.kind() == MediaKind.SEASON || item.kind() == MediaKind.EPISODE
                ? allowShows : false;
        return allowedKind && ContentRatings.atOrBelow(item.contentRating(), maximumContentRating);
    }

    private static String required(String value, String field) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return result;
    }
}
