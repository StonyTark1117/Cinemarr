package stonytark.cinemarr;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.server.CinemarrCommands;
import stonytark.cinemarr.server.CinemarrServer;
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.registry.CinemarrItems;

public final class Cinemarr implements ModInitializer {
    public static final String MODID = "cinemarr";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override public void onInitialize() {
        CinemarrBlocks.register();
        CinemarrItems.register();
        stonytark.cinemarr.registry.CinemarrCreativeTabs.register();
        CinemarrNetwork.register();
        CinemarrServer.register();
        CinemarrCommands.register();
    }
}
