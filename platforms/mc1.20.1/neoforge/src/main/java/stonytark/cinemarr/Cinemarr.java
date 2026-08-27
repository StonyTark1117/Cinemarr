package stonytark.cinemarr;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import stonytark.cinemarr.client.CinemarrClient;
import stonytark.cinemarr.config.CinemarrConfig;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrNetwork;
import stonytark.cinemarr.server.CinemarrCommands;
import stonytark.cinemarr.server.CinemarrServer;
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.registry.CinemarrItems;

@Mod(Cinemarr.MODID)
public final class Cinemarr {
    public static final String MODID = "cinemarr";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Cinemarr() {
        FMLJavaModLoadingContext context = FMLJavaModLoadingContext.get();
        CinemarrBlocks.REGISTER.register(context.getModEventBus());
        CinemarrItems.REGISTER.register(context.getModEventBus());
        migrateClientConfig("neoforge");
        CinemarrSettings.installClient(CinemarrConfig.clientValues());
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CinemarrConfig.CLIENT_SPEC);
        CinemarrNetwork.register();
        CinemarrServer.register();
        CinemarrCommands.register();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CinemarrClient.register(context));
    }

    private static void migrateClientConfig(String loader) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        try { CanonicalConfigFiles.loadClientForLoader(FMLPaths.CONFIGDIR.get(), loader); }
        catch (Exception error) { throw new IllegalStateException("Unable to migrate Cinemarr client settings", error); }
    }
}
