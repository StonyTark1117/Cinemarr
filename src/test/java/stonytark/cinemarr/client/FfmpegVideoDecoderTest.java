package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import stonytark.cinemarr.core.platform.DecoderProbeFixture;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_license;

final class FfmpegVideoDecoderTest {
    @Test
    void decodesSyntheticH264AacTransportStreamWithDeterministicFirstFrame() throws Exception {
        String nativeLicense = avcodec_license().getString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(nativeLicense.startsWith("lgpl"), nativeLicense);
        assertFalse(nativeLicense.startsWith("gpl"), nativeLicense);
        byte[] segment = syntheticSegment();
        FfmpegVideoDecoder decoder = new FfmpegVideoDecoder();
        DecodedMediaSegment decoded = decoder.decode(segment);

        assertTrue(decoded.video().size() >= 2, "video frames");
        assertFalse(decoded.audio().isEmpty(), "audio frames");
        DecodedVideoFrame first = decoded.video().getFirst();
        assertEquals(16, first.width());
        assertEquals(16, first.height());
        assertEquals(16 * 16 * 4, first.rgba().length);
        assertEquals(48_000, decoded.audio().getFirst().sampleRate());
        assertEquals(1, decoded.audio().getFirst().channels());

        // This catches BGR/RGB swaps, stride mistakes, and nondeterministic
        // texture conversion without baking a large media fixture into Git.
        assertEquals(frameHash(first), frameHash(decoder.decode(segment).video().getFirst()));
        assertTrue(Byte.toUnsignedInt(first.rgba()[0]) > Byte.toUnsignedInt(first.rgba()[2]), "red test frame remains red after BGR conversion");
    }

    @Test
    void separatesAudioRunwayFromByteBoundedHighResolutionVideo() throws Exception {
        byte[] segment = syntheticSegment(1280, 720, 30, "1.3");
        FfmpegVideoDecoder decoder = new FfmpegVideoDecoder();
        assertFalse(decoder.decodeAudio(segment).isEmpty(), "audio-only decode");
        FfmpegVideoDecoder.VideoDecodeResult video = decoder.decodeVideoBounded(segment);
        long retained = video.video().stream().mapToLong(frame -> frame.rgbaView().length).sum();
        assertFalse(video.video().isEmpty(), "video-only decode");
        assertTrue(video.droppedFrames() > 0, "high-resolution decode must shed frames before retaining an unbounded segment");
        assertTrue(retained <= 128L * 1024L * 1024L, "retained RGBA bytes");
        assertTrue(video.video().getFirst().presentationTimeUs()
                < video.video().getLast().presentationTimeUs(), "bounded sampling preserves the segment time span");
    }

    @Test
    void bundledProbeFixtureDecodesThroughLoaderNeutralSoftwareContract() throws Exception {
        MediaSegmentDecoder decoder = new FfmpegVideoDecoder(VideoDecoderBackend.SOFTWARE, "");
        DecodedMediaSegment decoded = decoder.decode(DecoderProbeFixture.bytes());
        assertFalse(decoded.video().isEmpty());
        assertFalse(decoded.audio().isEmpty());
        assertEquals(64, decoded.video().getFirst().width());
        assertEquals(48_000, decoded.audio().getFirst().sampleRate());
    }

    @Test
    void unknownLinuxAutoSelectionUsesSoftwareWithoutARecovery() throws Exception {
        String previous = System.getProperty("cinemarr.video.gpuVendor");
        try {
            System.setProperty("cinemarr.video.gpuVendor", "unknown");
            FfmpegVideoDecoder decoder = new FfmpegVideoDecoder(VideoDecoderBackend.AUTO, "secret/device/path");
            assertFalse(decoder.decodeVideoBounded(syntheticSegment()).video().isEmpty());
            assertEquals(VideoDecoderBackend.SOFTWARE, decoder.diagnostics().effectiveBackend());
            assertEquals(0L, decoder.diagnostics().fallbackCount());
            assertEquals("explicit-selector", decoder.diagnostics().deviceType());
            assertFalse(decoder.diagnostics().toString().contains("secret/device/path"));
        } finally {
            if (previous == null) System.clearProperty("cinemarr.video.gpuVendor");
            else System.setProperty("cinemarr.video.gpuVendor", previous);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CINEMARR_HARDWARE_VIDEO_TEST", matches = "true")
    void hardwareBackendDecodesSyntheticProgramAndReportsMetrics() throws Exception {
        byte[] segment = syntheticSegment(1280, 720, 30, "0.8");
        VideoDecoderBackend backend = VideoDecoderBackend.parseInternal(System.getenv("CINEMARR_HARDWARE_VIDEO_BACKEND"));
        FfmpegVideoDecoder software = new FfmpegVideoDecoder(VideoDecoderBackend.SOFTWARE, "");
        FfmpegVideoDecoder hardware = new FfmpegVideoDecoder(backend, System.getenv("CINEMARR_HARDWARE_VIDEO_DEVICE"));
        FfmpegVideoDecoder.VideoDecodeResult expected = software.decodeVideoBounded(segment);
        FfmpegVideoDecoder.VideoDecodeResult actual = hardware.decodeVideoBounded(segment);
        assertEquals(expected.video().size(), actual.video().size());
        assertFalse(actual.video().isEmpty());
        assertEquals(expected.video().getFirst().width(), actual.video().getFirst().width());
        assertEquals(expected.video().getFirst().height(), actual.video().getFirst().height());
        assertEquals(backend, hardware.diagnostics().effectiveBackend(), hardware.diagnostics().fallbackReason());
        assertTrue(hardware.diagnostics().wallNanos() > 0);
        assertTrue(hardware.diagnostics().decodedFrames() > 0);
    }

    private static byte[] syntheticSegment() throws IOException, InterruptedException {
        return syntheticSegment(16, 16, 5, "0.6");
    }

    private static byte[] syntheticSegment(int width, int height, int rate, String duration)
            throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i", "color=c=red:s=" + width + "x" + height + ":r=" + rate,
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
                    "-t", duration, "-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast",
                    "-g", Integer.toString(rate), "-c:a", "aac", "-f", "mpegts", "pipe:1")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException missingFfmpeg) {
            throw new org.opentest4j.TestAbortedException("Host ffmpeg is unavailable", missingFfmpeg);
        }
        byte[] output = process.getInputStream().readAllBytes();
        int status = process.waitFor();
        if (status != 0) throw new org.opentest4j.TestAbortedException("Host ffmpeg cannot generate the H.264/AAC fixture");
        assertTrue(output.length > 188, "MPEG-TS fixture");
        return output;
    }

    private static String frameHash(DecodedVideoFrame frame) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(frame.rgba()));
    }
}
