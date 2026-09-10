package stonytark.cinemarr.core.client;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/** Serializes connection ownership and resource cleanup on the client/render executor. */
public final class ClientConnectionLifecycle {
    private final Executor clientExecutor;
    private final Runnable reset;
    // Network callbacks invalidate eligibility immediately; native cleanup stays
    // on the client thread even when Minecraft defers executor tasks past a tick.
    private final AtomicReference<Object> connected = new AtomicReference<>();
    private volatile Object activeConnection;

    public ClientConnectionLifecycle(Executor clientExecutor, Runnable reset) {
        this.clientExecutor = Objects.requireNonNull(clientExecutor, "clientExecutor");
        this.reset = Objects.requireNonNull(reset, "reset");
    }

    public void joined(Object connection, Runnable hello) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(hello, "hello");
        connected.set(connection);
        clientExecutor.execute(() -> {
            if (connected.get() != connection) return;
            if (activeConnection == connection) return;
            activeConnection = null;
            // A new connection cannot inherit a previous clock, decoder or texture,
            // even if the old network thread has not delivered DISCONNECT yet.
            reset.run();
            if (connected.get() != connection) return;
            activeConnection = connection;
            hello.run();
        });
    }

    public void disconnected(Object connection) {
        Objects.requireNonNull(connection, "connection");
        connected.compareAndSet(connection, null);
        clientExecutor.execute(() -> {
            // A delayed callback from the old socket must not tear down its replacement.
            if (activeConnection != connection) return;
            activeConnection = null;
            reset.run();
        });
    }

    /** Whether a packet still belongs to the joined, live connection. */
    public boolean isActive(Object connection) {
        return connection != null && activeConnection == connection && connected.get() == connection;
    }

    /**
     * Called on the client/render thread before any media tick. Pass null when
     * the transport is closed or Minecraft has detached its play handler.
     * Retire stale media now instead of waiting for a queued disconnect task.
     */
    public boolean prepareTick(Object connection) {
        Object previous = activeConnection;
        if (previous != null && !isActive(connection)) {
            activeConnection = null;
            connected.compareAndSet(previous, null);
            reset.run();
        }
        return isActive(connection);
    }
}
