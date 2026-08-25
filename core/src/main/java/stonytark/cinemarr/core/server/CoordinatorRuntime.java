package stonytark.cinemarr.core.server;

import stonytark.cinemarr.core.platform.CoreLogger;
/** Convenience composition of the narrow services required by the shared coordinator. */
public interface CoordinatorRuntime<P> extends PlayerDirectory<P>, PacketTransport<P>,
        MainThreadScheduler, ServerStorage {
    CoreLogger logger();
}
