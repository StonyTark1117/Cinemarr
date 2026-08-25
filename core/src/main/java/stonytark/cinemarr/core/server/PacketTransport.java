package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.protocol.CinemarrMessage;

/** Loader-native delivery boundary for canonical protocol-5 messages. */
public interface PacketTransport<P> {
    void send(P player, CinemarrMessage message);
}
