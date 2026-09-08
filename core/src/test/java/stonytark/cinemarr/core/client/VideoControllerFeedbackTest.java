package stonytark.cinemarr.core.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class VideoControllerFeedbackTest {
    @Test void authoritativeBlankClearsOldBufferingAndStatusDoesNotExpire() {
        AtomicLong now = new AtomicLong();
        VideoControllerFeedback feedback = new VideoControllerFeedback(now::get);
        feedback.updateServerMessage("Buffering");
        feedback.updateServerMessage("");
        assertEquals("", feedback.message());
        feedback.updateServerMessage("Paused");
        now.addAndGet(VideoControllerFeedback.LOCAL_NOTICE_NANOS * 100);
        assertEquals("Paused", feedback.message());
        feedback.updateServerMessage(null);
        assertEquals("", feedback.message());
    }

    @Test void deniedRequestNeverDispatchesAndSurvivesRepeatedOrChangedSnapshots() {
        AtomicLong now = new AtomicLong();
        AtomicInteger sent = new AtomicInteger();
        VideoControllerFeedback feedback = new VideoControllerFeedback(now::get);
        assertFalse(feedback.request(false, false, "Queue requested", sent::incrementAndGet));
        feedback.updateServerMessage("Playing");
        feedback.updateServerMessage("Playing");
        feedback.updateServerMessage("");
        assertEquals(0, sent.get());
        assertEquals(VideoControllerFeedback.CONTROL_DENIED, feedback.message());
        now.set(VideoControllerFeedback.LOCAL_NOTICE_NANOS - 1);
        assertEquals(VideoControllerFeedback.CONTROL_DENIED, feedback.message());
        now.incrementAndGet();
        assertEquals("", feedback.message());
    }

    @Test void successfulDispatchShowsRequestNotCompletionAndNewProgressSupersedesIt() {
        AtomicLong now = new AtomicLong();
        AtomicInteger sent = new AtomicInteger();
        VideoControllerFeedback feedback = new VideoControllerFeedback(now::get);
        assertTrue(feedback.request(true, false, "Play requested", sent::incrementAndGet));
        assertEquals(1, sent.get());
        feedback.updateServerMessage("");
        assertEquals("Play requested", feedback.message());
        feedback.updateServerMessage("Playing");
        assertEquals("Playing", feedback.message());
        feedback.request(true, false, "Queue requested", sent::incrementAndGet);
        now.addAndGet(VideoControllerFeedback.LOCAL_NOTICE_NANOS);
        assertEquals("Playing", feedback.message());
    }

    @Test void tuneCanReachServerAndItsRejectionRemainsVisibleUntilExpiry() {
        AtomicLong now = new AtomicLong();
        AtomicInteger sent = new AtomicInteger();
        VideoControllerFeedback feedback = new VideoControllerFeedback(now::get);
        assertTrue(feedback.request(false, true, "", sent::incrementAndGet));
        assertEquals(1, sent.get());
        feedback.error("Only the TV owner or an operator can control this TV");
        feedback.updateServerMessage("Playing");
        assertEquals("Only the TV owner or an operator can control this TV", feedback.message());
        now.addAndGet(VideoControllerFeedback.LOCAL_NOTICE_NANOS);
        assertEquals("Playing", feedback.message());
    }

    @Test void failedSendCannotOverwriteAnErrorWithOptimisticFeedback() {
        VideoControllerFeedback feedback = new VideoControllerFeedback();
        feedback.error("Previous server error");
        assertThrows(IllegalStateException.class, () -> feedback.request(true, false, "Play requested",
                () -> { throw new IllegalStateException("send failed"); }));
        assertEquals("Previous server error", feedback.message());
    }

    @Test void localExpiryHandlesNanoTimeWraparound() {
        AtomicLong now = new AtomicLong(Long.MAX_VALUE - 10);
        VideoControllerFeedback feedback = new VideoControllerFeedback(now::get);
        feedback.error("Temporary error");
        now.addAndGet(VideoControllerFeedback.LOCAL_NOTICE_NANOS);
        assertEquals("", feedback.message());
    }
}
