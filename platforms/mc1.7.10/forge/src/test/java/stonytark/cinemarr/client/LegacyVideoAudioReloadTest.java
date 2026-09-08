package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Queue;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegacyVideoAudioReloadTest {
    @Test void reloadAfterContextDestructionDiscardsNativeIdsWithoutCallingOpenAl() throws Exception {
        LegacyVideoAudio audio = new LegacyVideoAudio();
        UUID session = UUID.randomUUID();
        set(audio, "source", 42);
        set(audio, "prepared", true);
        set(audio, "started", true);
        set(audio, "sourcePaused", true);
        set(audio, "sessionId", session);
        set(audio, "generation", 11L);
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
        assertEquals(session, get(audio, "sessionId"));
        assertEquals(11L, get(audio, "generation"));
        assertEquals(3, audio.underruns(), "Reload must not erase previously recorded failures");
        assertFalse(audio.ready());
        assertDoesNotThrow(audio::audioEngineReloaded, "Repeated reload must be idempotent");
        assertDoesNotThrow(audio::reset, "Disconnect after reload must not touch obsolete native IDs");
        assertTrue(queue(audio, "pending").isEmpty());
        assertNull(get(audio, "sessionId"));
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
