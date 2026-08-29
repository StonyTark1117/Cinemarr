package stonytark.cinemarr.client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import stonytark.cinemarr.core.platform.DecoderProbeFixture;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;

/**
 * Client-only FFmpeg decoder. Plex is instructed to keyframe each HLS segment,
 * so decoding a bounded segment independently also gives seek/recovery a clean
 * generation boundary and avoids ever exposing a Plex URL to native code.
 */
public final class FfmpegVideoDecoder implements MediaSegmentDecoder {
    private static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;
    private static final long MAX_RETAINED_VIDEO_BYTES = 128L * 1024L * 1024L;
    private static final ExecutorService PROBE_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Cinemarr FFmpeg hardware probe");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, CompletableFuture<String>> PROBES = new ConcurrentHashMap<>();
    private final VideoDecoderBackend requestedBackend;
    private final String requestedDevice;
    private final String sanitizedDeviceType;
    private volatile VideoDecoderBackend effectiveBackend;
    private volatile String fallbackReason = "";
    private long decodedSegments;
    private long decodedFrames;
    private long wallNanos;
    private long cpuNanos;
    private long transferNanos;
    private long conversionNanos;
    private long fallbackCount;
    private long peakRetainedBytes;

    public FfmpegVideoDecoder() { this(VideoDecoderBackend.SOFTWARE, ""); }

    public FfmpegVideoDecoder(VideoDecoderBackend backend, String device) {
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

    private static void preloadHostHardwareLibraries(VideoDecoderBackend backend) {
        if (backend != VideoDecoderBackend.VAAPI && backend != VideoDecoderBackend.QSV
                && backend != VideoDecoderBackend.AUTO) return;
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) return;
        try {
            // JavaCPP's portable FFmpeg bundle includes a conservative libva ABI. Loading the
            // host ABI first lets current Mesa/Intel drivers resolve their matching entry point.
            if (!loadFirstExisting("/usr/lib/libva.so.2", "/usr/lib/x86_64-linux-gnu/libva.so.2",
                    "/usr/lib/aarch64-linux-gnu/libva.so.2")) System.loadLibrary("va");
            if (!loadFirstExisting("/usr/lib/libva-drm.so.2", "/usr/lib/x86_64-linux-gnu/libva-drm.so.2",
                    "/usr/lib/aarch64-linux-gnu/libva-drm.so.2")) System.loadLibrary("va-drm");
        } catch (UnsatisfiedLinkError ignored) {
            // The ordinary probe/fallback path reports an unavailable backend without crashing.
        }
    }

    private static boolean loadFirstExisting(String... paths) {
        for (String path : paths) {
            if (Files.isRegularFile(Path.of(path))) {
                System.load(path);
                return true;
            }
        }
        return false;
    }

    @Override public DecodedMediaSegment decode(byte[] mpegTs) throws FrameGrabber.Exception {
        List<DecodedAudioFrame> audio = decodeAudio(mpegTs);
        VideoDecodeResult video = decodeVideoBounded(mpegTs);
        return new DecodedMediaSegment(video.video(), audio);
    }

    /** Decodes only program audio so startup runway does not retain future RGBA frames. */
    List<DecodedAudioFrame> decodeAudio(byte[] mpegTs) throws FrameGrabber.Exception {
        validate(mpegTs);
        List<DecodedAudioFrame> audio = new ArrayList<>();
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(mpegTs), 0)) {
            configure(grabber);
            grabber.start();
            Frame frame;
            while ((frame = grabber.grabSamples()) != null) {
                if (frame.samples != null && frame.samples.length != 0) {
                    audio.add(audioFrame(frame, Math.max(0, frame.timestamp), grabber.getSampleRate(), grabber.getAudioChannels()));
                }
            }
        }
        return List.copyOf(audio);
    }

    /** Decodes video while retaining a temporally even, byte-bounded frame set. */
    VideoDecodeResult decodeVideoBounded(byte[] mpegTs) throws FrameGrabber.Exception {
        validate(mpegTs);
        long started = System.nanoTime();
        long cpuStarted = currentThreadCpuTime();
        if (requestedBackend != VideoDecoderBackend.SOFTWARE && effectiveBackend != VideoDecoderBackend.SOFTWARE) {
            try {
                FfmpegHardwareVideoDecoder.Result hardware = decodeHardware(mpegTs);
                BoundedVideoFrames bounded = new BoundedVideoFrames(MAX_RETAINED_VIDEO_BYTES);
                for (DecodedVideoFrame frame : hardware.frames) bounded.add(frame);
                effectiveBackend = selectedHardwareBackend();
                record(bounded.frames.size(), started, cpuStarted, hardware.transferNanos, hardware.conversionNanos,
                        Math.max(bounded.peakBytes, hardware.peakRetainedBytes));
                return new VideoDecodeResult(List.copyOf(bounded.frames), bounded.dropped + hardware.droppedFrames);
            } catch (FfmpegHardwareVideoDecoder.HardwareDecoderException | RuntimeException | LinkageError failure) {
                effectiveBackend = VideoDecoderBackend.SOFTWARE;
                fallbackReason = sanitize(failure.getMessage());
                fallbackCount++;
            }
        }
        BoundedVideoFrames video = new BoundedVideoFrames(MAX_RETAINED_VIDEO_BYTES);
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(mpegTs), 0)) {
            configure(grabber);
            grabber.start();
            Frame frame;
            while ((frame = grabber.grabImage()) != null) {
                if (frame.image != null && frame.image.length != 0) {
                    video.add(videoFrame(frame, Math.max(0, frame.timestamp)));
                }
            }
        }
        record(video.frames.size(), started, cpuStarted, 0L, 0L, video.peakBytes);
        return new VideoDecodeResult(List.copyOf(video.frames), video.dropped);
    }

    private FfmpegHardwareVideoDecoder.Result decodeHardware(byte[] mpegTs)
            throws FfmpegHardwareVideoDecoder.HardwareDecoderException {
        FfmpegHardwareVideoDecoder.HardwareDecoderException last = null;
        for (VideoDecoderBackend backend : candidates()) {
            try {
                String probeFailure = probe(backend).join();
                if (!probeFailure.isEmpty()) throw new FfmpegHardwareVideoDecoder.HardwareDecoderException(probeFailure);
                FfmpegHardwareVideoDecoder.Result value = FfmpegHardwareVideoDecoder.decode(mpegTs, backend, requestedDevice);
                effectiveBackend = backend;
                return value;
            } catch (FfmpegHardwareVideoDecoder.HardwareDecoderException failure) {
                last = failure;
                if (requestedBackend != VideoDecoderBackend.AUTO) break;
            }
        }
        throw last == null ? new FfmpegHardwareVideoDecoder.HardwareDecoderException("No hardware decoder candidate") : last;
    }

    private CompletableFuture<String> probe(VideoDecoderBackend backend) {
        String key = backend.configValue() + '\n' + requestedDevice;
        return PROBES.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                FfmpegHardwareVideoDecoder.probe(backend, requestedDevice);
                FfmpegHardwareVideoDecoder.Result decoded = FfmpegHardwareVideoDecoder.decode(
                        DecoderProbeFixture.bytes(), backend, requestedDevice);
                if (decoded.frames.isEmpty()) return "Bundled H.264 probe produced no hardware frames";
                return "";
            } catch (FfmpegHardwareVideoDecoder.HardwareDecoderException | RuntimeException | LinkageError failure) {
                return sanitize(failure.getMessage());
            }
        }, PROBE_EXECUTOR));
    }

    private VideoDecoderBackend selectedHardwareBackend() {
        VideoDecoderBackend value = effectiveBackend;
        return value == null || value == VideoDecoderBackend.AUTO ? VideoDecoderBackend.SOFTWARE : value;
    }

    private List<VideoDecoderBackend> candidates() {
        if (requestedBackend != VideoDecoderBackend.AUTO) return List.of(requestedBackend);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return List.of(VideoDecoderBackend.D3D11VA, VideoDecoderBackend.DXVA2);
        String vendor = detectedGpuVendor();
        if (vendor.contains("nvidia")) return List.of(VideoDecoderBackend.CUDA);
        if (vendor.contains("intel")) return List.of(VideoDecoderBackend.QSV, VideoDecoderBackend.VAAPI);
        if (vendor.contains("amd") || vendor.contains("ati") || vendor.contains("radeon")) return List.of(VideoDecoderBackend.VAAPI);
        return List.of();
    }

    private String detectedGpuVendor() {
        String override = System.getProperty("cinemarr.video.gpuVendor", "").trim().toLowerCase(Locale.ROOT);
        if (!override.isEmpty()) return override;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return "";
        try {
            String node = requestedDevice.isEmpty() ? "" : Path.of(requestedDevice).getFileName().toString();
            if (!node.isEmpty()) {
                String vendor = Files.readString(Path.of("/sys/class/drm", node, "device/vendor")).trim();
                return vendorName(vendor);
            }
            try (java.nio.file.DirectoryStream<Path> nodes = Files.newDirectoryStream(Path.of("/sys/class/drm"), "renderD*")) {
                for (Path path : nodes) {
                    Path vendorFile = path.resolve("device/vendor");
                    if (Files.isRegularFile(vendorFile)) return vendorName(Files.readString(vendorFile).trim());
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String vendorName(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (normalized.equals("0x10de")) return "nvidia";
        if (normalized.equals("0x8086")) return "intel";
        if (normalized.equals("0x1002")) return "amd";
        return normalized;
    }

    public synchronized DecoderDiagnostics diagnostics() {
        return new DecoderDiagnostics(requestedBackend, effectiveBackend == null ? VideoDecoderBackend.SOFTWARE : effectiveBackend,
                sanitizedDeviceType, fallbackReason, decodedSegments, decodedFrames, wallNanos, cpuNanos,
                transferNanos, conversionNanos, fallbackCount, peakRetainedBytes);
    }

    private synchronized void record(int frames, long started, long cpuStarted, long transfer, long conversion, long retainedBytes) {
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

    private static void validate(byte[] mpegTs) {
        if (mpegTs == null || mpegTs.length == 0 || mpegTs.length > MAX_SEGMENT_BYTES) {
            throw new IllegalArgumentException("Invalid MPEG-TS segment size");
        }
    }

    private static void configure(FFmpegFrameGrabber grabber) {
        grabber.setFormat("mpegts");
        grabber.setPixelFormat(AV_PIX_FMT_RGBA);
        grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
    }

    private static DecodedVideoFrame videoFrame(Frame frame, long timestamp) {
        if (!(frame.image[0] instanceof ByteBuffer source)) throw new IllegalStateException("FFmpeg produced a non-byte video frame");
        int width = frame.imageWidth, height = frame.imageHeight, stride = frame.imageStride;
        ByteBuffer pixels = source.duplicate();
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        for (int y = 0; y < height; y++) {
            int row = y * stride;
            for (int x = 0; x < width; x++) {
                int input = row + x * 4, output = (y * width + x) * 4;
                pixels.get(input, rgba, output, 4);
            }
        }
        return new DecodedVideoFrame(timestamp, width, height, rgba);
    }

    private static DecodedAudioFrame audioFrame(Frame frame, long timestamp, int sampleRate, int channels) {
        if (channels < 1 || channels > 2) throw new IllegalStateException("Unsupported FFmpeg audio channel count " + channels);
        int totalSamples = 0;
        for (Buffer value : frame.samples) {
            if (!(value instanceof ShortBuffer)) throw new IllegalStateException("FFmpeg did not honor signed 16-bit output");
            totalSamples += value.remaining();
        }
        ByteBuffer pcm = ByteBuffer.allocate(totalSamples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (frame.samples.length == 1) {
            ShortBuffer source = ((ShortBuffer) frame.samples[0]).duplicate();
            while (source.hasRemaining()) pcm.putShort(source.get());
        } else {
            ShortBuffer[] planes = new ShortBuffer[frame.samples.length];
            for (int index = 0; index < planes.length; index++) planes[index] = ((ShortBuffer) frame.samples[index]).duplicate();
            while (planes[0].hasRemaining()) for (ShortBuffer plane : planes) if (plane.hasRemaining()) pcm.putShort(plane.get());
        }
        return new DecodedAudioFrame(timestamp, sampleRate, channels, pcm.array());
    }

    record VideoDecodeResult(List<DecodedVideoFrame> video, int droppedFrames) {}

    public record DecoderDiagnostics(VideoDecoderBackend requestedBackend, VideoDecoderBackend effectiveBackend,
                                     String deviceType, String fallbackReason, long decodedSegments, long decodedFrames,
                                     long wallNanos, long cpuNanos, long transferNanos, long conversionNanos,
                                     long fallbackCount, long peakRetainedBytes) {}

    private static final class BoundedVideoFrames {
        private final long maximumBytes;
        private final List<DecodedVideoFrame> frames = new ArrayList<>();
        private long bytes;
        private long peakBytes;
        private int dropped;

        private BoundedVideoFrames(long maximumBytes) { this.maximumBytes = maximumBytes; }

        private void add(DecodedVideoFrame frame) {
            frames.add(frame);
            bytes += frame.rgbaView().length;
            peakBytes = Math.max(peakBytes, bytes);
            while (bytes > maximumBytes && frames.size() > 1) {
                int remove = frames.size() == 2 ? 0 : closestInteriorFrame();
                bytes -= frames.remove(remove).rgbaView().length;
                dropped++;
            }
        }

        private int closestInteriorFrame() {
            int result = 1;
            long smallestSpan = Long.MAX_VALUE;
            for (int index = 1; index + 1 < frames.size(); index++) {
                long span = frames.get(index + 1).presentationTimeUs() - frames.get(index - 1).presentationTimeUs();
                if (span < smallestSpan) { smallestSpan = span; result = index; }
            }
            return result;
        }
    }
}
