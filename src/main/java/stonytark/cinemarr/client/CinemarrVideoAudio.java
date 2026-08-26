package stonytark.cinemarr.client;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.screen.ScreenFacing;
import stonytark.cinemarr.mixin.client.SoundEngineAccessor;
import stonytark.cinemarr.mixin.client.SoundManagerAccessor;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

/** Timeline-gated positional TV audio. Audio starts only after a future buffer exists. */
public final class CinemarrVideoAudio {
    private static final long START_BUFFER_US = 700_000;
    private final Queue<DecodedAudioFrame> pending = new ArrayDeque<>();
    private UUID sessionId;
    private long generation = -1;
    private VideoPcmAudioStream stream;
    private ChannelAccess.ChannelHandle channel;
    private boolean channelPending;
    private long channelAttempt;
    private int underruns;

    public void tick(CinemarrVideoPlayback playback, VideoPackets.SessionState session) {
        if (session == null || session.item() == null || session.status() == VideoPackets.SessionStatus.IDLE) { reset(); return; }
        if (!session.sessionId().equals(sessionId) || session.generation() != generation) {
            reset(); sessionId = session.sessionId(); generation = session.generation();
        }
        long targetUs = CinemarrVideoPlayback.authoritativePositionMs(session, System.currentTimeMillis()) * 1_000L;
        for (DecodedAudioFrame frame; (frame = playback.pollAudio()) != null;) {
            if (endUs(frame) < targetUs - 100_000L) continue;
            if (stream == null) pending.add(frame); else if (!stream.offer(frame)) underruns++;
        }
        if (stream == null && !channelPending) prepareAndStart(session, targetUs);
        if (channel != null) {
            Vec3 origin = nearestScreenPoint(session, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            float volume = CinemarrSettings.enabled() ? (float) (CinemarrSettings.volume()
                    * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS)) : 0;
            channel.execute(value -> {
                value.setSelfPosition(origin); value.setVolume(volume); value.setRelative(false); value.linearAttenuation(64);
                if (session.paused()) value.pause(); else value.unpause();
            });
            if (channel.isStopped() && !session.paused()) { underruns++; resetChannel(); }
        }
    }

    private void prepareAndStart(VideoPackets.SessionState session, long targetUs) {
        while (!pending.isEmpty() && endUs(pending.peek()) < targetUs - 50_000L) pending.poll();
        if (pending.isEmpty()) return;
        DecodedAudioFrame first = pending.peek(), last = first;
        for (DecodedAudioFrame value : pending) last = value;
        if (endUs(last) - Math.max(targetUs, first.presentationTimeUs()) < START_BUFFER_US || first.presentationTimeUs() > targetUs + 100_000L) return;
        VideoPcmAudioStream startingStream = new VideoPcmAudioStream(first.sampleRate(), first.channels());
        while (!pending.isEmpty()) startingStream.offer(pending.poll());
        UUID expectedSession = sessionId; long expectedGeneration = generation; long expectedAttempt = ++channelAttempt; channelPending = true;
        ChannelAccess access = ((SoundEngineAccessor) ((SoundManagerAccessor) (Object) Minecraft.getInstance().getSoundManager()).cinemarr$soundEngine()).cinemarr$channelAccess();
        access.createHandle(Library.Pool.STREAMING).whenComplete((handle, error) -> {
            if (expectedAttempt != channelAttempt) { startingStream.close(); if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::stop); return; }
            channelPending = false;
            if (error != null || handle == null || !expectedSession.equals(sessionId) || expectedGeneration != generation) {
                startingStream.close(); if (handle != null) handle.execute(com.mojang.blaze3d.audio.Channel::stop); return;
            }
            stream = startingStream; channel = handle;
            Vec3 origin = nearestScreenPoint(session, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            handle.execute(value -> { value.setRelative(false); value.setSelfPosition(origin); value.linearAttenuation(64); value.setVolume(0); value.attachBufferStream(startingStream); value.play(); });
        });
    }

    private static long endUs(DecodedAudioFrame frame) {
        long samples = frame.byteLength() / (2L * frame.channels());
        return frame.presentationTimeUs() + samples * 1_000_000L / frame.sampleRate();
    }

    static Vec3 nearestScreenPoint(VideoPackets.SessionState state, Vec3 listener) {
        double u = state.screenFacing() == ScreenFacing.EAST || state.screenFacing() == ScreenFacing.WEST ? listener.z : listener.x;
        double v = state.screenFacing() == ScreenFacing.UP || state.screenFacing() == ScreenFacing.DOWN ? listener.z : listener.y;
        u = Math.max(state.minimumU(), Math.min(state.minimumU() + state.screenWidth(), u));
        v = Math.max(state.minimumV(), Math.min(state.minimumV() + state.screenHeight(), v));
        return switch (state.screenFacing()) {
            case NORTH -> new Vec3(u,v,state.screenPlane()); case SOUTH -> new Vec3(u,v,state.screenPlane()+1);
            case WEST -> new Vec3(state.screenPlane(),v,u); case EAST -> new Vec3(state.screenPlane()+1,v,u);
            case DOWN -> new Vec3(u,state.screenPlane(),v); case UP -> new Vec3(u,state.screenPlane()+1,v);
        };
    }

    public int underruns() { return underruns; }
    public void audioEngineReloaded() { resetChannel(); }
    public void reset() { resetChannel(); pending.clear(); sessionId=null; generation=-1; underruns=0; }
    private void resetChannel() {
        channelAttempt++;
        if (channel != null) { channel.execute(com.mojang.blaze3d.audio.Channel::stop); channel=null; }
        if (stream != null) { stream.close(); stream=null; }
        channelPending=false;
    }
}
