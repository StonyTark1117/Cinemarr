package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVideoAudioReloadTest {
    @Test void reloadAfterContextDestructionDiscardsNativeIdsWithoutCallingOpenAl() throws Exception {
        LegacyVideoAudio audio = new LegacyVideoAudio();
        VideoStreamIdentity identity = new VideoStreamIdentity(UUID.randomUUID(), 3, UUID.randomUUID(), 11);
        set(audio, "source", 42);
        set(audio, "prepared", true);
        set(audio, "started", true);
        set(audio, "sourcePaused", true);
        set(audio, "identity", identity);
        set(audio, "underruns", 3);
        set(audio, "stableTicks", 40);
        set(audio, "backendCompletedUs", 123_000L);
        set(audio, "scheduledStartUs", 100_000L);
        set(audio, "queuedUntilLocalUs", 456_000L);
        queue(audio, "backendBuffers").add(new Object());
        LegacyDecodedAudioFrame pending = new LegacyDecodedAudioFrame(100_000, 48_000, 2, new byte[8]);
        queue(audio, "pending").add(pending);

        // No OpenAL context or native library is created in this test. The old
        // stopSource path tried nalSourceStop(42) and crashed on this event.
        assertDoesNotThrow(audio::audioEngineReloaded);
        assertEquals(0, get(audio, "source"));
        assertNull(get(audio, "sourceContext"));
        assertNull(get(audio, "soundSystem"));
        assertNull(get(audio, "format"));
        assertEquals(false, get(audio, "prepared"));
        assertEquals(false, get(audio, "started"));
        assertEquals(false, get(audio, "sourcePaused"));
        assertEquals(0, get(audio, "stableTicks"));
        assertEquals(0L, get(audio, "backendCompletedUs"));
        assertEquals(0L, get(audio, "scheduledStartUs"));
        assertEquals(0L, get(audio, "queuedUntilLocalUs"));
        assertTrue(queue(audio, "backendBuffers").isEmpty());
        assertSame(pending, queue(audio, "pending").peek());
        assertEquals(identity, get(audio, "identity"));
        assertEquals(3, audio.underruns(), "Reload must not erase previously recorded failures");
        assertFalse(audio.ready());
        assertDoesNotThrow(audio::audioEngineReloaded, "Repeated reload must be idempotent");
        assertDoesNotThrow(audio::reset, "Disconnect after reload must not touch obsolete native IDs");
        assertTrue(queue(audio, "pending").isEmpty());
        assertNull(get(audio, "identity"));
    }

    @Test void nativeIdsBelongOnlyToTheSameLiveContextIdentity() {
        Object original = new String("context"), replacement = new String("context");
        assertTrue(LegacyVideoAudio.ownsContext(original, original, true));
        assertFalse(LegacyVideoAudio.ownsContext(original, original, false));
        assertFalse(LegacyVideoAudio.ownsContext(original, null, true));
        assertFalse(LegacyVideoAudio.ownsContext(null, null, true));
        assertFalse(LegacyVideoAudio.ownsContext(original, replacement, true),
                "Even equal-looking replacement contexts cannot own the old native IDs");
    }

    @Test void completeStreamIdentityOwnsPendingAudioAcrossSeekRetuneAndQualityChanges() throws Exception {
        VideoStreamIdentity original = new VideoStreamIdentity(UUID.randomUUID(), 4, UUID.randomUUID(), 9);
        VideoStreamIdentity[] replacements = {
                new VideoStreamIdentity(UUID.randomUUID(), 4, original.streamId(), 9),
                new VideoStreamIdentity(original.timelineId(), 5, original.streamId(), 9),
                new VideoStreamIdentity(original.timelineId(), 4, UUID.randomUUID(), 9),
                new VideoStreamIdentity(original.timelineId(), 4, original.streamId(), 10)
        };
        java.lang.reflect.Method bind = LegacyVideoAudio.class.getDeclaredMethod("bindIdentity", VideoStreamIdentity.class);
        bind.setAccessible(true);
        for (VideoStreamIdentity replacement : replacements) {
            LegacyVideoAudio audio = new LegacyVideoAudio(); bind.invoke(audio, original);
            LegacyDecodedAudioFrame frame = new LegacyDecodedAudioFrame(100_000, 48_000, 2, new byte[8]);
            queue(audio, "pending").add(frame); set(audio, "underruns", 3);
            bind.invoke(audio, new VideoStreamIdentity(original.timelineId(), 4, original.streamId(), 9));
            assertSame(frame, queue(audio, "pending").peek()); assertEquals(3, audio.underruns());
            bind.invoke(audio, replacement);
            assertEquals(replacement, get(audio, "identity"));
            assertTrue(queue(audio, "pending").isEmpty()); assertEquals(0, audio.underruns());
            audio.reset(); assertNull(get(audio, "identity"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Queue<Object> queue(LegacyVideoAudio audio, String name) throws Exception {
        return (Queue<Object>) get(audio, name);
    }
    private static Object get(LegacyVideoAudio audio, String name) throws Exception {
        Field field = LegacyVideoAudio.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(audio);
    }
    private static void set(LegacyVideoAudio audio, String name, Object value) throws Exception {
        Field field = LegacyVideoAudio.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(audio, value);
    }
}
