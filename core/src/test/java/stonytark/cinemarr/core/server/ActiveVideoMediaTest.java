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
    private static ActiveVideoMedia timelineMedia(List<String> requests) throws Exception {
        HttpTransport http = (method, url, headers, connectTimeout, readTimeout) -> {
            requests.add(method + " " + url.getPath() + "?" + url.getQuery());
            return new HttpTransport.Response() {
                public int statusCode() { return 200; }
                public long contentLength() { return 2; }
                public InputStream body() { return new ByteArrayInputStream(new byte[]{'{', '}'}); }
                public void close() { }
            };
        };
        PlexVideoService plex = new PlexVideoService("http://plex.example.invalid", "test-token", http, 1_000);
        PlexVideoService.VideoSession session = new PlexVideoService.VideoSession(UUID.randomUUID(),
                new URL("http://plex.example.invalid/start.m3u8"), "#EXTM3U\n#EXTINF:8,\nsegment.ts\n", 60_000, "10", 0);
        return new ActiveVideoMedia(plex, session, null, segments(), null, 60_000,
                Collections.emptyList(), -1, -1);
    }

    private static void drainTimelines(VideoWorkQueues workers) throws Exception {
        workers.timeline(() -> null).get(5, TimeUnit.SECONDS);
    }

    @Test void timelineUsesLatestQueuedStateAndReportsEveryTenSecondsOrOnPause() throws Exception {
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        ActiveVideoMedia media = timelineMedia(requests);
        CountDownLatch occupied = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        try (VideoWorkQueues workers = new VideoWorkQueues("timeline-test ")) {
            workers.timeline(() -> {
                occupied.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                return null;
            });
            assertTrue(occupied.await(5, TimeUnit.SECONDS));
            media.updateTimeline(1_000, 1_000, false, workers, failure -> failures.incrementAndGet());
            for (int index = 0; index < 100; index++) {
                media.updateTimeline(2_000, 2_000, true, workers, failure -> failures.incrementAndGet());
            }
            assertEquals(1, workers.timelineQueuedTasks(), "Coalescing must bound each stream to one pending report");
            assertTrue(requests.isEmpty(), "Updating state must not perform HTTP on the caller");
            release.countDown(); drainTimelines(workers);
            assertEquals(1, requests.size());
            assertTrue(requests.get(0).contains("state=paused&time=2000"));
            media.updateTimeline(11_999, 2_000, true, workers, failure -> failures.incrementAndGet());
            drainTimelines(workers); assertEquals(1, requests.size());
            media.updateTimeline(12_000, 2_000, true, workers, failure -> failures.incrementAndGet());
            drainTimelines(workers); assertEquals(2, requests.size());
            media.updateTimeline(12_001, 2_001, false, workers, failure -> failures.incrementAndGet());
            drainTimelines(workers); assertEquals(3, requests.size());
            assertTrue(requests.get(2).contains("state=playing&time=2001"));
            media.close();
            assertTrue(requests.get(3).contains("state=stopped"));
            assertTrue(requests.get(4).contains("/transcode/universal/stop"));
            assertEquals(0, failures.get());
        } finally { release.countDown(); }
    }

    @Test void closingBeforeQueuedHeartbeatPreventsAnyPlaybackReportAfterStop() throws Exception {
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        ActiveVideoMedia media = timelineMedia(requests);
        CountDownLatch occupied = new CountDownLatch(1), release = new CountDownLatch(1);
        try (VideoWorkQueues workers = new VideoWorkQueues("timeline-close-test ")) {
            workers.timeline(() -> {
                occupied.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                return null;
            });
            assertTrue(occupied.await(5, TimeUnit.SECONDS));
            media.updateTimeline(1_000, 1_000, false, workers, failure -> { });
            media.close();
            release.countDown(); drainTimelines(workers);
            media.updateTimeline(100_000, 20_000, false, workers, failure -> { });
            drainTimelines(workers);
            assertEquals(1, requests.size());
            assertTrue(requests.get(0).startsWith("GET /video/:/transcode/universal/stop"));
        } finally { release.countDown(); }
    }

    @Test void rejectedTimelineAdmissionRetriesWithoutRetainingAPendingReport() throws Exception {
        List<String> requests = Collections.synchronizedList(new ArrayList<>());
        ActiveVideoMedia media = timelineMedia(requests);
        AtomicInteger failures = new AtomicInteger();
        VideoWorkQueues closed = new VideoWorkQueues("timeline-rejected-test ");
        closed.close();
        media.updateTimeline(1_000, 1_000, false, closed, failure -> failures.incrementAndGet());
        assertEquals(1, failures.get());
        try (VideoWorkQueues healthy = new VideoWorkQueues("timeline-retry-test ")) {
            media.updateTimeline(11_000, 11_000, false, healthy, failure -> failures.incrementAndGet());
            drainTimelines(healthy);
            assertEquals(1, requests.size());
            assertTrue(requests.get(0).contains("state=playing&time=11000"));
            assertEquals(1, failures.get());
            media.close();
        }
    }

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
                assertEquals(null, media.cachedSegment(0));
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
                assertEquals(null, media.cachedSegment(0), "pending retries must not start or wait for another fetch");
                assertEquals(0, media.fetchFailures());
            }).get(2, TimeUnit.SECONDS);
            releaseFetch.countDown();
            ActiveVideoMedia.SegmentData data = download.get(5, TimeUnit.SECONDS);
            assertArrayEquals(payload, data.bytes);
            assertEquals(Hashing.sha256(payload), data.sha);
            assertSame(data, media.segment(0));
            assertSame(data, media.cachedSegment(0), "window replay uses the already fetched segment");
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
