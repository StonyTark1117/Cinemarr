package stonytark.cinemarr.client;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.client.sounds.ChannelAccess;
import org.junit.jupiter.api.Test;
import stonytark.cinemarr.core.protocol.VideoPackets;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

import static org.junit.jupiter.api.Assertions.*;

class CinemarrVideoAudioIdentityTest {
    private final VideoStreamIdentity identity = new VideoStreamIdentity(UUID.randomUUID(), 4, UUID.randomUUID(), 9);

    @Test void anyIdentityChangeDiscardsPendingAudioAndInvalidatesDelayedChannelCreation() throws Exception {
        VideoStreamIdentity[] replacements = {
                new VideoStreamIdentity(UUID.randomUUID(), 4, identity.streamId(), 9),
                new VideoStreamIdentity(identity.timelineId(), 5, identity.streamId(), 9),
                new VideoStreamIdentity(identity.timelineId(), 4, UUID.randomUUID(), 9),
                new VideoStreamIdentity(identity.timelineId(), 4, identity.streamId(), 10)
        };
        for (VideoStreamIdentity replacement : replacements) {
            CinemarrVideoAudio audio = new CinemarrVideoAudio();
            bind(audio, identity);
            pending(audio).add(new DecodedAudioFrame(100_000, 48_000, 2, new byte[8]));
            set(audio, "underruns", 3); set(audio, "channelPending", true);
            long oldAttempt = (Long) get(audio, "channelAttempt");
            bind(audio, replacement);
            assertEquals(replacement, get(audio, "identity"));
            assertTrue(pending(audio).isEmpty()); assertEquals(0, audio.underruns());
            assertEquals(false, get(audio, "channelPending"));
            assertTrue((Long) get(audio, "channelAttempt") > oldAttempt);
            // A queued old completion must not clear a newer attempt's pending flag.
            set(audio, "channelPending", true);
            var finish = CinemarrVideoAudio.class.getDeclaredMethod("finishStart", VideoPackets.SessionState.class,
                    VideoStreamIdentity.class, long.class, long.class, ChannelAccess.ChannelHandle.class, Throwable.class);
            finish.setAccessible(true);
            finish.invoke(audio, null, identity, oldAttempt, 0L, null, null);
            assertEquals(true, get(audio, "channelPending"));
            audio.reset(); assertNull(get(audio, "identity"));
        }
    }

    @Test void unchangedIdentityAndSoundReloadPreservePendingProgramAudioAndFailureEvidence() throws Exception {
        CinemarrVideoAudio audio = new CinemarrVideoAudio();
        bind(audio, identity);
        DecodedAudioFrame frame = new DecodedAudioFrame(100_000, 48_000, 2, new byte[8]);
        pending(audio).add(frame); set(audio, "underruns", 3);
        long attempt = (Long) get(audio, "channelAttempt");
        bind(audio, new VideoStreamIdentity(identity.timelineId(), 4, identity.streamId(), 9));
        assertEquals(attempt, get(audio, "channelAttempt")); assertSame(frame, pending(audio).peek());
        audio.audioEngineReloaded();
        assertEquals(identity, get(audio, "identity")); assertSame(frame, pending(audio).peek());
        assertEquals(3, audio.underruns()); assertTrue((Long) get(audio, "channelAttempt") > attempt);
        audio.reset(); assertTrue(pending(audio).isEmpty()); assertNull(get(audio, "identity"));
    }

    private static void bind(CinemarrVideoAudio audio, VideoStreamIdentity value) throws Exception {
        var method = CinemarrVideoAudio.class.getDeclaredMethod("bindIdentity", VideoStreamIdentity.class);
        method.setAccessible(true); method.invoke(audio, value);
    }
    @SuppressWarnings("unchecked")
    private static Queue<DecodedAudioFrame> pending(CinemarrVideoAudio audio) throws Exception {
        return (Queue<DecodedAudioFrame>) get(audio, "pending");
    }
    private static Object get(CinemarrVideoAudio audio, String name) throws Exception {
        Field field = CinemarrVideoAudio.class.getDeclaredField(name); field.setAccessible(true); return field.get(audio);
    }
    private static void set(CinemarrVideoAudio audio, String name, Object value) throws Exception {
        Field field = CinemarrVideoAudio.class.getDeclaredField(name); field.setAccessible(true); field.set(audio, value);
    }
}
