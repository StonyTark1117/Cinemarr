package stonytark.cinemarr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;

import javax.sound.sampled.AudioFormat;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/** Timeline-gated positional PCM fed through Minecraft's existing OpenAL context. */
final class LegacyVideoAudio {
    private static final long START_BUFFER_US = 700_000L;
    private static final long TARGET_QUEUE_MS = 1_000L;
    private static long ids;
    private final String source = "cinemarr:video:" + (++ids);
    private final Queue<LegacyDecodedAudioFrame> pending = new ArrayDeque<LegacyDecodedAudioFrame>();
    private UUID sessionId;
    private long generation = -1;
    private SoundSystem soundSystem;
    private AudioFormat format;
    private long queuedUntilMs;
    private int underruns;
    private boolean started;

    void tick(LegacyVideoPlayback playback, VideoPackets.SessionState session, List<VideoPackets.SessionState> televisions) {
        if (session == null || session.item() == null || session.status() == VideoPackets.SessionStatus.IDLE || televisions.isEmpty()) { reset(); return; }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            reset(); sessionId = session.sessionId(); generation = session.generation();
        }
        long targetUs = LegacyVideoPlayback.authoritativePositionMs(session,
                LegacyClientState.INSTANCE.serverEpoch(System.currentTimeMillis())) * 1_000L;
        LegacyDecodedAudioFrame frame;
        while ((frame = playback.pollAudio()) != null) {
            if (endUs(frame) >= targetUs - 100_000L) pending.add(frame);
            while (pending.size() > 512) { pending.poll(); underruns++; }
        }
        SoundSystem current = LegacySoundAccess.soundSystem(Minecraft.getMinecraft());
        if (started && current != soundSystem) stopSource();
        if (!started) prepareAndStart(current, targetUs);
        if (!started || soundSystem == null) return;
        float[] origin = nearest(televisions);
        soundSystem.setPosition(source, origin[0], origin[1], origin[2]);
        soundSystem.setVolume(source, CinemarrSettings.enabled() ? (float) (CinemarrSettings.volume()
                * Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.RECORDS)) : 0.0F);
        if (session.paused()) soundSystem.pause(source);
        else {
            feed(System.currentTimeMillis()); soundSystem.play(source);
            if (pending.isEmpty() && System.currentTimeMillis() > queuedUntilMs + 250L && !soundSystem.playing(source)) { underruns++; stopSource(); }
        }
    }

    private void prepareAndStart(SoundSystem system, long targetUs) {
        while (!pending.isEmpty() && endUs(pending.peek()) < targetUs - 50_000L) pending.poll();
        if (system == null || pending.isEmpty()) return;
        LegacyDecodedAudioFrame first = pending.peek(), last = first; for (LegacyDecodedAudioFrame value : pending) last = value;
        if (endUs(last) - Math.max(targetUs, first.presentationTimeUs()) < START_BUFFER_US || first.presentationTimeUs() > targetUs + 100_000L) return;
        format = new AudioFormat(first.sampleRate(), 16, first.channels(), true, false); soundSystem = system;
        float[] origin = new float[] { 0, 0, 0 };
        soundSystem.rawDataStream(format, true, source, origin[0], origin[1], origin[2], SoundSystemConfig.ATTENUATION_LINEAR, 64.0F);
        soundSystem.setLooping(source, false); queuedUntilMs = System.currentTimeMillis(); started = true; feed(queuedUntilMs); soundSystem.play(source);
    }

    private void feed(long now) {
        if (soundSystem == null || format == null) return; queuedUntilMs = Math.max(now, queuedUntilMs);
        while (queuedUntilMs - now < TARGET_QUEUE_MS && !pending.isEmpty()) {
            LegacyDecodedAudioFrame frame = pending.poll();
            if (frame.sampleRate() != (int) format.getSampleRate() || frame.channels() != format.getChannels()) { underruns++; stopSource(); return; }
            byte[] pcm = frame.pcm();
            soundSystem.feedRawAudioData(source, pcm); queuedUntilMs += durationMs(frame);
        }
    }

    private static long durationMs(LegacyDecodedAudioFrame frame) {
        return frame.pcmView().length * 1_000L / (2L * frame.channels() * frame.sampleRate());
    }
    private static long endUs(LegacyDecodedAudioFrame frame) { return frame.presentationTimeUs() + durationMs(frame) * 1_000L; }
    private static float[] nearest(List<VideoPackets.SessionState> televisions) {
        net.minecraft.entity.Entity listener = Minecraft.getMinecraft().renderViewEntity;
        double x = listener == null ? 0 : listener.posX, y = listener == null ? 0 : listener.posY, z = listener == null ? 0 : listener.posZ;
        float[] best = null; double bestDistance = Double.MAX_VALUE;
        for (VideoPackets.SessionState television : televisions) {
            float[] point = nearestScreenPoint(television, x, y, z); double dx = point[0] - x, dy = point[1] - y, dz = point[2] - z;
            double distance = dx * dx + dy * dy + dz * dz; if (distance < bestDistance) { bestDistance = distance; best = point; }
        }
        return best == null ? new float[] { 0, 0, 0 } : best;
    }
    static float[] nearestScreenPoint(VideoPackets.SessionState state, double listenerX, double listenerY, double listenerZ) {
        double u = state.screenFacing() == ScreenFacing.EAST || state.screenFacing() == ScreenFacing.WEST ? listenerZ : listenerX;
        double v = state.screenFacing() == ScreenFacing.UP || state.screenFacing() == ScreenFacing.DOWN ? listenerZ : listenerY;
        u = Math.max(state.minimumU(), Math.min(state.minimumU() + state.screenWidth(), u));
        v = Math.max(state.minimumV(), Math.min(state.minimumV() + state.screenHeight(), v));
        switch (state.screenFacing()) {
            case NORTH: return new float[] { (float) u, (float) v, state.screenPlane() };
            case SOUTH: return new float[] { (float) u, (float) v, state.screenPlane() + 1 };
            case WEST: return new float[] { state.screenPlane(), (float) v, (float) u };
            case EAST: return new float[] { state.screenPlane() + 1, (float) v, (float) u };
            case DOWN: return new float[] { (float) u, state.screenPlane(), (float) v };
            default: return new float[] { (float) u, state.screenPlane() + 1, (float) v };
        }
    }
    int underruns() { return underruns; }
    void audioEngineReloaded() { stopSource(); }
    void reset() { stopSource(); pending.clear(); sessionId = null; generation = -1; underruns = 0; }
    private void stopSource() {
        SoundSystem previous = soundSystem; soundSystem = null; format = null; started = false; queuedUntilMs = 0;
        if (previous != null) {
            try { previous.stop(source); previous.flush(source); previous.removeSource(source); }
            catch (RuntimeException ignored) { }
        }
    }
}
