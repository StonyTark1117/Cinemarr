package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.AudioFormat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.UUID;
import stonytark.cinemarr.core.protocol.VideoStreamIdentity;
import static org.junit.jupiter.api.Assertions.*;

final class LegacyVideoAudioRecoveryTest {
    @SuppressWarnings("unchecked")
    @Test void reloadRetainsTheSharedScheduledBoundary() throws Exception {
        LegacyVideoAudio audio = new LegacyVideoAudio();
        Field pendingField = field("pending");
        Queue<LegacyDecodedAudioFrame> pending = (Queue<LegacyDecodedAudioFrame>) pendingField.get(audio);
        long firstUs = 298_400_000L;
        for (int i = 0; i < 350; i++) pending.add(new LegacyDecodedAudioFrame(firstUs + i * 20_000L, 48_000, 2, new byte[3_840]));
        field("format").set(audio, new AudioFormat(48_000, 16, 2, true, false));
        field("queuedProgramUntilUs").set(audio, firstUs);
        Method batch = LegacyVideoAudio.class.getDeclaredMethod("queueBatch"); batch.setAccessible(true);
        long queuedUs = 0;
        while (queuedUs < 4_000_000L) queuedUs += (Long) batch.invoke(audio);
        audio.audioEngineReloaded();
        long scheduledUs = LegacyVideoAudio.scheduledStartUs(298_389_000L);
        while (!pending.isEmpty() && endUs(pending.peek()) <= scheduledUs) pending.poll();
        LegacyDecodedAudioFrame first = pending.peek(), last = first;
        for (LegacyDecodedAudioFrame frame : pending) last = frame;
        boolean runway = first != null && first.presentationTimeUs() <= scheduledUs + 100_000L
                && endUs(last) - scheduledUs >= 700_000L;
        assertTrue(runway, "Legacy reload discarded queued program samples required by the shared start boundary");

    }

    @Test void terminalDrainDoesNotResurrectFinishedAudio() throws Exception {
        LegacyVideoAudio audio = audio();
        feed(audio, new LegacyDecodedAudioFrame(0, 48_000, 2, new byte[3_840]));
        assertEquals(1, queue(audio, "recovery").size());
        Method stop = LegacyVideoAudio.class.getDeclaredMethod("stopSource", boolean.class);
        stop.setAccessible(true); stop.invoke(audio, false);
        assertTrue(queue(audio, "pending").isEmpty());
        assertTrue(queue(audio, "recovery").isEmpty());
        assertEquals(0L, field("recoveryBytes").get(audio));
    }

    @Test void repeatedReloadPreservesOrderAndDoesNotDuplicateFrames() throws Exception {
        LegacyVideoAudio audio = audio();
        LegacyDecodedAudioFrame submitted = new LegacyDecodedAudioFrame(0, 48_000, 2, new byte[3_840]);
        LegacyDecodedAudioFrame pending = new LegacyDecodedAudioFrame(20_000, 48_000, 2, new byte[3_840]);
        feed(audio, submitted); queue(audio, "pending").add(pending);
        audio.audioEngineReloaded(); audio.audioEngineReloaded();
        Queue<LegacyDecodedAudioFrame> restored = queue(audio, "pending");
        assertEquals(2, restored.size());
        assertSame(submitted, restored.remove()); assertSame(pending, restored.remove());
        assertTrue(queue(audio, "recovery").isEmpty());
    }

    @Test void prolongedPlaybackBoundsRecoveryBytesAndDuration() throws Exception {
        LegacyVideoAudio audio = audio();
        for (int i = 0; i < 3_000; i++) feed(audio, new LegacyDecodedAudioFrame(i * 20_000L, 48_000, 2, new byte[3_840]));
        Queue<LegacyDecodedAudioFrame> recovery = queue(audio, "recovery");
        assertEquals(400, recovery.size());
        assertEquals(52_000_000L, recovery.peek().presentationTimeUs());
        assertEquals(1_536_000L, field("recoveryBytes").get(audio));
    }

    @Test void tinyFramesCannotAccumulateUnboundedEntries() throws Exception {
        LegacyVideoAudio audio = audio();
        for (int i = 0; i < 3_000; i++) feed(audio, new LegacyDecodedAudioFrame(i * 1_000_000L / 48_000, 48_000, 2, new byte[4]));
        assertEquals(1_024, queue(audio, "recovery").size());
        assertEquals(4_096L, field("recoveryBytes").get(audio));
    }

    @Test void identityChangeDiscardsSubmittedProgramHistory() throws Exception {
        LegacyVideoAudio audio = audio();
        VideoStreamIdentity before = new VideoStreamIdentity(UUID.randomUUID(), 1, UUID.randomUUID(), 1);
        field("identity").set(audio, before);
        feed(audio, new LegacyDecodedAudioFrame(0, 48_000, 2, new byte[3_840]));
        Method bind = LegacyVideoAudio.class.getDeclaredMethod("bindIdentity", VideoStreamIdentity.class);
        bind.setAccessible(true);
        bind.invoke(audio, new VideoStreamIdentity(before.timelineId(), 2, before.streamId(), 2));
        assertTrue(queue(audio, "pending").isEmpty());
        assertTrue(queue(audio, "recovery").isEmpty());
        assertEquals(0L, field("recoveryBytes").get(audio));
    }

    private static LegacyVideoAudio audio() throws Exception {
        LegacyVideoAudio audio = new LegacyVideoAudio();
        field("format").set(audio, new AudioFormat(48_000, 16, 2, true, false));
        return audio;
    }
    private static void feed(LegacyVideoAudio audio, LegacyDecodedAudioFrame frame) throws Exception {
        queue(audio, "pending").add(frame);
        Method batch = LegacyVideoAudio.class.getDeclaredMethod("queueBatch"); batch.setAccessible(true);
        assertTrue((Long) batch.invoke(audio) > 0L);
    }
    @SuppressWarnings("unchecked")
    private static Queue<LegacyDecodedAudioFrame> queue(LegacyVideoAudio audio, String name) throws Exception {
        return (Queue<LegacyDecodedAudioFrame>) field(name).get(audio);
    }
    private static Field field(String name) throws Exception {
        Field field = LegacyVideoAudio.class.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static long endUs(LegacyDecodedAudioFrame frame) {
        return frame.presentationTimeUs() + frame.pcmView().length * 1_000_000L / (2L * frame.channels() * frame.sampleRate());
    }
}
