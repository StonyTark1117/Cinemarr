package stonytark.cinemarr.client;

import java.util.List;

/** Immutable result of decoding one independently-keyframed HLS MPEG-TS segment. */
public record DecodedMediaSegment(List<DecodedVideoFrame> video, List<DecodedAudioFrame> audio) {
    public DecodedMediaSegment {
        video = List.copyOf(video);
        audio = List.copyOf(audio);
    }
}
