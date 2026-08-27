package stonytark.cinemarr.client;

/** Client-thread owner for legacy video decode, texture, rendering, and audio pipelines. */
final class LegacyVideoRuntime {
    static final LegacyVideoRuntime INSTANCE = new LegacyVideoRuntime();
    private final LegacyVideoPlaybackManager playback = new LegacyVideoPlaybackManager();
    private final LegacyVideoRenderer renderer = new LegacyVideoRenderer();
    private final LegacyVideoAudioManager audio = new LegacyVideoAudioManager();
    void tick() {
        playback.tick(LegacyVideoClientState.INSTANCE);
        audio.tick(playback, LegacyVideoClientState.INSTANCE);
    }
    void render() { renderer.render(playback, LegacyVideoClientState.INSTANCE); }
    void audioEngineReloaded() { audio.audioEngineReloaded(); }
    void reset() { audio.reset(); playback.reset(); }
    private LegacyVideoRuntime() {}
}
