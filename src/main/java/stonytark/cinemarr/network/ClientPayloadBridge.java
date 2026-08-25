package stonytark.cinemarr.network;

import stonytark.cinemarr.core.protocol.CinemarrMessage;
import java.util.function.Consumer;

public final class ClientPayloadBridge {
    private static Consumer<CinemarrMessage> receiver = payload -> {};
    public static void install(Consumer<CinemarrMessage> value) { receiver = value; }
    public static void accept(CinemarrMessage payload) { receiver.accept(payload); }
    private ClientPayloadBridge() {}
}
