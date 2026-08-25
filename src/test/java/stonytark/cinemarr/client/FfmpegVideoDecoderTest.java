package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;

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

    private static byte[] syntheticSegment() throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i", "color=c=red:s=16x16:r=5",
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
                    "-t", "0.6", "-pix_fmt", "yuv420p", "-c:v", "libx264", "-preset", "ultrafast",
                    "-g", "5", "-c:a", "aac", "-f", "mpegts", "pipe:1")
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
