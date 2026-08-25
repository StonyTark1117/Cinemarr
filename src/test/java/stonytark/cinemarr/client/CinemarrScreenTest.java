package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.network.CinemarrPayloads;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CinemarrScreenTest {
    @Test void queueRequestDoesNotInheritThePreviousSearchQuery() {
        assertEquals("", CinemarrScreen.browseQuery(CinemarrPayloads.BrowseKind.QUEUE, "previous search"));
    }

    @Test void textSearchRetainsItsTrimmedQuery() {
        assertEquals("search terms", CinemarrScreen.browseQuery(CinemarrPayloads.BrowseKind.SEARCH, "  search terms  "));
    }
}
