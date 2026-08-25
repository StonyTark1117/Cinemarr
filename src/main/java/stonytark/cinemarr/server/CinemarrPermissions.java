package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;

/** Keeps numeric operator-level policy out of the shared coordinator. */
public final class CinemarrPermissions {
    public static boolean has(ServerPlayer player, int level) {
        return player.hasPermissions(level);
    }

    private CinemarrPermissions() {}
}
