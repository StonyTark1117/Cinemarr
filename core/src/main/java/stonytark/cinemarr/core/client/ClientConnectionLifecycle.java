package stonytark.cinemarr.core.client;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Serializes connection ownership and resource cleanup on the client/render executor. */
public final class ClientConnectionLifecycle {
    private final Executor clientExecutor;
    private final Runnable reset;
    // Only accessed inside tasks on the supplied serial client executor.
    private Object activeConnection;

    public ClientConnectionLifecycle(Executor clientExecutor, Runnable reset) {
        this.clientExecutor = Objects.requireNonNull(clientExecutor, "clientExecutor");
        this.reset = Objects.requireNonNull(reset, "reset");
    }

    public void joined(Object connection, Runnable hello) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(hello, "hello");
        clientExecutor.execute(() -> {
            if (activeConnection == connection) return;
            activeConnection = null;
            // A new connection cannot inherit a previous clock, decoder or texture,
            // even if the old network thread has not delivered DISCONNECT yet.
            reset.run();
            activeConnection = connection;
            hello.run();
        });
    }

    public void disconnected(Object connection) {
        Objects.requireNonNull(connection, "connection");
        clientExecutor.execute(() -> {
            // A delayed callback from the old socket must not tear down its replacement.
            if (activeConnection != connection) return;
            activeConnection = null;
            reset.run();
        });
    }
}
