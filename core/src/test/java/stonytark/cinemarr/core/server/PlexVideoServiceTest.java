package stonytark.cinemarr.core.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.video.RenditionPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlexVideoServiceTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicBoolean stopped = new AtomicBoolean();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/library/sections", exchange -> json(exchange,
                "{\"MediaContainer\":{\"Directory\":[{\"key\":\"1\",\"title\":\"Movies\",\"type\":\"movie\"}]}}"));
        server.createContext("/library/sections/1/all", exchange -> json(exchange,
                "{\"MediaContainer\":{\"Metadata\":["
                        + "{\"type\":\"movie\",\"ratingKey\":\"10\",\"title\":\"Allowed\",\"contentRating\":\"PG\",\"duration\":60000},"
                        + "{\"type\":\"movie\",\"ratingKey\":\"11\",\"title\":\"Denied\",\"contentRating\":\"R\",\"duration\":60000}]}}"));
        server.createContext("/video/:/transcode/universal/start.m3u8", exchange -> bytes(exchange,
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:2.0,\nsegment0.ts\n".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/video/:/transcode/universal/segment0.ts", exchange -> bytes(exchange, new byte[]{1, 2, 3}));
        server.createContext("/video/:/transcode/universal/stop", exchange -> { stopped.set(true); bytes(exchange, new byte[0]); });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void resolvesAllowlistFiltersRatingsAndRelaysSegmentsWithoutExposingCredentials() throws Exception {
        PlexVideoService service = new PlexVideoService(baseUrl, "secret-token");
        LibraryRule rule = new LibraryRule("family", "Movies", "Family", true, false, "PG-13", 0);
        PlexVideoService.ResolvedLibrary library = service.resolveLibraries(Collections.singletonList(rule)).get(0);
        PlexVideoService.Page page = service.browse(library, "", "", 0, 20, 0);
        assertEquals(1, page.items().size());
        VideoMediaItem movie = page.items().get(0);
        PlexVideoService.VideoSession session = service.start(movie,
                RenditionPolicy.choose(4, 4, 1920, 1080, 1920, 1080), 0, null, null);
        assertTrue(session.playlist().startsWith("#EXTM3U"));
        assertFalse(session.playlist().contains("secret-token"));
        assertArrayEquals(new byte[]{1, 2, 3}, service.fetch(session, "segment0.ts"));
        service.stop(session);
        assertTrue(stopped.get());
    }

    @Test void rejectsCrossOriginPlaylistReferences() throws Exception {
        PlexVideoService service = new PlexVideoService(baseUrl, "secret-token");
        VideoMediaItem movie = new VideoMediaItem(MediaKind.MOVIE, "10", "Allowed", "", "PG", 0, 60_000);
        PlexVideoService.VideoSession session = service.start(movie,
                RenditionPolicy.choose(4, 4, 1920, 1080, 1920, 1080), 0, null, null);
        assertThrows(PlexException.class, () -> service.fetch(session, "https://example.invalid/segment.ts"));
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        assertEquals("secret-token", exchange.getRequestHeaders().getFirst("X-Plex-Token"));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        bytes(exchange, body.getBytes(StandardCharsets.UTF_8));
    }
    private static void bytes(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
