package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opentest4j.TestAbortedException;
import stonytark.cinemarr.core.platform.DecoderProbeFixture;
import stonytark.cinemarr.core.platform.VideoDecoderBackend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import static org.bytedeco.ffmpeg.global.avcodec.avcodec_license;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LegacyFfmpegVideoDecoderTest {
    @Test
    void javaEightDecoderReadsSyntheticH264AacTransportStream() throws Exception {
        String nativeLicense = avcodec_license().getString().toLowerCase(java.util.Locale.ROOT);
        assertTrue(nativeLicense.startsWith("lgpl"), nativeLicense);
        assertFalse(nativeLicense.startsWith("gpl"), nativeLicense);
        byte[] segment = syntheticSegment();
        LegacyFfmpegVideoDecoder decoder = new LegacyFfmpegVideoDecoder();
        LegacyDecodedMediaSegment decoded = decoder.decode(segment);
        assertTrue(decoded.video().size() >= 2, "video frames");
        assertFalse(decoded.audio().isEmpty(), "audio frames");
        LegacyDecodedVideoFrame first = decoded.video().get(0);
        assertEquals(16, first.width());
        assertEquals(16, first.height());
        assertEquals(16 * 16 * 4, first.rgba().length);
        assertEquals(48_000, decoded.audio().get(0).sampleRate());
        assertEquals(1, decoded.audio().get(0).channels());
        assertEquals(hash(first.rgba()), hash(decoder.decode(segment).video().get(0).rgba()));
        assertTrue((first.rgba()[0] & 0xff) > (first.rgba()[2] & 0xff), "decoded frame remains red");
    }

    @Test
    void javaEightContractDecodesBundledHardwareProbeFixture() throws Exception {
        LegacyMediaSegmentDecoder decoder = new LegacyFfmpegVideoDecoder(VideoDecoderBackend.SOFTWARE, "");
        LegacyDecodedMediaSegment decoded = decoder.decode(DecoderProbeFixture.bytes());
        assertFalse(decoded.video().isEmpty());
        assertFalse(decoded.audio().isEmpty());
        assertEquals(64, decoded.video().get(0).width());
        assertEquals(48_000, decoded.audio().get(0).sampleRate());
    }

    @Test
    void javaEightUnknownAutoSelectionUsesSoftwareWithoutFallback() throws Exception {
        String previous = System.getProperty("cinemarr.video.gpuVendor");
        try {
            System.setProperty("cinemarr.video.gpuVendor", "unknown");
            LegacyFfmpegVideoDecoder decoder = new LegacyFfmpegVideoDecoder(VideoDecoderBackend.AUTO, "secret/device/path");
            assertFalse(decoder.decode(syntheticSegment()).video().isEmpty());
            assertEquals(VideoDecoderBackend.SOFTWARE, decoder.effectiveBackend());
            assertEquals(0L, decoder.fallbackCount());
            assertEquals("explicit-selector", decoder.deviceType());
            assertFalse(decoder.fallbackReason().contains("secret/device/path"));
        } finally {
            if (previous == null) System.clearProperty("cinemarr.video.gpuVendor");
            else System.setProperty("cinemarr.video.gpuVendor", previous);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CINEMARR_HARDWARE_VIDEO_TEST", matches = "true")
    void javaEightHardwareDecoderReadsSyntheticProgram() throws Exception {
        VideoDecoderBackend backend = VideoDecoderBackend.parseInternal(System.getenv("CINEMARR_HARDWARE_VIDEO_BACKEND"));
        LegacyFfmpegVideoDecoder decoder = new LegacyFfmpegVideoDecoder(
                backend, System.getenv("CINEMARR_HARDWARE_VIDEO_DEVICE"));
        LegacyDecodedMediaSegment decoded = decoder.decode(syntheticSegment(1280, 720, 30, "0.6"));
        assertFalse(decoded.video().isEmpty());
        assertFalse(decoded.audio().isEmpty());
        assertEquals(backend, decoder.effectiveBackend(), decoder.fallbackReason());
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
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        } catch (IOException missing) {
            throw new TestAbortedException("Host ffmpeg is unavailable", missing);
        }
        byte[] output = readAll(process.getInputStream());
        int status = process.waitFor();
        if (status != 0) throw new TestAbortedException("Host ffmpeg cannot generate the H.264/AAC fixture");
        assertTrue(output.length > 188, "MPEG-TS fixture");
        return output;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read; (read = input.read(buffer)) >= 0;) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte next : digest) value.append(String.format("%02x", next & 0xff));
        return value.toString();
    }
}
