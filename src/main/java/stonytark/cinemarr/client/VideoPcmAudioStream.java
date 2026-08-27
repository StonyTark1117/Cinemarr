package stonytark.cinemarr.client;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongSupplier;

/** Non-blocking, bounded PCM bridge from the decoder thread to Minecraft's streaming OpenAL worker. */
final class VideoPcmAudioStream implements AudioStream {
    private static final long MAX_BUFFERED_MS = 4_000;
    private final AudioFormat format;
    private final Deque<byte[]> queue = new ArrayDeque<>();
    private final LongSupplier nanoTime;
    private byte[] current;
    private int offset;
    private long bufferedBytes;
    private boolean closed;
    private boolean starving;
    private int starvations;
    private long silenceDeadlineNanos = Long.MIN_VALUE;
    private long scheduledSkipBytes;

    VideoPcmAudioStream(int sampleRate, int channels) {
        this(sampleRate, channels, System::nanoTime);
    }

    VideoPcmAudioStream(int sampleRate, int channels, LongSupplier nanoTime) {
        format = new AudioFormat(sampleRate, 16, channels, true, false);
        this.nanoTime = nanoTime;
    }

    synchronized boolean offer(DecodedAudioFrame frame) {
        return offer(frame, frame.presentationTimeUs());
    }

    synchronized boolean offer(DecodedAudioFrame frame, long minimumPresentationTimeUs) {
        if (closed || frame.sampleRate() != (int) format.getSampleRate() || frame.channels() != format.getChannels()
                || bufferedMs() >= MAX_BUFFERED_MS) return false;
        int frameSize = format.getFrameSize();
        long skippedSamples = Math.max(0L, minimumPresentationTimeUs - frame.presentationTimeUs())
                * frame.sampleRate() / 1_000_000L;
        int offset = (int) Math.min(frame.byteLength(), skippedSamples * frameSize);
        offset -= offset % frameSize;
        if (scheduledSkipBytes > 0) {
            long catchUp = Math.min(frame.byteLength() - offset, scheduledSkipBytes);
            catchUp -= catchUp % frameSize;
            offset += (int) catchUp;
            scheduledSkipBytes -= catchUp;
        }
        if (offset >= frame.byteLength()) return true;
        byte[] source = frame.pcmView();
        byte[] pcm = new byte[source.length - offset];
        System.arraycopy(source, offset, pcm, 0, pcm.length);
        queue.add(pcm); bufferedBytes += pcm.length; starving = false; return true;
    }

    synchronized boolean offerSilence(long durationUs) {
        if (closed || durationUs < 0 || bufferedMs() >= MAX_BUFFERED_MS) return false;
        long samples = durationUs * (long) format.getSampleRate() / 1_000_000L;
        long bytes = samples * format.getFrameSize();
        if (bytes > Integer.MAX_VALUE || bufferedBytes + bytes > MAX_BUFFERED_MS
                * (long) format.getSampleRate() * format.getChannels() * 2L / 1_000L) return false;
        if (bytes > 0) { queue.add(new byte[(int) bytes]); bufferedBytes += bytes; }
        starving = false;
        return true;
    }

    synchronized void scheduleSilenceFor(long durationUs) {
        silenceDeadlineNanos = nanoTime.getAsLong() + Math.max(0L, durationUs) * 1_000L;
    }

    synchronized long bufferedMs() {
        return bufferedBytes * 1_000L / Math.max(1, (long) format.getSampleRate() * format.getChannels() * 2L);
    }

    @Override public AudioFormat getFormat() { return format; }

    @Override public synchronized ByteBuffer read(int requested) {
        int aligned = Math.max(format.getFrameSize(), requested - requested % format.getFrameSize());
        if (closed && current == null && queue.isEmpty()) return null;
        if (silenceDeadlineNanos != Long.MIN_VALUE) {
            long remainingNanos = silenceDeadlineNanos - nanoTime.getAsLong();
            silenceDeadlineNanos = Long.MIN_VALUE;
            long bytes = bytesForDurationUs(Math.abs(remainingNanos) / 1_000L);
            if (remainingNanos >= 0) {
                bytes = Math.min(Integer.MAX_VALUE, bytes);
                if (bytes > 0) { queue.addFirst(new byte[(int) bytes]); bufferedBytes += bytes; }
            } else {
                skipQueuedBytes(bytes);
            }
        }
        ByteBuffer output = ByteBuffer.allocateDirect(aligned);
        while (output.hasRemaining()) {
            if (current == null) {
                current = queue.poll(); offset = 0;
                if (current == null) {
                    if (!starving) { starving = true; starvations++; }
                    while (output.hasRemaining()) output.put((byte) 0);
                    break;
                }
            }
            int count = Math.min(output.remaining(), current.length - offset);
            output.put(current, offset, count); offset += count; bufferedBytes -= count;
            if (offset == current.length) { current = null; offset = 0; }
        }
        output.flip(); return output.hasRemaining() ? output : null;
    }

    synchronized int starvations() { return starvations; }

    private long bytesForDurationUs(long durationUs) {
        long samples = durationUs * (long) format.getSampleRate() / 1_000_000L;
        long bytes = samples * format.getFrameSize();
        return bytes - bytes % format.getFrameSize();
    }

    private void skipQueuedBytes(long requested) {
        long remaining = requested;
        while (remaining > 0) {
            if (current == null) {
                current = queue.poll();
                offset = 0;
                if (current == null) break;
            }
            int count = (int) Math.min(remaining, current.length - offset);
            offset += count;
            bufferedBytes -= count;
            remaining -= count;
            if (offset == current.length) { current = null; offset = 0; }
        }
        scheduledSkipBytes += remaining;
    }

    @Override public synchronized void close() {
        closed = true; queue.clear(); current = null; bufferedBytes = 0; scheduledSkipBytes = 0;
    }
}
