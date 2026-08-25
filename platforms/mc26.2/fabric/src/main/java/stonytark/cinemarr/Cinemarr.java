package stonytark.cinemarr;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.server.CinemarrCommands;
import stonytark.cinemarr.server.CinemarrServer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Cinemarr implements ModInitializer {
    public static final String MODID = "cinemarr";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    @Override public void onInitialize() {
        initializeOnce();
    }

    public static void bootstrapQuilt() {
        if (FabricLoader.getInstance().isModLoaded("quilt_loader")) initializeOnce();
    }

    private static void initializeOnce() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        CinemarrNetwork.register();
        CinemarrServer.register();
        CinemarrCommands.register();
    }
}
