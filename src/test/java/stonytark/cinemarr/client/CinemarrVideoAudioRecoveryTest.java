package stonytark.cinemarr.client;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Queue;
import static org.junit.jupiter.api.Assertions.*;

final class CinemarrVideoAudioRecoveryTest {
    @SuppressWarnings("unchecked")
    @Test void resetRetainsSamplesRequiredByTheScheduledStart() throws Exception {
        CinemarrVideoAudio audio = new CinemarrVideoAudio();
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        long targetUs = 298_389_000L;
        long firstPtsUs = 298_400_000L;
        byte[] pcm = new byte[960 * 4]; // 20 ms, nonzero stereo PCM.
        Arrays.fill(pcm, (byte) 37);
        for (int i = 0; i < 200; i++) {
            assertTrue(stream.offer(new DecodedAudioFrame(firstPtsUs + i * 20_000L, 48_000, 2, pcm)), "initial PCM rejected");
        }
        // Model the existing twelve 250-ms backend buffers, then refill the Java bridge.
        for (int i = 0; i < 12; i++) stream.read(48_000);
        for (int i = 200; i < 350; i++) {
            assertTrue(stream.offer(new DecodedAudioFrame(firstPtsUs + i * 20_000L, 48_000, 2, pcm)), "refill PCM rejected");
        }
        Field streamField = field("stream");
        streamField.set(audio, stream);
        Queue<DecodedAudioFrame> pending = (Queue<DecodedAudioFrame>) field("pending").get(audio);
        for (int i = 350; i < 606; i++) pending.add(new DecodedAudioFrame(firstPtsUs + i * 20_000L, 48_000, 2, pcm));
        assertTrue(stream.bufferedMs() == 4_000 && stream.totalReadUs() == 3_000_000, "incorrect fixture queue depths");
        Method reset = CinemarrVideoAudio.class.getDeclaredMethod("resetChannel");
        reset.setAccessible(true);
        reset.invoke(audio);
        long scheduledUs = CinemarrVideoAudio.scheduledStartUs(targetUs);
        Method startWindow = CinemarrVideoAudio.class.getDeclaredMethod("startWindow", long.class);
        startWindow.setAccessible(true);
        Object window = startWindow.invoke(audio, scheduledUs);
        Method first = window.getClass().getDeclaredMethod("first"); first.setAccessible(true);
        Method last = window.getClass().getDeclaredMethod("last"); last.setAccessible(true);
        DecodedAudioFrame firstFrame = (DecodedAudioFrame) first.invoke(window);
        DecodedAudioFrame lastFrame = (DecodedAudioFrame) last.invoke(window);
        boolean runway = CinemarrVideoAudio.hasStartRunway(scheduledUs, firstFrame.presentationTimeUs(), endUs(firstFrame), endUs(lastFrame));
        assertTrue(runway, "Drift reset discarded program PCM needed for the next scheduled start");

    }

    @Test void recoveryKeepsProgramSamplesAndTrimsAtTheNewMediaBoundary() {
        byte[] pcm = new byte[4_000];
        for (int i = 0; i < 2_000; i++) { pcm[2*i] = (byte) i; pcm[2*i+1] = (byte) (i >>> 8); }
        VideoPcmAudioStream original = new VideoPcmAudioStream(1_000, 1);
        assertTrue(original.offer(new DecodedAudioFrame(0, 1_000, 1, pcm), 500_000));
        assertTrue(original.prependSilenceFor(1_000_000));
        original.read(4_000);
        var recovered = original.drainRecoveryFrames();
        original.close();
        assertTrue(original.drainRecoveryFrames().isEmpty());
        assertEquals(1, recovered.size(), "scheduling silence must not become program audio");
        VideoPcmAudioStream replacement = new VideoPcmAudioStream(1_000, 1);
        assertTrue(replacement.offer(recovered.getFirst(), 1_750_000));
        ByteBuffer output = replacement.read(500);
        byte[] actual = new byte[500]; output.get(actual);
        assertArrayEquals(java.util.Arrays.copyOfRange(pcm, 3_500, 4_000), actual);
        assertEquals(0, replacement.starvations());
    }

    @Test void prolongedPlaybackCannotAccumulateUnboundedRecoveryPcm() {
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        byte[] pcm = new byte[960 * 4];
        for (int i = 0; i < 3_000; i++) {
            assertTrue(stream.offer(new DecodedAudioFrame(i * 20_000L, 48_000, 2, pcm)));
            stream.read(pcm.length);
        }
        var recovered = stream.drainRecoveryFrames();
        assertEquals(400, recovered.size());
        assertEquals(52_000_000L, recovered.getFirst().presentationTimeUs());
        assertEquals(1_536_000L, recovered.stream().mapToLong(DecodedAudioFrame::byteLength).sum());
    }

    @Test void verySmallFramesAndEmptyFramesCannotAccumulateUnboundedEntries() {
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        for (int i = 0; i < 3_000; i++) {
            long pts = i * 1_000_000L / 48_000;
            assertTrue(stream.offer(new DecodedAudioFrame(pts, 48_000, 2, new byte[4])));
            assertTrue(stream.offer(new DecodedAudioFrame(pts, 48_000, 2, new byte[0])));
            stream.read(4);
        }
        var recovered = stream.drainRecoveryFrames();
        assertEquals(1_024, recovered.size());
        assertEquals(4_096, recovered.stream().mapToInt(DecodedAudioFrame::byteLength).sum());
    }

    @Test void timestampDiscontinuityEvictsObsoleteRecoverySamples() {
        VideoPcmAudioStream stream = new VideoPcmAudioStream(1_000, 1);
        for (int i = 0; i < 10; i++) {
            assertTrue(stream.offer(new DecodedAudioFrame(i * 2_000_000L, 1_000, 1, new byte[200])));
            stream.read(4_000);
        }
        var recovered = stream.drainRecoveryFrames();
        assertEquals(4, recovered.size());
        assertEquals(12_000_000L, recovered.getFirst().presentationTimeUs());
    }

    @Test void fullResetDiscardsRecoveredAudioButSoundReloadPreservesIt() throws Exception {
        CinemarrVideoAudio audio = new CinemarrVideoAudio();
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        assertTrue(stream.offer(new DecodedAudioFrame(3_000_000L, 48_000, 2, new byte[3_840])));
        Field streamField = CinemarrVideoAudio.class.getDeclaredField("stream"); streamField.setAccessible(true);
        streamField.set(audio, stream);
        stream.close(); // SoundEngine destruction can precede the resource callback.
        audio.audioEngineReloaded();
        Field pendingField = CinemarrVideoAudio.class.getDeclaredField("pending"); pendingField.setAccessible(true);
        Queue<?> pending = (Queue<?>) pendingField.get(audio);
        assertEquals(1, pending.size());
        audio.reset();
        assertTrue(pending.isEmpty());
        assertTrue(stream.drainRecoveryFrames().isEmpty());
    }

    @Test void terminalDrainDiscardsHistoryInsteadOfResurrectingFinishedProgramAudio() throws Exception {
        CinemarrVideoAudio audio = new CinemarrVideoAudio();
        VideoPcmAudioStream stream = new VideoPcmAudioStream(48_000, 2);
        assertTrue(stream.offer(new DecodedAudioFrame(3_000_000L, 48_000, 2, new byte[3_840])));
        Field streamField = CinemarrVideoAudio.class.getDeclaredField("stream"); streamField.setAccessible(true);
        streamField.set(audio, stream);
        var reset = CinemarrVideoAudio.class.getDeclaredMethod("resetChannel", boolean.class);
        reset.setAccessible(true); reset.invoke(audio, false);
        Field pendingField = CinemarrVideoAudio.class.getDeclaredField("pending"); pendingField.setAccessible(true);
        assertTrue(((Queue<?>) pendingField.get(audio)).isEmpty());
        assertTrue(stream.drainRecoveryFrames().isEmpty());
        assertNull(streamField.get(audio));
    }

    private static Field field(String name) throws Exception {
        Field field = CinemarrVideoAudio.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static long endUs(DecodedAudioFrame frame) {
        return frame.presentationTimeUs() + frame.byteLength() * 1_000_000L / (frame.channels() * 2L * frame.sampleRate());
    }
}
