package stonytark.cinemarr;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.server.CinemarrCommands;
import stonytark.cinemarr.server.CinemarrServer;

public final class Cinemarr implements ModInitializer {
    public static final String MODID = "cinemarr";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override public void onInitialize() {
        CinemarrNetwork.register();
        CinemarrServer.register();
        CinemarrCommands.register();
    }
}
