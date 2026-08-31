package stonytark.cinemarr.client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import stonytark.cinemarr.core.platform.DecoderProbeFixture;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;
import stonytark.cinemarr.core.server.BoundedWorkExecutor;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;

/** Java-8-compatible FFmpeg H.264/AAC decoder for independently-keyframed HLS segments. */
public final class LegacyFfmpegVideoDecoder implements LegacyMediaSegmentDecoder {
    private static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;
    private static final long MAX_RETAINED_VIDEO_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_PROBE_KEYS = 32;
    private static final BoundedWorkExecutor PROBE_EXECUTOR =
            new BoundedWorkExecutor(1, 8, "Cinemarr legacy FFmpeg hardware probe ");
    private static final Map<String, CompletableFuture<String>> PROBES = new ConcurrentHashMap<String, CompletableFuture<String>>();
    private final VideoDecoderBackend requestedBackend;
    private final String requestedDevice;
    private final String sanitizedDeviceType;
    private volatile VideoDecoderBackend effectiveBackend;
    private volatile String fallbackReason = "";
    private volatile long fallbackCount;
    private volatile long peakRetainedBytes;
    private long decodedSegments;
    private long decodedFrames;
    private long wallNanos;
    private long cpuNanos;
    private long transferNanos;
    private long conversionNanos;

    public LegacyFfmpegVideoDecoder() { this(VideoDecoderBackend.SOFTWARE, ""); }

    public LegacyFfmpegVideoDecoder(VideoDecoderBackend backend, String device) {
        requestedBackend = backend == null ? VideoDecoderBackend.SOFTWARE : backend;
        requestedDevice = device == null ? "" : device.trim();
        sanitizedDeviceType = sanitizedDeviceType(requestedDevice);
        effectiveBackend = requestedBackend == VideoDecoderBackend.SOFTWARE ? VideoDecoderBackend.SOFTWARE : null;
        preloadHostHardwareLibraries(requestedBackend);
        if (requestedBackend != VideoDecoderBackend.SOFTWARE) {
            List<VideoDecoderBackend> candidates = candidates();
            if (candidates.isEmpty()) effectiveBackend = VideoDecoderBackend.SOFTWARE;
            else for (VideoDecoderBackend candidate : candidates) probe(candidate);
        }
    }

    @Override public LegacyDecodedMediaSegment decode(byte[] mpegTs) throws FrameGrabber.Exception {
        validate(mpegTs);
        long started = System.nanoTime();
        long cpuStarted = currentThreadCpuTime();
        if (requestedBackend != VideoDecoderBackend.SOFTWARE && effectiveBackend != VideoDecoderBackend.SOFTWARE) {
            try {
                FfmpegHardwareVideoDecoder.Result decoded = decodeHardware(mpegTs);
                List<LegacyDecodedVideoFrame> video = new ArrayList<LegacyDecodedVideoFrame>();
                for (DecodedVideoFrame frame : decoded.frames) video.add(new LegacyDecodedVideoFrame(
                        frame.presentationTimeUs(), frame.width(), frame.height(), frame.rgbaView()));
                peakRetainedBytes = Math.max(peakRetainedBytes, decoded.peakRetainedBytes);
                LegacyDecodedMediaSegment result = new LegacyDecodedMediaSegment(video, decodeAudio(mpegTs));
                record(video.size(), started, cpuStarted, decoded.transferNanos, decoded.conversionNanos,
                        decoded.peakRetainedBytes);
                return result;
            } catch (FfmpegHardwareVideoDecoder.HardwareDecoderException | RuntimeException | LinkageError failure) {
                effectiveBackend = VideoDecoderBackend.SOFTWARE;
                fallbackReason = sanitize(failure.getMessage());
                fallbackCount++;
            }
        }
        LegacyDecodedMediaSegment result = decodeSoftware(mpegTs);
        record(result.video().size(), started, cpuStarted, 0L, 0L, peakRetainedBytes);
        return result;
    }

    private LegacyDecodedMediaSegment decodeSoftware(byte[] mpegTs) throws FrameGrabber.Exception {
        BoundedVideoFrames video = new BoundedVideoFrames(MAX_RETAINED_VIDEO_BYTES);
        List<LegacyDecodedAudioFrame> audio = new ArrayList<LegacyDecodedAudioFrame>();
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(mpegTs), 0);
        try {
            grabber.setFormat("mpegts");
            grabber.setPixelFormat(AV_PIX_FMT_RGBA);
            grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
            grabber.start();
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                long timestamp = Math.max(0L, frame.timestamp);
                if (frame.image != null && frame.image.length != 0) video.add(videoFrame(frame, timestamp));
                if (frame.samples != null && frame.samples.length != 0) {
                    audio.add(audioFrame(frame, timestamp, grabber.getSampleRate(), grabber.getAudioChannels()));
                }
            }
        } finally {
            try { grabber.close(); } catch (FrameGrabber.Exception ignored) { }
        }
        peakRetainedBytes = Math.max(peakRetainedBytes, video.peakBytes);
        return new LegacyDecodedMediaSegment(video.frames, audio);
    }

    private List<LegacyDecodedAudioFrame> decodeAudio(byte[] mpegTs) throws FrameGrabber.Exception {
        List<LegacyDecodedAudioFrame> audio = new ArrayList<LegacyDecodedAudioFrame>();
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(mpegTs), 0);
        try {
            grabber.setFormat("mpegts"); grabber.setSampleFormat(AV_SAMPLE_FMT_S16); grabber.start();
            Frame frame;
            while ((frame = grabber.grabSamples()) != null) if (frame.samples != null && frame.samples.length != 0) {
                audio.add(audioFrame(frame, Math.max(0L, frame.timestamp), grabber.getSampleRate(), grabber.getAudioChannels()));
            }
        } finally { try { grabber.close(); } catch (FrameGrabber.Exception ignored) { } }
        return audio;
    }

    private FfmpegHardwareVideoDecoder.Result decodeHardware(byte[] mpegTs)
            throws FfmpegHardwareVideoDecoder.HardwareDecoderException {
        FfmpegHardwareVideoDecoder.HardwareDecoderException last = null;
        for (VideoDecoderBackend backend : candidates()) {
            try {
                String probeFailure = probe(backend).join();
                if (!probeFailure.isEmpty()) throw new FfmpegHardwareVideoDecoder.HardwareDecoderException(probeFailure);
                FfmpegHardwareVideoDecoder.Result result = FfmpegHardwareVideoDecoder.decode(mpegTs, backend, requestedDevice);
                effectiveBackend = backend;
                return result;
            } catch (FfmpegHardwareVideoDecoder.HardwareDecoderException failure) {
                last = failure;
                if (requestedBackend != VideoDecoderBackend.AUTO) break;
            }
        }
        throw last == null ? new FfmpegHardwareVideoDecoder.HardwareDecoderException("No hardware decoder candidate") : last;
    }

    private CompletableFuture<String> probe(final VideoDecoderBackend backend) {
        final String key = backend.configValue() + '\n' + requestedDevice;
        CompletableFuture<String> existing = PROBES.get(key);
        if (existing != null) return existing;
        if (PROBES.size() >= MAX_PROBE_KEYS) return CompletableFuture.completedFuture(
                "Hardware probe cache limit reached; use a stable decoder device setting");
        CompletableFuture<String> created = PROBE_EXECUTOR.supply(() -> {
            try {
                FfmpegHardwareVideoDecoder.probe(backend, requestedDevice);
                FfmpegHardwareVideoDecoder.Result decoded = FfmpegHardwareVideoDecoder.decode(
                        DecoderProbeFixture.bytes(), backend, requestedDevice);
                return decoded.frames.isEmpty() ? "Bundled H.264 probe produced no hardware frames" : "";
            } catch (FfmpegHardwareVideoDecoder.HardwareDecoderException | RuntimeException | LinkageError failure) {
                return sanitize(failure.getMessage());
            }
        });
        existing = PROBES.putIfAbsent(key, created);
        return existing == null ? created : existing;
    }

    private List<VideoDecoderBackend> candidates() {
        if (requestedBackend != VideoDecoderBackend.AUTO) return Arrays.asList(requestedBackend);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Arrays.asList(VideoDecoderBackend.D3D11VA, VideoDecoderBackend.DXVA2);
        String vendor = detectedGpuVendor();
        if (vendor.contains("nvidia")) return Arrays.asList(VideoDecoderBackend.CUDA);
        if (vendor.contains("intel")) return Arrays.asList(VideoDecoderBackend.QSV, VideoDecoderBackend.VAAPI);
        if (vendor.contains("amd")) return Arrays.asList(VideoDecoderBackend.VAAPI);
        return java.util.Collections.emptyList();
    }

    private String detectedGpuVendor() {
        String override = System.getProperty("cinemarr.video.gpuVendor", "").trim().toLowerCase(Locale.ROOT);
        if (!override.isEmpty()) return override;
        try {
            if (!requestedDevice.isEmpty()) {
                Path name = Paths.get(requestedDevice).getFileName();
                return vendorName(new String(Files.readAllBytes(Paths.get("/sys/class/drm", name.toString(), "device/vendor")), "UTF-8").trim());
            }
            DirectoryStream<Path> nodes = Files.newDirectoryStream(Paths.get("/sys/class/drm"), "renderD*");
            try {
                for (Path node : nodes) {
                    Path vendor = node.resolve("device/vendor");
                    if (Files.isRegularFile(vendor)) {
                        return vendorName(new String(Files.readAllBytes(vendor), "UTF-8").trim());
                    }
                }
            } finally { nodes.close(); }
        } catch (Exception ignored) { }
        return "";
    }

    private static String vendorName(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        if ("0x10de".equals(value)) return "nvidia";
        if ("0x8086".equals(value)) return "intel";
        if ("0x1002".equals(value)) return "amd";
        return value;
    }

    private static void preloadHostHardwareLibraries(VideoDecoderBackend backend) {
        if (backend != VideoDecoderBackend.VAAPI && backend != VideoDecoderBackend.QSV && backend != VideoDecoderBackend.AUTO) return;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return;
        try {
            if (!loadFirstExisting("/usr/lib/libva.so.2", "/usr/lib/x86_64-linux-gnu/libva.so.2", "/usr/lib/aarch64-linux-gnu/libva.so.2")) System.loadLibrary("va");
            if (!loadFirstExisting("/usr/lib/libva-drm.so.2", "/usr/lib/x86_64-linux-gnu/libva-drm.so.2", "/usr/lib/aarch64-linux-gnu/libva-drm.so.2")) System.loadLibrary("va-drm");
        } catch (UnsatisfiedLinkError ignored) { }
    }

    private static boolean loadFirstExisting(String... paths) {
        for (String path : paths) if (Files.isRegularFile(Paths.get(path))) { System.load(path); return true; }
        return false;
    }

    private static String sanitize(String value) {
        if (value == null) return "hardware decoder unavailable";
        String clean = value.replaceAll("(?i)([A-Z]:)?[/\\\\][^ ]+", "<device>");
        return clean.length() <= 240 ? clean : clean.substring(0, 240);
    }

    private static String sanitizedDeviceType(String value) {
        if (value == null || value.trim().isEmpty()) return "default";
        String normalized = value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.matches("[0-9]+")) return "adapter-index";
        if (normalized.matches(".*/renderd[0-9]+")) return "drm-render-node";
        if (normalized.matches("[0-9a-f]{1,4}:[0-9a-f]{1,2}:[0-9a-f]{1,2}(\\.[0-7])?")) return "pci-selector";
        return "explicit-selector";
    }

    VideoDecoderBackend requestedBackend() { return requestedBackend; }
    VideoDecoderBackend effectiveBackend() { return effectiveBackend == null ? VideoDecoderBackend.SOFTWARE : effectiveBackend; }
    String deviceType() { return sanitizedDeviceType; }
    String fallbackReason() { return fallbackReason; }
    long fallbackCount() { return fallbackCount; }
    long peakRetainedBytes() { return peakRetainedBytes; }
    synchronized long decodedSegments() { return decodedSegments; }
    synchronized long decodedFrames() { return decodedFrames; }
    synchronized long wallNanos() { return wallNanos; }
    synchronized long cpuNanos() { return cpuNanos; }
    synchronized long transferNanos() { return transferNanos; }
    synchronized long conversionNanos() { return conversionNanos; }

    private synchronized void record(int frames, long started, long cpuStarted, long transfer,
                                     long conversion, long retainedBytes) {
        decodedSegments++;
        decodedFrames += frames;
        wallNanos += Math.max(0L, System.nanoTime() - started);
        long cpuFinished = currentThreadCpuTime();
        if (cpuStarted >= 0L && cpuFinished >= cpuStarted) cpuNanos += cpuFinished - cpuStarted;
        transferNanos += Math.max(0L, transfer);
        conversionNanos += Math.max(0L, conversion);
        peakRetainedBytes = Math.max(peakRetainedBytes, Math.max(0L, retainedBytes));
    }

    private static long currentThreadCpuTime() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1L;
    }

    private static void validate(byte[] mpegTs) {
        if (mpegTs == null || mpegTs.length == 0 || mpegTs.length > MAX_SEGMENT_BYTES) {
            throw new IllegalArgumentException("Invalid MPEG-TS segment size");
        }
    }

    private static LegacyDecodedVideoFrame videoFrame(Frame frame, long timestamp) {
        if (!(frame.image[0] instanceof ByteBuffer)) {
            throw new IllegalStateException("FFmpeg produced a non-byte video frame");
        }
        ByteBuffer source = ((ByteBuffer) frame.image[0]).duplicate();
        int width = frame.imageWidth;
        int height = frame.imageHeight;
        int stride = frame.imageStride;
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int y = 0; y < height; y++) {
            int row = y * stride;
            for (int x = 0; x < width; x++) {
                int input = row + x * 4;
                int output = (y * width + x) * 4;
                for (int component = 0; component < 4; component++) rgba[output + component] = source.get(input + component);
            }
        }
        return new LegacyDecodedVideoFrame(timestamp, width, height, rgba);
    }

    private static LegacyDecodedAudioFrame audioFrame(Frame frame, long timestamp, int sampleRate, int channels) {
        if (channels < 1 || channels > 2) {
            throw new IllegalStateException("Unsupported FFmpeg audio channel count " + channels);
        }
        int totalSamples = 0;
        for (Buffer value : frame.samples) {
            if (!(value instanceof ShortBuffer)) throw new IllegalStateException("FFmpeg did not honor signed 16-bit output");
            totalSamples += value.remaining();
        }
        ByteBuffer pcm = ByteBuffer.allocate(totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        if (frame.samples.length == 1) {
            ShortBuffer source = ((ShortBuffer) frame.samples[0]).duplicate();
            while (source.hasRemaining()) pcm.putShort(source.get());
        } else {
            ShortBuffer[] planes = new ShortBuffer[frame.samples.length];
            for (int index = 0; index < planes.length; index++) planes[index] = ((ShortBuffer) frame.samples[index]).duplicate();
            while (planes[0].hasRemaining()) {
                for (ShortBuffer plane : planes) if (plane.hasRemaining()) pcm.putShort(plane.get());
            }
        }
        return new LegacyDecodedAudioFrame(timestamp, sampleRate, channels, pcm.array());
    }

    private static final class BoundedVideoFrames {
        private final long maximumBytes;
        private final List<LegacyDecodedVideoFrame> frames = new ArrayList<LegacyDecodedVideoFrame>();
        private long bytes;
        private long peakBytes;
        private int dropped;
        BoundedVideoFrames(long maximumBytes) { this.maximumBytes = maximumBytes; }
        void add(LegacyDecodedVideoFrame frame) {
            frames.add(frame); bytes += frame.rgbaView().length; peakBytes = Math.max(peakBytes, bytes);
            while (bytes > maximumBytes && frames.size() > 1) {
                int remove = frames.size() == 2 ? 0 : closestInteriorFrame(); bytes -= frames.remove(remove).rgbaView().length; dropped++;
            }
        }
        int closestInteriorFrame() {
            int result = 1; long smallestSpan = Long.MAX_VALUE;
            for (int index = 1; index + 1 < frames.size(); index++) {
                long span = frames.get(index + 1).presentationTimeUs() - frames.get(index - 1).presentationTimeUs();
                if (span < smallestSpan) { smallestSpan = span; result = index; }
            }
            return result;
        }
    }
}
