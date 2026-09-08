package stonytark.cinemarr.core.server;

import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.network.HttpTransport;
import stonytark.cinemarr.core.network.Hashing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ActiveVideoMediaTest {
    private static List<ActiveVideoMedia.SegmentReference> segments() {
        return new ArrayList<>(Collections.singletonList(new ActiveVideoMedia.SegmentReference(
                HlsPlaylist.mediaSegments("#EXTM3U\n#EXTINF:8,\nsegment.ts\n", 0).get(0))));
    }

    private static ActiveVideoMedia media(List<ActiveVideoMedia.SegmentReference> segments) {
        return new ActiveVideoMedia(null, null, null, segments, null, 8_000,
                Collections.emptyList(), -1, -1);
    }

    @Test
    void mainThreadMetadataReadsDoNotWaitForTheSegmentDownloadMonitor() throws Exception {
        ActiveVideoMedia media = media(segments());
        CountDownLatch downloadOwnsMonitor = new CountDownLatch(1);
        CountDownLatch releaseDownload = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> download = threads.submit(() -> {
                synchronized (media) {
                    downloadOwnsMonitor.countDown();
                    try {
                        releaseDownload.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
            assertTrue(downloadOwnsMonitor.await(5, TimeUnit.SECONDS));
            Future<?> mainThreadRead = threads.submit(() -> {
                assertEquals(1, media.segmentCount());
                assertEquals(0, media.presentationTime(0));
                assertEquals(0, media.segmentAt(4_000));
                assertEquals(1, media.descriptors(0, 16).size());
                assertEquals(0, media.cachedSegments());
                assertEquals(0, media.cachedBytes());
                assertEquals(0, media.fetchRetries());
                assertEquals(0, media.fetchFailures());
            });
            // The simulated slow fetch retains its monitor until every
            // main-thread read completes. No network timing or sleeps involved.
            mainThreadRead.get(2, TimeUnit.SECONDS);
            releaseDownload.countDown();
            download.get(5, TimeUnit.SECONDS);
        } finally {
            releaseDownload.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void publishedSegmentMetadataOwnsAnImmutableSnapshot() {
        List<ActiveVideoMedia.SegmentReference> input = segments();
        ActiveVideoMedia media = media(input);
        input.clear();
        assertEquals(1, media.segmentCount());
        assertEquals(8_000, media.descriptors(0, 16).get(0).durationMs());
    }

    @Test
    void descriptorWindowsRejectInvalidBoundsAndCannotOverflow() {
        ActiveVideoMedia media = media(segments());
        assertEquals(0, media.descriptors(-1, 16).size());
        assertEquals(0, media.descriptors(1, 16).size());
        assertEquals(0, media.descriptors(0, -1).size());
        assertEquals(0, media.descriptors(0, 0).size());
        assertEquals(1, media.descriptors(0, Integer.MAX_VALUE).size());
    }

    @Test
    void actualSegmentFetchRetriesMaterializationWithoutBlockingMetadataAndCachesTheResult() throws Exception {
        CountDownLatch fetching = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger responsesClosed = new AtomicInteger();
        byte[] payload = {1, 2, 3};
        HttpTransport http = (method, url, headers, connectTimeout, readTimeout) -> {
            int status = requests.incrementAndGet() <= 2 ? 404 : 200;
            if (status == 200) {
                fetching.countDown();
                try {
                    if (!releaseFetch.await(10, TimeUnit.SECONDS)) throw new IOException("test fetch timeout");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test fetch interrupted", interrupted);
                }
            }
            return new HttpTransport.Response() {
                public int statusCode() { return status; }
                public long contentLength() { return payload.length; }
                public InputStream body() { return new ByteArrayInputStream(payload); }
                public void close() { responsesClosed.incrementAndGet(); }
            };
        };
        PlexVideoService plex = new PlexVideoService("http://plex.example.invalid", "test-token", http, 1_000);
        String text = "#EXTM3U\n#EXTINF:8,\nsegment.ts\n";
        PlexVideoService.VideoSession session = new PlexVideoService.VideoSession(UUID.randomUUID(),
                new URL("http://plex.example.invalid/start.m3u8"), text, 8_000);
        PlexVideoService.MediaPlaylist playlist = plex.mediaPlaylist(session, 0);
        ActiveVideoMedia media = new ActiveVideoMedia(plex, session, playlist,
                Collections.singletonList(new ActiveVideoMedia.SegmentReference(playlist.segments().get(0))),
                null, 8_000, Collections.emptyList(), -1, -1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<ActiveVideoMedia.SegmentData> download = threads.submit(() -> media.segment(0));
            assertTrue(fetching.await(5, TimeUnit.SECONDS));
            threads.submit(() -> {
                assertEquals(1, media.segmentCount());
                assertEquals(0, media.presentationTime(0));
                assertEquals(0, media.segmentAt(4_000));
                assertEquals(1, media.descriptors(0, 16).size());
                assertEquals(0, media.cachedSegments());
                assertEquals(0, media.cachedBytes());
                assertEquals(2, media.fetchRetries());
                assertEquals(0, media.fetchFailures());
            }).get(2, TimeUnit.SECONDS);
            releaseFetch.countDown();
            ActiveVideoMedia.SegmentData data = download.get(5, TimeUnit.SECONDS);
            assertArrayEquals(payload, data.bytes);
            assertEquals(Hashing.sha256(payload), data.sha);
            assertSame(data, media.segment(0));
            assertEquals(3, requests.get());
            assertEquals(3, responsesClosed.get());
            assertEquals(1, media.cachedSegments());
            assertEquals(3, media.cachedBytes());
        } finally {
            releaseFetch.countDown();
            threads.shutdownNow();
            assertTrue(threads.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
