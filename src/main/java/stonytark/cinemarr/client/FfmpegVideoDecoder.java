package stonytark.cinemarr.client;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;

import java.io.ByteArrayInputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_RGBA;

/**
 * Client-only FFmpeg decoder. Plex is instructed to keyframe each HLS segment,
 * so decoding a bounded segment independently also gives seek/recovery a clean
 * generation boundary and avoids ever exposing a Plex URL to native code.
 */
public final class FfmpegVideoDecoder {
    private static final int MAX_SEGMENT_BYTES = 32 * 1024 * 1024;

    public DecodedMediaSegment decode(byte[] mpegTs) throws FrameGrabber.Exception {
        if (mpegTs == null || mpegTs.length == 0 || mpegTs.length > MAX_SEGMENT_BYTES) {
            throw new IllegalArgumentException("Invalid MPEG-TS segment size");
        }
        List<DecodedVideoFrame> video = new ArrayList<>();
        List<DecodedAudioFrame> audio = new ArrayList<>();
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(mpegTs), 0)) {
            grabber.setFormat("mpegts");
            grabber.setPixelFormat(AV_PIX_FMT_RGBA);
            grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
            grabber.start();
            Frame frame;
            while ((frame = grabber.grab()) != null) {
                long timestamp = Math.max(0, frame.timestamp);
                if (frame.image != null && frame.image.length != 0) video.add(videoFrame(frame, timestamp));
                if (frame.samples != null && frame.samples.length != 0) audio.add(audioFrame(frame, timestamp, grabber.getSampleRate(), grabber.getAudioChannels()));
            }
        }
        return new DecodedMediaSegment(video, audio);
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
}
