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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlexVideoServiceTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicReference<String> transcodeQuery = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/library/sections", exchange -> json(exchange,
                "{\"MediaContainer\":{\"Directory\":[{\"key\":\"1\",\"title\":\"Movies\",\"type\":\"movie\"}]}}"));
        server.createContext("/library/sections/1/all", exchange -> json(exchange,
                "{\"MediaContainer\":{\"Metadata\":["
                        + "{\"type\":\"movie\",\"ratingKey\":\"10\",\"title\":\"Allowed\",\"contentRating\":\"PG\",\"duration\":60000},"
                        + "{\"type\":\"movie\",\"ratingKey\":\"11\",\"title\":\"Denied\",\"contentRating\":\"R\",\"duration\":60000}]}}"));
        server.createContext("/library/metadata/10", exchange -> json(exchange,"{\"MediaContainer\":{\"Metadata\":[{\"type\":\"movie\",\"ratingKey\":\"10\",\"title\":\"Allowed\",\"contentRating\":\"PG\",\"duration\":60000,\"Media\":[{\"Part\":[{\"Stream\":[{\"streamType\":2,\"id\":101,\"language\":\"English\",\"languageCode\":\"eng\",\"codec\":\"aac\",\"selected\":1},{\"streamType\":3,\"id\":202,\"title\":\"English SDH\",\"languageCode\":\"eng\",\"codec\":\"srt\"}]}]}]}]}}"));
        server.createContext("/library/metadata/20",exchange->json(exchange,"{\"MediaContainer\":{\"Metadata\":[{\"type\":\"episode\",\"ratingKey\":\"20\",\"title\":\"First\",\"grandparentTitle\":\"Show\",\"grandparentRatingKey\":\"99\",\"parentIndex\":1,\"index\":1,\"duration\":30000}]}}"));
        server.createContext("/library/metadata/11", exchange -> json(exchange, metadataWithSelected("true")));
        server.createContext("/library/metadata/12", exchange -> json(exchange, metadataWithSelected("false")));
        server.createContext("/library/metadata/13", exchange -> json(exchange, metadataWithSelected("0")));
        server.createContext("/library/metadata/99/allLeaves",exchange->json(exchange,"{\"MediaContainer\":{\"Metadata\":[{\"type\":\"episode\",\"ratingKey\":\"21\",\"title\":\"Second\",\"grandparentTitle\":\"Show\",\"grandparentRatingKey\":\"99\",\"parentIndex\":1,\"index\":2,\"duration\":30000},{\"type\":\"episode\",\"ratingKey\":\"20\",\"title\":\"First\",\"grandparentTitle\":\"Show\",\"grandparentRatingKey\":\"99\",\"parentIndex\":1,\"index\":1,\"duration\":30000}]}}"));
        server.createContext("/video/:/transcode/universal/start.m3u8", exchange -> {transcodeQuery.set(exchange.getRequestURI().getRawQuery());bytes(exchange,
                "#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:2.0,\nsegment0.ts\n".getBytes(StandardCharsets.UTF_8));});
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

    @Test void discoversAudioAndSubtitleStreamsAndSelectsThemWithoutExposingTheTokenInTheManifest() throws Exception {
        PlexVideoService service=new PlexVideoService(baseUrl,"secret-token");PlexVideoService.PlaybackMetadata metadata=service.metadataDetails("10");
        assertEquals(2,metadata.streams().size());assertEquals("English SDH",metadata.streams().get(1).label());
        PlexVideoService.VideoSession session=service.start(metadata.item(),RenditionPolicy.choose(4,4,1920,1080,1920,1080),0,101,202);
        assertTrue(transcodeQuery.get().contains("audioStreamID=101"));assertTrue(transcodeQuery.get().contains("subtitleStreamID=202"));assertFalse(session.playlist().contains("secret-token"));
    }

    @Test void acceptsBooleanAndNumericSelectedStreamFlags() throws Exception {
        PlexVideoService service = new PlexVideoService(baseUrl, "secret-token");
        assertTrue(service.metadataDetails("10").streams().get(0).selected(), "numeric 1");
        assertTrue(service.metadataDetails("11").streams().get(0).selected(), "boolean true");
        assertFalse(service.metadataDetails("12").streams().get(0).selected(), "boolean false");
        assertFalse(service.metadataDetails("13").streams().get(0).selected(), "numeric 0");
    }

    @Test void resolvesTheNextEpisodeAcrossTheShowsOrderedLeaves()throws Exception{VideoMediaItem next=new PlexVideoService(baseUrl,"secret-token").nextEpisode("20");assertEquals("21",next.key());assertEquals("Second",next.title());assertEquals(1,next.parentIndex());}

    private static void json(HttpExchange exchange, String body) throws IOException {
        assertEquals("secret-token", exchange.getRequestHeaders().getFirst("X-Plex-Token"));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        bytes(exchange, body.getBytes(StandardCharsets.UTF_8));
    }
    private static String metadataWithSelected(String selected) {
        return "{\"MediaContainer\":{\"Metadata\":[{\"type\":\"movie\",\"ratingKey\":\"11\",\"title\":\"Allowed\",\"duration\":60000,\"Media\":[{\"Part\":[{\"Stream\":[{\"streamType\":2,\"id\":101,\"selected\":" + selected + "}]}]}]}]}}";
    }
    private static void bytes(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
