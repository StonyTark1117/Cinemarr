package stonytark.cinemarr.client;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

/** Blocking, bounded PCM bridge from the decoder thread to Minecraft's streaming OpenAL worker. */
final class VideoPcmAudioStream implements AudioStream {
    private static final long MAX_BUFFERED_MS = 8_000;
    private final AudioFormat format;
    private final Queue<byte[]> queue = new ArrayDeque<>();
    private byte[] current;
    private int offset;
    private long bufferedBytes;
    private boolean closed;

    VideoPcmAudioStream(int sampleRate, int channels) {
        format = new AudioFormat(sampleRate, 16, channels, true, false);
    }

    synchronized boolean offer(DecodedAudioFrame frame) {
        if (closed || frame.sampleRate() != (int) format.getSampleRate() || frame.channels() != format.getChannels()
                || bufferedMs() >= MAX_BUFFERED_MS) return false;
        byte[] pcm = frame.pcmView(); queue.add(pcm); bufferedBytes += pcm.length; notifyAll(); return true;
    }

    synchronized long bufferedMs() {
        return bufferedBytes * 1_000L / Math.max(1, (long) format.getSampleRate() * format.getChannels() * 2L);
    }

    @Override public AudioFormat getFormat() { return format; }

    @Override public synchronized ByteBuffer read(int requested) {
        int aligned = Math.max(format.getFrameSize(), requested - requested % format.getFrameSize());
        while (!closed && current == null && queue.isEmpty()) {
            try { wait(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
        }
        if (closed && current == null && queue.isEmpty()) return null;
        ByteBuffer output = ByteBuffer.allocateDirect(aligned);
        while (output.hasRemaining()) {
            if (current == null) { current = queue.poll(); offset = 0; if (current == null) break; }
            int count = Math.min(output.remaining(), current.length - offset);
            output.put(current, offset, count); offset += count; bufferedBytes -= count;
            if (offset == current.length) { current = null; offset = 0; }
        }
        output.flip(); return output.hasRemaining() ? output : null;
    }

    @Override public synchronized void close() { closed = true; queue.clear(); current = null; bufferedBytes = 0; notifyAll(); }
}
