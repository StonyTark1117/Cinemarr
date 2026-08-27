package stonytark.cinemarr.client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.io.ByteArrayInputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;

/** Java-8-compatible FFmpeg H.264/AAC decoder for independently-keyframed HLS segments. */
public final class LegacyFfmpegVideoDecoder {
    private static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;

    public LegacyDecodedMediaSegment decode(byte[] mpegTs) throws FrameGrabber.Exception {
        if (mpegTs == null || mpegTs.length == 0 || mpegTs.length > MAX_SEGMENT_BYTES) {
            throw new IllegalArgumentException("Invalid MPEG-TS segment size");
        }
        List<LegacyDecodedVideoFrame> video = new ArrayList<LegacyDecodedVideoFrame>();
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
        return new LegacyDecodedMediaSegment(video, audio);
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
}
