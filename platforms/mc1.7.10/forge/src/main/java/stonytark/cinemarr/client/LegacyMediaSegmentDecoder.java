package stonytark.cinemarr.client;

import org.bytedeco.javacv.FrameGrabber;

/** Java 8, Minecraft-independent segment-decoder contract for the isolated legacy adapter. */
public interface LegacyMediaSegmentDecoder {
    LegacyDecodedMediaSegment decode(byte[] mpegTs) throws FrameGrabber.Exception;
}
