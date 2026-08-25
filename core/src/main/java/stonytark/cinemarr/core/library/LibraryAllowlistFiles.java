package stonytark.cinemarr.core.library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict parser/writer for world/serverconfig/cinemarr-libraries.toml. */
public final class LibraryAllowlistFiles {
    public static final String FILE_NAME = "cinemarr-libraries.toml";

    public static List<LibraryRule> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            writeExample(path);
            return Collections.emptyList();
        }
        List<LibraryRule> output = new ArrayList<LibraryRule>();
        Map<String, String> current = null;
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) continue;
            if ("[[libraries]]".equals(line)) {
                if (current != null) output.add(rule(current));
                current = new LinkedHashMap<String, String>();
                continue;
            }
            if (current == null) throw new IllegalArgumentException("Library values must follow [[libraries]]");
            int equals = line.indexOf('=');
            if (equals < 1) throw new IllegalArgumentException("Malformed library setting");
            String key = line.substring(0, equals).trim();
            if (!known(key)) throw new IllegalArgumentException("Unknown library setting: " + key);
            current.put(key, scalar(line.substring(equals + 1).trim()));
        }
        if (current != null) output.add(rule(current));
        return Collections.unmodifiableList(output);
    }

    private static LibraryRule rule(Map<String, String> values) {
        return new LibraryRule(values.get("id"), values.get("section"), fallback(values.get("displayName"), values.get("section")),
                bool(values, "allowMovies", true), bool(values, "allowShows", true),
                fallback(values.get("maximumContentRating"), ""), integer(values, "permissionLevel", 0));
    }

    private static void writeExample(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<String>();
        lines.add("# No Plex library is visible until an operator adds an allowlist entry.");
        lines.add("# [[libraries]]");
        lines.add("# id = \"family_movies\"");
        lines.add("# section = \"Movies\"");
        lines.add("# displayName = \"Family Movies\"");
        lines.add("# allowMovies = true");
        lines.add("# allowShows = false");
        lines.add("# maximumContentRating = \"PG-13\"");
        lines.add("# permissionLevel = 0");
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static boolean known(String key) {
        return "id".equals(key) || "section".equals(key) || "displayName".equals(key)
                || "allowMovies".equals(key) || "allowShows".equals(key)
                || "maximumContentRating".equals(key) || "permissionLevel".equals(key);
    }
    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException("Invalid boolean for " + key);
    }
    private static int integer(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid integer for " + key); }
    }
    private static String scalar(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }
    private static String stripComment(String value) {
        boolean quoted = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' && (index == 0 || value.charAt(index - 1) != '\\')) quoted = !quoted;
            if (character == '#' && !quoted) return value.substring(0, index);
        }
        return value;
    }
    private static String fallback(String value, String fallback) { return value == null ? fallback : value; }
    private LibraryAllowlistFiles() {}
}
