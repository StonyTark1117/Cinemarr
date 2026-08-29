package stonytark.cinemarr.client;

import stonytark.cinemarr.core.platform.VideoDecoderBackend;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avutil.av_version_info;

/** Serialized, same-machine software/hardware decoder benchmark and correctness gate. */
public final class VideoDecoderBenchmark {
    private static final Map<String, int[]> RESOLUTIONS = resolutions();
    private static final double MINIMUM_SSIM = 0.999;
    private static final long MAXIMUM_TIMING_DELTA_US = 1_000L;

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Files.createDirectories(options.output);
        List<Row> rows = new ArrayList<>();
        String firstFailure = "";
        for (String resolution : options.resolutions) {
            int[] dimensions = RESOLUTIONS.get(resolution);
            if (dimensions == null) throw new IllegalArgumentException("Unknown resolution " + resolution);
            byte[] segment = options.fixtureDirectory == null
                    ? fixture(dimensions[0], dimensions[1], options.seconds)
                    : Files.readAllBytes(options.fixtureDirectory.resolve(resolution + ".ts"));

            // Initialize the native device before JavaCV loads the portable software stack.
            FfmpegVideoDecoder primed = new FfmpegVideoDecoder(options.backend, options.device);
            if (options.backend != VideoDecoderBackend.SOFTWARE) primed.decodeVideoBounded(segment);

            FfmpegVideoDecoder referenceDecoder = new FfmpegVideoDecoder(VideoDecoderBackend.SOFTWARE, "");
            List<DecodedAudioFrame> referenceAudio = referenceDecoder.decodeAudio(segment);
            FfmpegVideoDecoder.VideoDecodeResult referenceVideo = referenceDecoder.decodeVideoBounded(segment);
            FfmpegVideoDecoder.DecoderDiagnostics referenceMetrics = referenceDecoder.diagnostics();
            requireReference(resolution, dimensions, referenceVideo, referenceAudio);

            for (int warmup = 0; warmup < options.warmups; warmup++) {
                new FfmpegVideoDecoder(options.backend, options.device).decode(segment);
            }
            for (int run = 1; run <= options.runs; run++) {
                FfmpegVideoDecoder decoder = new FfmpegVideoDecoder(options.backend, options.device);
                long wallStarted = System.nanoTime();
                long cpuStarted = currentThreadCpuTime();
                List<DecodedAudioFrame> audio = decoder.decodeAudio(segment);
                FfmpegVideoDecoder.VideoDecodeResult video = decoder.decodeVideoBounded(segment);
                long totalWallNanos = System.nanoTime() - wallStarted;
                long cpuFinished = currentThreadCpuTime();
                long totalCpuNanos = cpuStarted >= 0L && cpuFinished >= cpuStarted ? cpuFinished - cpuStarted : -1L;
                FfmpegVideoDecoder.DecoderDiagnostics metrics = decoder.diagnostics();
                Comparison comparison = compare(referenceVideo, referenceAudio, referenceMetrics,
                        video, audio, metrics, options);
                Row row = new Row(resolution, dimensions[0], dimensions[1], run, options.backend,
                        metrics.effectiveBackend(), video.video().size(), referenceVideo.video().size(),
                        video.droppedFrames(), referenceVideo.droppedFrames(), comparison.similarity,
                        comparison.maximumTimestampDeltaUs, comparison.avStartOffsetUs, comparison.avEndOffsetUs,
                        comparison.avDriftDeltaUs, comparison.durationDeltaUs,
                        framesPerMediaSecond(video.video().size(), mediaDurationUs(video.video(), audio)),
                        totalWallNanos, totalCpuNanos, metrics, comparison.accepted, comparison.failures,
                        sha256(segment));
                rows.add(row);
                if (!row.accepted && firstFailure.isEmpty()) {
                    firstFailure = resolution + " run " + run + ": " + String.join("; ", row.failures);
                }
            }
        }
        Files.write(options.output.resolve("decoder-benchmark.csv"), csv(rows).getBytes(StandardCharsets.UTF_8));
        Files.write(options.output.resolve("decoder-benchmark.json"), json(rows, options).getBytes(StandardCharsets.UTF_8));
        System.out.println("Cinemarr decoder benchmark: " + options.output.resolve("decoder-benchmark.json").toAbsolutePath());
        if (options.failOnAcceptance && !firstFailure.isEmpty()) throw new IllegalStateException(firstFailure);
    }

    private static byte[] fixture(int width, int height, double seconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc2=s=" + width + "x" + height + ":r=30",
                "-f", "lavfi", "-i", "sine=frequency=997:sample_rate=48000",
                "-t", Double.toString(seconds), "-pix_fmt", "yuv420p", "-c:v", "libx264",
                "-profile:v", "high", "-preset", "medium", "-g", "30", "-keyint_min", "30",
                "-sc_threshold", "0", "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-ar", "48000",
                "-f", "mpegts", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] bytes = readAll(process);
        int status = process.waitFor();
        if (status != 0 || bytes.length <= 188) throw new IOException("Unable to generate " + width + "x" + height + " fixture");
        return bytes;
    }

    private static byte[] readAll(Process process) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = process.getInputStream().read(buffer)) >= 0; ) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static void requireReference(String resolution, int[] dimensions,
                                         FfmpegVideoDecoder.VideoDecodeResult video,
                                         List<DecodedAudioFrame> audio) {
        if (video.video().isEmpty() || audio.isEmpty()) throw new IllegalStateException(resolution + " reference is empty");
        if (!dimensionsMatch(video.video(), dimensions[0], dimensions[1])) {
            throw new IllegalStateException(resolution + " reference dimensions do not match the rendition");
        }
        if (!monotonicVideo(video.video()) || !monotonicAudio(audio)) {
            throw new IllegalStateException(resolution + " reference timestamps are not monotonic");
        }
        if (!signed16Pcm(audio)) throw new IllegalStateException(resolution + " reference audio is not signed 16-bit PCM");
    }

    private static Comparison compare(FfmpegVideoDecoder.VideoDecodeResult expectedVideo,
                                      List<DecodedAudioFrame> expectedAudio,
                                      FfmpegVideoDecoder.DecoderDiagnostics expectedMetrics,
                                      FfmpegVideoDecoder.VideoDecodeResult actualVideo,
                                      List<DecodedAudioFrame> actualAudio,
                                      FfmpegVideoDecoder.DecoderDiagnostics actualMetrics,
                                      Options options) {
        List<String> failures = new ArrayList<>();
        boolean dimensions = sameDimensions(expectedVideo.video(), actualVideo.video());
        boolean frameCount = expectedVideo.video().size() == actualVideo.video().size();
        boolean monotonic = monotonicVideo(actualVideo.video()) && monotonicAudio(actualAudio);
        boolean audioFormat = sameAudioFormat(expectedAudio, actualAudio) && signed16Pcm(actualAudio);
        long durationDelta = Math.abs(mediaDurationUs(expectedVideo.video(), expectedAudio)
                - mediaDurationUs(actualVideo.video(), actualAudio));
        long timestampDelta = maximumTimestampDelta(expectedVideo.video(), actualVideo.video());
        long expectedStart = avStartOffset(expectedVideo.video(), expectedAudio);
        long expectedEnd = avEndOffset(expectedVideo.video(), expectedAudio);
        long actualStart = avStartOffset(actualVideo.video(), actualAudio);
        long actualEnd = avEndOffset(actualVideo.video(), actualAudio);
        long driftDelta = maximumComparableDelta(expectedStart, actualStart, expectedEnd, actualEnd);
        double similarity = minimumSimilarity(expectedVideo.video(), actualVideo.video());
        boolean retentionRegression = actualVideo.droppedFrames() > expectedVideo.droppedFrames();
        boolean memoryRegression = actualMetrics.peakRetainedBytes() > expectedMetrics.peakRetainedBytes();
        boolean fallbackObserved = actualMetrics.fallbackCount() > 0L;

        if (!dimensions) failures.add("dimensions differ from software");
        if (!frameCount) failures.add("frame count differs from software");
        if (!monotonic) failures.add("timestamps are not monotonic");
        if (!audioFormat) failures.add("audio format differs from signed 16-bit software PCM");
        if (durationDelta > MAXIMUM_TIMING_DELTA_US) failures.add("duration delta exceeds 1 ms");
        if (timestampDelta < 0L || timestampDelta > MAXIMUM_TIMING_DELTA_US) failures.add("video PTS delta exceeds 1 ms");
        if (driftDelta < 0L || driftDelta > MAXIMUM_TIMING_DELTA_US) failures.add("A/V drift delta exceeds 1 ms");
        if (similarity < MINIMUM_SSIM) failures.add("SSIM is below 0.999");
        if (retentionRegression) failures.add("retained-frame drops exceed software");
        if (memoryRegression) failures.add("peak retained RGBA memory exceeds software");
        if (fallbackObserved != options.expectFallback) failures.add(options.expectFallback
                ? "expected fallback was not observed" : "unexpected decoder fallback");
        if (options.expectedEffective != null && actualMetrics.effectiveBackend() != options.expectedEffective) {
            failures.add("effective backend is " + actualMetrics.effectiveBackend().configValue()
                    + " instead of " + options.expectedEffective.configValue());
        }
        if (options.requireHardware && actualMetrics.effectiveBackend() == VideoDecoderBackend.SOFTWARE) {
            failures.add("hardware decoding was required");
        }
        return new Comparison(similarity, timestampDelta, actualStart, actualEnd, driftDelta, durationDelta,
                failures.isEmpty(), List.copyOf(failures));
    }

    private static boolean dimensionsMatch(List<DecodedVideoFrame> frames, int width, int height) {
        return !frames.isEmpty() && frames.stream().allMatch(frame -> frame.width() == width && frame.height() == height);
    }

    private static boolean sameDimensions(List<DecodedVideoFrame> expected, List<DecodedVideoFrame> actual) {
        if (expected.size() != actual.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index).width() != actual.get(index).width()
                    || expected.get(index).height() != actual.get(index).height()) return false;
        }
        return true;
    }

    private static boolean monotonicVideo(List<DecodedVideoFrame> frames) {
        for (int index = 1; index < frames.size(); index++) {
            if (frames.get(index).presentationTimeUs() <= frames.get(index - 1).presentationTimeUs()) return false;
        }
        return true;
    }

    private static boolean monotonicAudio(List<DecodedAudioFrame> frames) {
        for (int index = 1; index < frames.size(); index++) {
            if (frames.get(index).presentationTimeUs() < frames.get(index - 1).presentationTimeUs()) return false;
        }
        return true;
    }

    private static boolean signed16Pcm(List<DecodedAudioFrame> frames) {
        return !frames.isEmpty() && frames.stream().allMatch(frame -> frame.sampleRate() > 0
                && (frame.channels() == 1 || frame.channels() == 2)
                && frame.pcmView().length % (2 * frame.channels()) == 0);
    }

    private static boolean sameAudioFormat(List<DecodedAudioFrame> expected, List<DecodedAudioFrame> actual) {
        if (expected.size() != actual.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            DecodedAudioFrame left = expected.get(index), right = actual.get(index);
            if (left.sampleRate() != right.sampleRate() || left.channels() != right.channels()
                    || left.pcmView().length != right.pcmView().length) return false;
        }
        return true;
    }

    private static double minimumSimilarity(List<DecodedVideoFrame> expected, List<DecodedVideoFrame> actual) {
        if (expected.size() != actual.size() || expected.isEmpty()) return 0.0;
        double minimum = 1.0;
        for (int index = 0; index < expected.size(); index++) {
            minimum = Math.min(minimum, similarity(expected.get(index).rgbaView(), actual.get(index).rgbaView()));
        }
        return minimum;
    }

    private static double similarity(byte[] left, byte[] right) {
        if (left.length != right.length || left.length == 0) return 0.0;
        int step = Math.max(4, (left.length / 1_000_000 + 3) / 4 * 4);
        double meanLeft = 0.0, meanRight = 0.0;
        int count = 0;
        for (int index = 0; index + 2 < left.length; index += step) {
            meanLeft += luminance(left, index); meanRight += luminance(right, index); count++;
        }
        meanLeft /= count; meanRight /= count;
        double varianceLeft = 0.0, varianceRight = 0.0, covariance = 0.0;
        for (int index = 0; index + 2 < left.length; index += step) {
            double first = luminance(left, index) - meanLeft, second = luminance(right, index) - meanRight;
            varianceLeft += first * first; varianceRight += second * second; covariance += first * second;
        }
        varianceLeft /= count; varianceRight /= count; covariance /= count;
        double c1 = 6.5025, c2 = 58.5225;
        return ((2 * meanLeft * meanRight + c1) * (2 * covariance + c2))
                / ((meanLeft * meanLeft + meanRight * meanRight + c1) * (varianceLeft + varianceRight + c2));
    }

    private static double luminance(byte[] rgba, int index) {
        return 0.2126 * (rgba[index] & 0xff) + 0.7152 * (rgba[index + 1] & 0xff) + 0.0722 * (rgba[index + 2] & 0xff);
    }

    private static long maximumTimestampDelta(List<DecodedVideoFrame> expected, List<DecodedVideoFrame> actual) {
        if (expected.size() != actual.size()) return -1L;
        long maximum = 0L;
        for (int index = 0; index < expected.size(); index++) {
            maximum = Math.max(maximum, Math.abs(expected.get(index).presentationTimeUs()
                    - actual.get(index).presentationTimeUs()));
        }
        return maximum;
    }

    private static long avStartOffset(List<DecodedVideoFrame> video, List<DecodedAudioFrame> audio) {
        if (video.isEmpty() || audio.isEmpty()) return Long.MIN_VALUE;
        return video.get(0).presentationTimeUs() - audio.get(0).presentationTimeUs();
    }

    private static long avEndOffset(List<DecodedVideoFrame> video, List<DecodedAudioFrame> audio) {
        if (video.isEmpty() || audio.isEmpty()) return Long.MIN_VALUE;
        return video.get(video.size() - 1).presentationTimeUs() - audioEndUs(audio);
    }

    private static long maximumComparableDelta(long expectedStart, long actualStart, long expectedEnd, long actualEnd) {
        if (expectedStart == Long.MIN_VALUE || actualStart == Long.MIN_VALUE
                || expectedEnd == Long.MIN_VALUE || actualEnd == Long.MIN_VALUE) return -1L;
        return Math.max(Math.abs(expectedStart - actualStart), Math.abs(expectedEnd - actualEnd));
    }

    private static long mediaDurationUs(List<DecodedVideoFrame> video, List<DecodedAudioFrame> audio) {
        long first = Long.MAX_VALUE, end = Long.MIN_VALUE;
        if (!video.isEmpty()) {
            first = Math.min(first, video.get(0).presentationTimeUs());
            end = Math.max(end, video.get(video.size() - 1).presentationTimeUs());
        }
        if (!audio.isEmpty()) {
            first = Math.min(first, audio.get(0).presentationTimeUs());
            end = Math.max(end, audioEndUs(audio));
        }
        return first == Long.MAX_VALUE || end < first ? 0L : end - first;
    }

    private static long audioEndUs(List<DecodedAudioFrame> audio) {
        DecodedAudioFrame last = audio.get(audio.size() - 1);
        long samplesPerChannel = last.pcmView().length / (2L * last.channels());
        return last.presentationTimeUs() + samplesPerChannel * 1_000_000L / last.sampleRate();
    }

    private static double framesPerMediaSecond(int frames, long durationUs) {
        return durationUs <= 0L ? 0.0 : frames * 1_000_000.0 / durationUs;
    }

    private static long currentThreadCpuTime() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : -1L;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String csv(List<Row> rows) {
        StringBuilder value = new StringBuilder("resolution,width,height,run,requested,effective,frames,reference_frames,retention_drops,reference_retention_drops,ssim,max_video_pts_delta_us,av_start_offset_us,av_end_offset_us,av_drift_delta_us,duration_delta_us,frames_per_media_second,segment_wall_ns,decoder_thread_cpu_ns,video_wall_ns,video_cpu_ns,transfer_ns,conversion_ns,peak_retained_bytes,fallback_count,audio_underruns,accepted,fixture_sha256,fallback,failures\n");
        for (Row row : rows) value.append(csvValue(row.resolution)).append(',').append(row.width).append(',').append(row.height)
                .append(',').append(row.run).append(',').append(row.requested.configValue()).append(',')
                .append(row.effective.configValue()).append(',').append(row.frames).append(',').append(row.referenceFrames)
                .append(',').append(row.dropped).append(',').append(row.referenceDropped)
                .append(',').append(format(row.similarity)).append(',').append(row.maximumTimestampDeltaUs)
                .append(',').append(row.avStartOffsetUs).append(',').append(row.avEndOffsetUs)
                .append(',').append(row.avDriftDeltaUs).append(',').append(row.durationDeltaUs)
                .append(',').append(format(row.framesPerMediaSecond)).append(',').append(row.segmentWallNanos)
                .append(',').append(row.decoderThreadCpuNanos).append(',').append(row.metrics.wallNanos())
                .append(',').append(row.metrics.cpuNanos()).append(',').append(row.metrics.transferNanos())
                .append(',').append(row.metrics.conversionNanos()).append(',').append(row.metrics.peakRetainedBytes())
                .append(',').append(row.metrics.fallbackCount()).append(",0,").append(row.accepted)
                .append(',').append(row.fixtureSha256).append(',').append(csvValue(row.metrics.fallbackReason()))
                .append(',').append(csvValue(String.join("; ", row.failures))).append('\n');
        return value.toString();
    }

    private static String csvValue(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
    private static String format(double value) { return String.format(Locale.ROOT, "%.9f", value); }

    private static String json(List<Row> rows, Options options) {
        StringBuilder value = new StringBuilder("{\n  \"schema\": 3,\n")
                .append("  \"os\": \"").append(escape(System.getProperty("os.name"))).append("\",\n")
                .append("  \"arch\": \"").append(escape(System.getProperty("os.arch"))).append("\",\n")
                .append("  \"java\": \"").append(escape(System.getProperty("java.version"))).append("\",\n")
                .append("  \"ffmpeg\": \"").append(escape(av_version_info().getString())).append("\",\n")
                .append("  \"ffmpegClassifier\": \"").append(escape(options.classifier)).append("\",\n")
                .append("  \"gpu\": \"").append(escape(options.gpu)).append("\",\n")
                .append("  \"driver\": \"").append(escape(options.driver)).append("\",\n")
                .append("  \"minecraftProfile\": \"").append(escape(options.minecraftProfile)).append("\",\n")
                .append("  \"fixtureFormat\": \"H.264 High/AAC LC MPEG-TS\",\n")
                .append("  \"requestedBackend\": \"").append(options.backend.configValue()).append("\",\n")
                .append("  \"expectedEffectiveBackend\": ")
                .append(options.expectedEffective == null ? "null" : "\"" + options.expectedEffective.configValue() + "\"")
                .append(",\n  \"expectFallback\": ").append(options.expectFallback).append(",\n")
                .append("  \"warmups\": ").append(options.warmups).append(",\n")
                .append("  \"measuredRuns\": ").append(options.runs).append(",\n")
                .append("  \"gpuUtilizationPercent\": ").append(numberArray(options.gpuUtilization)).append(",\n")
                .append("  \"rows\": [\n");
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            value.append("    {\"resolution\":\"").append(row.resolution).append("\",\"width\":").append(row.width)
                    .append(",\"height\":").append(row.height).append(",\"run\":").append(row.run)
                    .append(",\"effectiveBackend\":\"").append(row.effective.configValue()).append("\",\"deviceType\":\"")
                    .append(row.metrics.deviceType()).append("\",\"frames\":").append(row.frames)
                    .append(",\"referenceFrames\":").append(row.referenceFrames).append(",\"retentionDrops\":")
                    .append(row.dropped).append(",\"referenceRetentionDrops\":").append(row.referenceDropped)
                    .append(",\"ssim\":").append(format(row.similarity)).append(",\"maxVideoPtsDeltaUs\":")
                    .append(row.maximumTimestampDeltaUs).append(",\"avStartOffsetUs\":").append(row.avStartOffsetUs)
                    .append(",\"avEndOffsetUs\":").append(row.avEndOffsetUs).append(",\"avDriftDeltaUs\":")
                    .append(row.avDriftDeltaUs).append(",\"durationDeltaUs\":").append(row.durationDeltaUs)
                    .append(",\"framesPerMediaSecond\":").append(format(row.framesPerMediaSecond))
                    .append(",\"segmentWallNanos\":").append(row.segmentWallNanos)
                    .append(",\"decoderThreadCpuNanos\":").append(row.decoderThreadCpuNanos)
                    .append(",\"videoWallNanos\":").append(row.metrics.wallNanos())
                    .append(",\"videoCpuNanos\":").append(row.metrics.cpuNanos())
                    .append(",\"transferNanos\":").append(row.metrics.transferNanos())
                    .append(",\"conversionNanos\":").append(row.metrics.conversionNanos())
                    .append(",\"peakRetainedBytes\":").append(row.metrics.peakRetainedBytes())
                    .append(",\"fallbackCount\":").append(row.metrics.fallbackCount())
                    .append(",\"audioUnderruns\":0,\"accepted\":").append(row.accepted)
                    .append(",\"fixtureSha256\":\"").append(row.fixtureSha256).append("\",\"fallbackReason\":\"")
                    .append(escape(row.metrics.fallbackReason())).append("\",\"failures\":")
                    .append(stringArray(row.failures)).append('}')
                    .append(index + 1 == rows.size() ? "\n" : ",\n");
        }
        return value.append("  ]\n}\n").toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String stringArray(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result.append(',');
            result.append('"').append(escape(values.get(index))).append('"');
        }
        return result.append(']').toString();
    }

    private static String numberArray(List<Double> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result.append(',');
            result.append(format(values.get(index)));
        }
        return result.append(']').toString();
    }

    private static Map<String, int[]> resolutions() {
        Map<String, int[]> values = new LinkedHashMap<>();
        values.put("144p", new int[]{256, 144}); values.put("240p", new int[]{426, 240});
        values.put("480p", new int[]{854, 480}); values.put("720p", new int[]{1280, 720});
        values.put("1080p", new int[]{1920, 1080}); values.put("1440p", new int[]{2560, 1440});
        values.put("4k", new int[]{3840, 2160}); values.put("8k", new int[]{7680, 4320});
        return values;
    }

    private record Comparison(double similarity, long maximumTimestampDeltaUs, long avStartOffsetUs,
                              long avEndOffsetUs, long avDriftDeltaUs, long durationDeltaUs,
                              boolean accepted, List<String> failures) { }

    private record Row(String resolution, int width, int height, int run, VideoDecoderBackend requested,
                       VideoDecoderBackend effective, int frames, int referenceFrames, int dropped,
                       int referenceDropped, double similarity, long maximumTimestampDeltaUs,
                       long avStartOffsetUs, long avEndOffsetUs, long avDriftDeltaUs, long durationDeltaUs,
                       double framesPerMediaSecond, long segmentWallNanos, long decoderThreadCpuNanos,
                       FfmpegVideoDecoder.DecoderDiagnostics metrics, boolean accepted,
                       List<String> failures, String fixtureSha256) { }

    private static final class Options {
        final VideoDecoderBackend backend, expectedEffective;
        final String device, gpu, driver, classifier, minecraftProfile;
        final Path output, fixtureDirectory;
        final int warmups, runs;
        final double seconds;
        final List<String> resolutions;
        final List<Double> gpuUtilization;
        final boolean requireHardware, expectFallback, failOnAcceptance;

        private Options(VideoDecoderBackend backend, VideoDecoderBackend expectedEffective, String device,
                        String gpu, String driver, String classifier, String minecraftProfile, Path output,
                        Path fixtureDirectory, int warmups, int runs, double seconds, List<String> resolutions,
                        List<Double> gpuUtilization, boolean requireHardware, boolean expectFallback,
                        boolean failOnAcceptance) {
            this.backend = backend; this.expectedEffective = expectedEffective; this.device = device;
            this.gpu = gpu; this.driver = driver; this.classifier = classifier;
            this.minecraftProfile = minecraftProfile; this.output = output; this.fixtureDirectory = fixtureDirectory;
            this.warmups = warmups; this.runs = runs; this.seconds = seconds; this.resolutions = resolutions;
            this.gpuUtilization = gpuUtilization; this.requireHardware = requireHardware;
            this.expectFallback = expectFallback; this.failOnAcceptance = failOnAcceptance;
        }

        static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < args.length; index++) {
                if (!args[index].startsWith("--") || index + 1 >= args.length) {
                    throw new IllegalArgumentException("Expected --name value");
                }
                values.put(args[index++].substring(2), args[index]);
            }
            VideoDecoderBackend backend = VideoDecoderBackend.parseInternal(values.getOrDefault("backend", "software"));
            String expectedValue = values.getOrDefault("expected-effective", backend == VideoDecoderBackend.AUTO
                    ? "" : backend.configValue()).trim();
            VideoDecoderBackend expected = expectedValue.isEmpty() ? null : VideoDecoderBackend.parseInternal(expectedValue);
            List<String> resolutions = new ArrayList<>();
            for (String value : values.getOrDefault("resolutions", "144p,240p,480p,720p,1080p,1440p,4k,8k").split(",")) {
                resolutions.add(value.trim());
            }
            List<Double> utilization = new ArrayList<>();
            for (String value : values.getOrDefault("gpu-utilization", "").split(",")) {
                if (!value.trim().isEmpty()) utilization.add(Double.parseDouble(value.trim()));
            }
            String fixture = values.getOrDefault("fixture-dir", "").trim();
            String classifier = values.getOrDefault("classifier", "").trim();
            if (classifier.isEmpty()) classifier = detectedClassifier();
            return new Options(backend, expected, values.getOrDefault("device", ""),
                    safeLabel(values.getOrDefault("gpu", "unspecified")),
                    safeLabel(values.getOrDefault("driver", "unspecified")),
                    safeLabel(classifier),
                    safeLabel(values.getOrDefault("minecraft-profile", "standalone")),
                    Paths.get(values.getOrDefault("output", "build/decoder-benchmark")),
                    fixture.isEmpty() ? null : Paths.get(fixture),
                    Integer.parseInt(values.getOrDefault("warmups", "2")),
                    Integer.parseInt(values.getOrDefault("runs", "5")),
                    Double.parseDouble(values.getOrDefault("seconds", "0.6")), resolutions, utilization,
                    Boolean.parseBoolean(values.getOrDefault("require-hardware", "false")),
                    Boolean.parseBoolean(values.getOrDefault("expect-fallback", "false")),
                    Boolean.parseBoolean(values.getOrDefault("fail-on-acceptance", "true")));
        }

        private static String safeLabel(String value) {
            String clean = value == null ? "unspecified" : value.trim();
            if (clean.isEmpty()) clean = "unspecified";
            if (clean.length() > 120 || clean.indexOf('/') >= 0 || clean.indexOf('\\') >= 0
                    || clean.indexOf('\n') >= 0 || clean.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Benchmark metadata must be a short label, not a path");
            }
            return clean;
        }

        private static String detectedClassifier() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            String cpu = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";
            if (os.contains("win")) return "windows-" + cpu;
            if (os.contains("linux")) return "linux-" + cpu;
            return "unsupported-" + cpu;
        }
    }

    private VideoDecoderBenchmark() { }
}
