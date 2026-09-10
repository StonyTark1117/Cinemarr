package stonytark.cinemarr.network;

import stonytark.cinemarr.core.protocol.CinemarrMessage;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ClientPayloadBridge {
    private static BiConsumer<Object, CinemarrMessage> receiver = (connection, payload) -> {};

    /** For adapters which already validate the packet's origin before delivery. */
    public static void install(Consumer<CinemarrMessage> value) {
        Objects.requireNonNull(value, "receiver");
        receiver = (connection, payload) -> value.accept(payload);
    }

    /** Evaluate ownership at delivery time, after any loader main-thread queue. */
    public static void install(Predicate<Object> activeConnection, Consumer<CinemarrMessage> value) {
        Objects.requireNonNull(activeConnection, "activeConnection");
        Objects.requireNonNull(value, "receiver");
        receiver = (connection, payload) -> {
            if (connection != null && activeConnection.test(connection)) value.accept(payload);
        };
    }

    public static void accept(CinemarrMessage payload) { accept(null, payload); }
    public static void accept(Object connection, CinemarrMessage payload) { receiver.accept(connection, payload); }
    private ClientPayloadBridge() {}
}
