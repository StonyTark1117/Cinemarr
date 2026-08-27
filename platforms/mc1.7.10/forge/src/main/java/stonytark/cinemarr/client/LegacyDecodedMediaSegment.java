package stonytark.cinemarr.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable Java-8 representation of one decoded HLS MPEG-TS segment. */
public final class LegacyDecodedMediaSegment {
    private final List<LegacyDecodedVideoFrame> video;
    private final List<LegacyDecodedAudioFrame> audio;

    public LegacyDecodedMediaSegment(List<LegacyDecodedVideoFrame> video, List<LegacyDecodedAudioFrame> audio) {
        this.video = Collections.unmodifiableList(new ArrayList<LegacyDecodedVideoFrame>(video));
        this.audio = Collections.unmodifiableList(new ArrayList<LegacyDecodedAudioFrame>(audio));
    }

    public List<LegacyDecodedVideoFrame> video() { return video; }
    public List<LegacyDecodedAudioFrame> audio() { return audio; }
}
