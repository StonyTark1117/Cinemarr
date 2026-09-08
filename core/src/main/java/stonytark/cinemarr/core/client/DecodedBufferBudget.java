package stonytark.cinemarr.core.client;

import java.util.concurrent.CancellationException;

/** Per-segment Java buffer budget, independent of the compressed input size. */
public final class DecodedBufferBudget {
    public static final long MAX_AUDIO_BYTES = 64L * 1024 * 1024;
    public static final int MAX_AUDIO_FRAMES = 16_384;
    public static final int MAX_AUDIO_FRAME_BYTES = 1024 * 1024;
    public static final long MAX_VIDEO_FRAME_BYTES = 128L * 1024 * 1024;
    private long audioBytes;
    private int audioFrames;

    /** Reserve before allocation; rejection leaves the accounting unchanged. */
    public int reservePcm16(long samples) {
        checkCancelled();
        if (samples < 0 || samples > MAX_AUDIO_FRAME_BYTES / 2)
            throw new IllegalArgumentException("Decoded audio frame exceeds buffer limit");
        int bytes = (int) samples * 2;
        if (audioFrames >= MAX_AUDIO_FRAMES || bytes > MAX_AUDIO_BYTES - audioBytes)
            throw new IllegalArgumentException("Decoded segment exceeds audio buffer limit");
        audioBytes += bytes;
        audioFrames++;
        return bytes;
    }

    public static int rgbaBytes(int width, int height) {
        checkCancelled();
        // Divide first so even Integer.MAX_VALUE dimensions cannot overflow.
        if (width <= 0 || height <= 0 || (long) width * height > MAX_VIDEO_FRAME_BYTES / 4)
            throw new IllegalArgumentException("Decoded video frame exceeds buffer limit");
        return width * height * 4;
    }

    /** Does not clear the interrupt; callers must still close their native resources. */
    public static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Video decode cancelled");
    }

    public long audioBytes() { return audioBytes; }
    public int audioFrames() { return audioFrames; }
}
