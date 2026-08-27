package stonytark.cinemarr.client;

/** Signed little-endian 16-bit PCM emitted by FFmpeg. */
public final class LegacyDecodedAudioFrame {
    private final long presentationTimeUs;
    private final int sampleRate;
    private final int channels;
    private final byte[] pcm;

    public LegacyDecodedAudioFrame(long presentationTimeUs, int sampleRate, int channels, byte[] pcm) {
        if (presentationTimeUs < 0 || sampleRate < 1 || (channels != 1 && channels != 2)
                || pcm == null || (pcm.length & 1) != 0) {
            throw new IllegalArgumentException("Invalid decoded audio frame");
        }
        this.presentationTimeUs = presentationTimeUs;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.pcm = pcm.clone();
    }

    public long presentationTimeUs() { return presentationTimeUs; }
    public int sampleRate() { return sampleRate; }
    public int channels() { return channels; }
    public byte[] pcm() { return pcm.clone(); }
}
