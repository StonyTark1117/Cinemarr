package stonytark.cinemarr.core.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import stonytark.cinemarr.core.library.LibraryRule;
import stonytark.cinemarr.core.library.MediaKind;
import stonytark.cinemarr.core.library.VideoMediaItem;
import stonytark.cinemarr.core.library.VideoStreamOption;
import stonytark.cinemarr.core.network.BoundedStreams;
import stonytark.cinemarr.core.network.HttpTransport;
import stonytark.cinemarr.core.network.UrlConnectionHttpTransport;
import stonytark.cinemarr.core.video.RenditionPolicy;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only Plex video boundary. URLs and credentials never appear in client-facing models. */
public final class PlexVideoService {
    public static final int MAX_METADATA_BYTES = 4 * 1024 * 1024;
    public static final int MAX_PLAYLIST_BYTES = 1024 * 1024;
    public static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;
    private static final String CLIENT_ID = "c4c216b757884ecb91dce7f33dbe9f31";

    private final String baseUrl;
    private final String token;
    private final HttpTransport http;
    private final int timeoutMs;

    public PlexVideoService(String baseUrl, String token) throws PlexException {
        this(baseUrl, token, new UrlConnectionHttpTransport(), 15_000);
    }

    PlexVideoService(String baseUrl, String token, HttpTransport http, int timeoutMs) throws PlexException {
        this.baseUrl = validatedBaseUrl(baseUrl);
        this.token = token == null ? "" : token.trim();
        if (this.token.isEmpty()) throw new PlexException(PlexException.Kind.CONFIGURATION, "Plex token is not configured");
        this.http = http;
        this.timeoutMs = Math.max(1, timeoutMs);
    }

    public List<ResolvedLibrary> resolveLibraries(List<LibraryRule> rules) throws IOException {
        JsonArray sections = array(container(json("GET", "/library/sections", "")), "Directory");
        List<ResolvedLibrary> resolved = new ArrayList<ResolvedLibrary>();
        for (LibraryRule rule : rules) {
            JsonObject match = null;
            for (JsonElement element : sections) {
                if (!element.isJsonObject()) continue;
                JsonObject section = element.getAsJsonObject();
                String key = text(section, "key");
                String title = text(section, "title");
                if (rule.section().equals(key) || rule.section().equalsIgnoreCase(title)) { match = section; break; }
            }
            if (match == null) throw new PlexException(PlexException.Kind.CONFIGURATION,
                    "No matching Plex library was found for allowlist entry " + rule.id());
            String type = text(match, "type");
            if (!("movie".equals(type) || "show".equals(type))) throw new PlexException(PlexException.Kind.CONFIGURATION,
                    "Allowlist entry " + rule.id() + " does not reference a movie or show library");
            resolved.add(new ResolvedLibrary(rule, text(match, "key"), type));
        }
        return immutable(resolved);
    }

    public Page browse(ResolvedLibrary library, String parentKey, String search, int page, int pageSize,
                       int playerPermissionLevel) throws IOException {
        if (library == null) throw new IllegalArgumentException("library");
        int boundedSize = Math.max(1, Math.min(100, pageSize));
        int start = Math.max(0, page) * boundedSize;
        String path;
        String query = "X-Plex-Container-Start=" + start + "&X-Plex-Container-Size=" + (boundedSize + 1);
        if (parentKey != null && !parentKey.trim().isEmpty()) path = "/library/metadata/" + encodePath(parentKey) + "/children";
        else {
            path = "/library/sections/" + encodePath(library.sectionKey()) + "/all";
            if (search != null && !search.trim().isEmpty()) query += "&title=" + encode(search.trim());
        }
        JsonArray metadata = array(container(json("GET", path, query)), "Metadata");
        List<VideoMediaItem> visible = new ArrayList<VideoMediaItem>();
        boolean hasMore = metadata.size() > boundedSize;
        for (JsonElement element : metadata) {
            VideoMediaItem item = item(element);
            if (item != null && library.rule().allows(item, playerPermissionLevel)) visible.add(item);
            if (visible.size() >= boundedSize) break;
        }
        return new Page(visible, hasMore);
    }

    public VideoMediaItem metadata(String key) throws IOException {
        return metadataDetails(key).item();
    }

    public PlaybackMetadata metadataDetails(String key) throws IOException {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("key");
        JsonArray metadata = array(container(json("GET", "/library/metadata/" + encodePath(key.trim()), "")), "Metadata");
        if (metadata.size() == 0) throw new PlexException(PlexException.Kind.NOT_FOUND, "Plex video item was not found");
        VideoMediaItem value = item(metadata.get(0));
        if (value == null) throw new PlexException(PlexException.Kind.INVALID_RESPONSE, "Plex item is not playable video metadata");
        return new PlaybackMetadata(value,streams(metadata.get(0).getAsJsonObject()));
    }

    public VideoMediaItem nextEpisode(String key)throws IOException{
        VideoMediaItem current=metadata(key);if(current.kind()!=MediaKind.EPISODE||current.seriesKey().isEmpty())throw new PlexException(PlexException.Kind.NOT_FOUND,"Plex episode has no series metadata");
        JsonArray metadata=array(container(json("GET","/library/metadata/"+encodePath(current.seriesKey())+"/allLeaves","")),"Metadata");List<VideoMediaItem> episodes=new ArrayList<VideoMediaItem>();for(JsonElement element:metadata){VideoMediaItem value=item(element);if(value!=null&&value.kind()==MediaKind.EPISODE)episodes.add(value);}Collections.sort(episodes,(left,right)->{int season=Integer.compare(left.parentIndex(),right.parentIndex());return season!=0?season:Integer.compare(left.index(),right.index());});
        for(int index=0;index<episodes.size();index++)if(episodes.get(index).key().equals(current.key()))return index+1<episodes.size()?episodes.get(index+1):null;throw new PlexException(PlexException.Kind.NOT_FOUND,"Current Plex episode is absent from its series");
    }

    public VideoSession start(VideoMediaItem item, RenditionPolicy.Dimensions rendition, long offsetMs,
                              Integer audioStreamId, Integer subtitleStreamId) throws IOException {
        if (item == null || rendition == null || item.key().isEmpty()) throw new IllegalArgumentException("Playable item required");
        String sessionId = UUID.randomUUID().toString();
        String profile = "add-transcode-target(type=videoProfile&context=streaming&protocol=hls&container=mpegts&videoCodec=h264&audioCodec=aac)";
        StringBuilder query = new StringBuilder();
        parameter(query, "path", "/library/metadata/" + item.key());
        parameter(query, "mediaIndex", "0");
        parameter(query, "partIndex", "0");
        parameter(query, "session", sessionId);
        parameter(query, "protocol", "hls");
        parameter(query, "directPlay", "0");
        parameter(query, "directStream", "0");
        parameter(query, "videoCodec", "h264");
        parameter(query, "audioCodec", "aac");
        parameter(query, "videoResolution", rendition.width() + "x" + rendition.height());
        parameter(query, "maxVideoBitrate", "2000");
        parameter(query, "videoQuality", "60");
        parameter(query, "location", "lan");
        parameter(query, "offset", Long.toString(Math.max(0, offsetMs) / 1000));
        parameter(query, "X-Plex-Client-Identifier", CLIENT_ID);
        parameter(query, "X-Plex-Device-Name", "Minecraft Server");
        parameter(query, "X-Plex-Product", "Cinemarr");
        parameter(query, "X-Plex-Platform", "Java");
        parameter(query, "X-Plex-Version", "1.0.0");
        parameter(query, "X-Plex-Provides", "player");
        parameter(query, "X-Plex-Client-Profile-Name", "Generic");
        parameter(query, "X-Plex-Client-Profile-Extra", profile);
        if (audioStreamId != null) parameter(query, "audioStreamID", Integer.toString(audioStreamId));
        if (subtitleStreamId != null) parameter(query, "subtitleStreamID", Integer.toString(subtitleStreamId));
        parameter(query, "X-Plex-Token", token);
        URL playlist = new URL(baseUrl + "/video/:/transcode/universal/start.m3u8?" + query);
        byte[] manifest = bounded("GET", playlist, Collections.<String, String>emptyMap(), MAX_PLAYLIST_BYTES,
                "Plex video playlist exceeds the safety limit");
        String text = new String(manifest, StandardCharsets.UTF_8);
        if (!text.startsWith("#EXTM3U")) throw new PlexException(PlexException.Kind.INVALID_RESPONSE, "Plex returned a malformed video playlist");
        return new VideoSession(UUID.fromString(sessionId), playlist, text, item.durationMs());
    }

    public byte[] fetch(VideoSession session, String reference) throws IOException {
        if (session == null || reference == null || reference.trim().isEmpty()) throw new IllegalArgumentException("Session and reference required");
        URL resolved = new URL(session.playlistUrl, reference);
        if (!sameOrigin(session.playlistUrl, resolved)) throw new PlexException(PlexException.Kind.INVALID_RESPONSE,
                "Plex playlist referenced a different origin");
        return bounded("GET", resolved, Collections.<String, String>emptyMap(), MAX_SEGMENT_BYTES,
                "Plex video segment exceeds the safety limit");
    }

    /** Fetches a child URI relative to a previously fetched playlist URI. */
    public byte[] fetch(VideoSession session, String parentReference, String childReference) throws IOException {
        if (session == null || parentReference == null || childReference == null) throw new IllegalArgumentException("References required");
        URL parent = new URL(session.playlistUrl, parentReference);
        URL resolved = new URL(parent, childReference);
        if (!sameOrigin(session.playlistUrl, resolved)) throw new PlexException(PlexException.Kind.INVALID_RESPONSE,
                "Plex playlist referenced a different origin");
        return bounded("GET", resolved, Collections.<String, String>emptyMap(), MAX_SEGMENT_BYTES,
                "Plex video segment exceeds the safety limit");
    }

    public void stop(VideoSession session) throws IOException {
        if (session == null) return;
        String query = "session=" + encode(session.id().toString()) + "&X-Plex-Token=" + encode(token);
        HttpTransport.Response response = open("GET", new URL(baseUrl + "/video/:/transcode/universal/stop?" + query),
                Collections.<String, String>emptyMap());
        try { if (response.statusCode() / 100 != 2 && response.statusCode() != 404) throw status(response.statusCode(), "stop"); }
        finally { response.close(); }
    }

    private JsonObject json(String method, String path, String query) throws IOException {
        URL url = new URL(baseUrl + path + (query.isEmpty() ? "" : "?" + query));
        Map<String, String> headers = headers();
        headers.put("Accept", "application/json");
        byte[] bytes = bounded(method, url, headers, MAX_METADATA_BYTES, "Plex metadata response exceeds the safety limit");
        try {
            JsonElement parsed = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root");
            return parsed.getAsJsonObject();
        } catch (RuntimeException error) {
            throw new PlexException(PlexException.Kind.INVALID_RESPONSE, "Plex returned malformed JSON", error);
        }
    }

    private byte[] bounded(String method, URL url, Map<String, String> headers, int maximum, String message) throws IOException {
        HttpTransport.Response response = open(method, url, headers);
        try {
            if (response.statusCode() / 100 != 2) throw status(response.statusCode(), "request");
            if (response.contentLength() > maximum) throw new PlexException(PlexException.Kind.INVALID_RESPONSE, message);
            return BoundedStreams.read(response.body(), maximum, message);
        } finally { response.close(); }
    }

    private HttpTransport.Response open(String method, URL url, Map<String, String> supplied) throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>(supplied);
        String query = url.getQuery();
        if (!headers.containsKey("X-Plex-Token") && (query == null || !query.contains("X-Plex-Token="))) headers.putAll(headers());
        return http.open(method, url, headers, timeoutMs, timeoutMs);
    }
    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Plex-Token", token);
        headers.put("X-Plex-Product", "Cinemarr");
        headers.put("X-Plex-Version", "1.0.0");
        headers.put("X-Plex-Client-Identifier", CLIENT_ID);
        return headers;
    }
    private static PlexException status(int status, String operation) {
        PlexException.Kind kind = status == 401 || status == 403 ? PlexException.Kind.AUTHENTICATION
                : status == 404 ? PlexException.Kind.NOT_FOUND : PlexException.Kind.OFFLINE;
        return new PlexException(kind, "Plex " + operation + " returned HTTP " + status);
    }
    private static VideoMediaItem item(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject value = element.getAsJsonObject();
        String type = text(value, "type");
        MediaKind kind;
        if ("movie".equals(type)) kind = MediaKind.MOVIE;
        else if ("show".equals(type)) kind = MediaKind.SHOW;
        else if ("season".equals(type)) kind = MediaKind.SEASON;
        else if ("episode".equals(type)) kind = MediaKind.EPISODE;
        else return null;
        String key = text(value, "ratingKey");
        String title = text(value, "title");
        if (key.isEmpty() || title.isEmpty()) return null;
        return new VideoMediaItem(kind, key, title, text(value, "grandparentTitle"), text(value, "contentRating"),
                integer(value, "index"), number(value, "duration"),text(value,"grandparentRatingKey"),integer(value,"parentIndex"));
    }
    private static boolean sameOrigin(URL first, URL second) {
        int firstPort = first.getPort() < 0 ? first.getDefaultPort() : first.getPort();
        int secondPort = second.getPort() < 0 ? second.getDefaultPort() : second.getPort();
        return first.getProtocol().equalsIgnoreCase(second.getProtocol()) && first.getHost().equalsIgnoreCase(second.getHost())
                && firstPort == secondPort;
    }
    private static String validatedBaseUrl(String value) throws PlexException {
        try {
            String candidate = value == null ? "" : value.trim();
            URI uri = URI.create(candidate);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("scheme or host");
            }
            return candidate.endsWith("/") ? candidate.substring(0, candidate.length() - 1) : candidate;
        } catch (IllegalArgumentException error) {
            throw new PlexException(PlexException.Kind.CONFIGURATION, "Plex URL must be an http(s) URL with a host", error);
        }
    }
    private static void parameter(StringBuilder output, String key, String value) {
        if (output.length() != 0) output.append('&');
        output.append(encode(key)).append('=').append(encode(value));
    }
    private static String encodePath(String value) { return encode(value).replace("+", "%20"); }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    private static JsonObject container(JsonObject root) {
        JsonElement value = root.get("MediaContainer");
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }
    private static String text(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
    private static long number(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsLong();
    }
    private static int integer(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsInt();
    }
    private static List<VideoStreamOption> streams(JsonObject metadata){
        List<VideoStreamOption> values=new ArrayList<VideoStreamOption>();JsonArray media=array(metadata,"Media");
        for(JsonElement mediaElement:media){if(!mediaElement.isJsonObject())continue;JsonArray parts=array(mediaElement.getAsJsonObject(),"Part");for(JsonElement partElement:parts){if(!partElement.isJsonObject())continue;JsonArray streams=array(partElement.getAsJsonObject(),"Stream");for(JsonElement streamElement:streams){if(!streamElement.isJsonObject())continue;JsonObject stream=streamElement.getAsJsonObject();int type=integer(stream,"streamType"),id=integer(stream,"id");if(id<1||(type!=2&&type!=3))continue;String language=text(stream,"language");String title=text(stream,"title");String label=title.isEmpty()?language:title;if(label.isEmpty())label=type==2?"Audio "+id:"Subtitle "+id;values.add(new VideoStreamOption(type==2?VideoStreamOption.Kind.AUDIO:VideoStreamOption.Kind.SUBTITLE,id,label,text(stream,"languageCode"),text(stream,"codec"),integer(stream,"selected")==1));}}}
        return immutable(values);
    }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }

    public static final class ResolvedLibrary {
        private final LibraryRule rule;
        private final String sectionKey;
        private final String plexType;
        ResolvedLibrary(LibraryRule rule, String sectionKey, String plexType) {
            this.rule = rule; this.sectionKey = sectionKey; this.plexType = plexType;
        }
        public LibraryRule rule() { return rule; }
        public String sectionKey() { return sectionKey; }
        public String plexType() { return plexType; }
    }
    public static final class Page {
        private final List<VideoMediaItem> items;
        private final boolean hasMore;
        Page(List<VideoMediaItem> items, boolean hasMore) { this.items = immutable(items); this.hasMore = hasMore; }
        public List<VideoMediaItem> items() { return items; }
        public boolean hasMore() { return hasMore; }
    }
    public static final class PlaybackMetadata {private final VideoMediaItem item;private final List<VideoStreamOption> streams;PlaybackMetadata(VideoMediaItem item,List<VideoStreamOption> streams){this.item=item;this.streams=immutable(streams);}public VideoMediaItem item(){return item;}public List<VideoStreamOption> streams(){return streams;}}
    public static final class VideoSession {
        private final UUID id;
        private final URL playlistUrl;
        private final String playlist;
        private final long durationMs;
        VideoSession(UUID id, URL playlistUrl, String playlist, long durationMs) {
            this.id = id; this.playlistUrl = playlistUrl; this.playlist = playlist; this.durationMs = durationMs;
        }
        public UUID id() { return id; }
        public String playlist() { return playlist; }
        public long durationMs() { return durationMs; }
    }
}
