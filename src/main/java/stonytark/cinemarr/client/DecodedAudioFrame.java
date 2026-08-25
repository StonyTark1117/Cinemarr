package stonytark.cinemarr.client;

/** Signed little-endian 16-bit PCM emitted by FFmpeg. */
public record DecodedAudioFrame(long presentationTimeUs, int sampleRate, int channels, byte[] pcm) {
    public DecodedAudioFrame {
        if (presentationTimeUs < 0 || sampleRate < 1 || (channels != 1 && channels != 2) || pcm == null || (pcm.length & 1) != 0) {
            throw new IllegalArgumentException("Invalid decoded audio frame");
        }
        pcm = pcm.clone();
    }

    @Override public byte[] pcm() { return pcm.clone(); }
    byte[] pcmView() { return pcm; }
    int byteLength() { return pcm.length; }
}
