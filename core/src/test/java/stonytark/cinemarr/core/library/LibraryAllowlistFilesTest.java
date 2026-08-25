package stonytark.cinemarr.core.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryAllowlistFilesTest {
    @TempDir Path temporary;

    @Test void missingFileIsGeneratedDenyingEverythingByDefault() throws Exception {
        Path path = temporary.resolve(LibraryAllowlistFiles.FILE_NAME);
        assertTrue(LibraryAllowlistFiles.load(path).isEmpty());
        assertTrue(Files.readAllLines(path, StandardCharsets.UTF_8).get(0).contains("No Plex library is visible"));
    }

    @Test void parsesRulesAndEnforcesTypeRatingAndPermission() throws Exception {
        Path path = temporary.resolve(LibraryAllowlistFiles.FILE_NAME);
        Files.write(path, ("[[libraries]]\n"
                + "id = \"family_movies\"\nsection = \"Movies\"\ndisplayName = \"Family Movies\"\n"
                + "allowMovies = true\nallowShows = false\nmaximumContentRating = \"PG-13\"\npermissionLevel = 1\n")
                .getBytes(StandardCharsets.UTF_8));
        List<LibraryRule> rules = LibraryAllowlistFiles.load(path);
        assertEquals(1, rules.size());
        VideoMediaItem pg = new VideoMediaItem(MediaKind.MOVIE, "1", "Film", "", "PG", 0, 1000);
        VideoMediaItem ratedR = new VideoMediaItem(MediaKind.MOVIE, "2", "Film", "", "R", 0, 1000);
        VideoMediaItem episode = new VideoMediaItem(MediaKind.EPISODE, "3", "Episode", "Show", "TV-PG", 1, 1000);
        assertFalse(rules.get(0).allows(pg, 0));
        assertTrue(rules.get(0).allows(pg, 1));
        assertFalse(rules.get(0).allows(ratedR, 1));
        assertFalse(rules.get(0).allows(episode, 1));
    }

    @Test void rejectsUnknownSettingsRatherThanSilentlyWeakeningPolicy() throws Exception {
        Path path = temporary.resolve(LibraryAllowlistFiles.FILE_NAME);
        Files.write(path, "[[libraries]]\nid=\"x\"\nsection=\"Movies\"\ndisplayName=\"X\"\nallowEverything=true\n"
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> LibraryAllowlistFiles.load(path));
    }
}
