package stonytark.cinemarr.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;

/** Keeps numeric operator-level policy out of the shared coordinator. */
public final class CinemarrPermissions {
    public static boolean has(ServerPlayer player, int level) {
        return player.hasPermissions(level);
    }
    public static boolean has(CommandSourceStack source,int level){return source.hasPermission(level);}

    private CinemarrPermissions() {}
}
