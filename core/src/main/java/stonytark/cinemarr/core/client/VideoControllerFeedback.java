package stonytark.cinemarr.core.client;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Separates authoritative playback status from bounded local command feedback. */
public final class VideoControllerFeedback {
    public static final String CONTROL_DENIED = "You do not have permission to control this TV";
    public static final long LOCAL_NOTICE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final LongSupplier nanoClock;
    private String serverMessage = "", localMessage = "";
    private long localStartedAt;
    private boolean localError;

    public VideoControllerFeedback() { this(System::nanoTime); }
    public VideoControllerFeedback(LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public void updateServerMessage(String message) {
        String next = normalize(message);
        if (next.equals(serverMessage)) return;
        serverMessage = next;
        // New authoritative progress supersedes an optimistic request, but an
        // unrelated playback update must not immediately hide a local error.
        if (!localError) localMessage = "";
    }

    public void error(String message) { local(message, true); }

    public boolean request(boolean mayControl, boolean tuning, String pendingMessage, Runnable send) {
        if (!mayControl && !tuning) {
            error(CONTROL_DENIED);
            return false;
        }
        Objects.requireNonNull(send, "send").run();
        // This is request feedback, not a claim that the server accepted it.
        local(pendingMessage, false);
        return true;
    }

    public String message() {
        if (!localMessage.isEmpty() && nanoClock.getAsLong() - localStartedAt >= LOCAL_NOTICE_NANOS) {
            localMessage = "";
            localError = false;
        }
        return localMessage.isEmpty() ? serverMessage : localMessage;
    }

    private void local(String message, boolean error) {
        localMessage = normalize(message);
        localError = error;
        localStartedAt = nanoClock.getAsLong();
    }

    private static String normalize(String message) { return message == null ? "" : message.trim(); }
}
