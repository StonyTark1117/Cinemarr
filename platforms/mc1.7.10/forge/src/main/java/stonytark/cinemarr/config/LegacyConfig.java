package stonytark.cinemarr.config;

import net.minecraft.server.MinecraftServer;
import stonytark.cinemarr.core.platform.CanonicalConfigFiles;
import stonytark.cinemarr.core.platform.CinemarrSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Installs the same canonical configuration files used by every modern target. */
public final class LegacyConfig {
    public static CanonicalConfigFiles.ClientConfig installClient(File configDirectory) throws IOException {
        Path config = configDirectory.toPath();
        CanonicalConfigFiles.ClientConfig values = CanonicalConfigFiles.loadClientForLoader(config, "legacy");
        CinemarrSettings.installClient(values);
        return values;
    }

    public static CanonicalConfigFiles.ServerConfig installServer(MinecraftServer server) throws IOException {
        String worldDirectory = server.getFolderName();
        if (worldDirectory == null || worldDirectory.trim().isEmpty()) {
            throw new IOException("Minecraft did not expose the active world directory");
        }
        Path canonical = server.getFile(worldDirectory + "/serverconfig/" + CanonicalConfigFiles.SERVER_FILE_NAME).toPath();
        Path worldServerConfig = canonical.getParent();
        Path gameDirectory = server.getFile(".").toPath();
        Path config = gameDirectory.resolve("config");
        CanonicalConfigFiles.ServerConfig values = CanonicalConfigFiles.loadServer(
                canonical,
                worldServerConfig.resolve("pampmod-server.toml"),
                worldServerConfig.resolve("cinemarr-common.toml"),
                config.resolve(CanonicalConfigFiles.SERVER_FILE_NAME),
                config.resolve("cinemarr-server-legacy.toml"),
                config.resolve("cinemarr-server-forge.toml"),
                config.resolve("cinemarr-server-fabric.toml"),
                config.resolve("cinemarr-server-neoforge.toml"),
                config.resolve("pampmod-server-legacy.toml"),
                config.resolve("pampmod-server-forge.toml"),
                config.resolve("pampmod-server-fabric.toml"),
                config.resolve("pampmod-server-neoforge.toml"),
                config.resolve("cinemarr-common.toml"),
                config.resolve("pampmod-server.toml"),
                config.resolve("pampmod-common.toml"),
                config.resolve("cinemarr.toml"),
                config.resolve("pampmod.toml"));
        CinemarrSettings.installServer(values);
        return values;
    }

    private LegacyConfig() {}
}
