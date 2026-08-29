package stonytark.cinemarr.client;

import org.bytedeco.javacv.FrameGrabber;

/** Minecraft-independent contract for decoding one bounded H.264/AAC MPEG-TS segment. */
public interface MediaSegmentDecoder {
    DecodedMediaSegment decode(byte[] mpegTs) throws FrameGrabber.Exception;
}
