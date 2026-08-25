package stonytark.cinemarr.core.library;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Conservative US movie/TV rating comparison used by the server allowlist. */
public final class ContentRatings {
    private static final Map<String, Integer> LEVELS = new HashMap<String, Integer>();
    static {
        put(0, "", "NR", "UNRATED", "NOT RATED", "TV-Y");
        put(1, "G", "TV-G", "TV-Y7", "TV-Y7-FV");
        put(2, "PG", "TV-PG");
        put(3, "PG-13", "TV-14");
        put(4, "R", "TV-MA");
        put(5, "NC-17", "X");
    }

    public static boolean atOrBelow(String actual, String maximum) {
        if (maximum == null || maximum.trim().isEmpty()) return true;
        Integer maximumLevel = LEVELS.get(normalize(maximum));
        Integer actualLevel = LEVELS.get(normalize(actual));
        // Unknown ratings are denied when a maximum is configured.
        return maximumLevel != null && actualLevel != null && actualLevel <= maximumLevel;
    }

    private static void put(int level, String... values) { for (String value : values) LEVELS.put(normalize(value), level); }
    private static String normalize(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        int slash = result.lastIndexOf('/');
        return slash < 0 ? result : result.substring(slash + 1);
    }
    private ContentRatings() {}
}
