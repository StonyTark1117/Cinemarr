package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrCommands {
    private static final CinemarrCommands INSTANCE = new CinemarrCommands();
    public static void register() { MinecraftForge.EVENT_BUS.register(INSTANCE); }

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cinemarr")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("diagnostics")
                        .requires(source -> source.hasPermission(CinemarrSettings.operatorPermissionLevel()))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    CinemarrServer.instance().videoDiagnostics()), false);
                            return 1;
                        }));
        root.then(Commands.literal("retry")
                .requires(source -> source.hasPermission(CinemarrSettings.operatorPermissionLevel()))
                .executes(context -> retry(context.getSource())));
        root.then(CinemarrTvCommands.command());
        event.getDispatcher().register(root);
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CinemarrServer.instance().videoStatus()), false);
        return 1;
    }

    private static int retry(CommandSourceStack source) {
        boolean started = CinemarrServer.instance().retryPlex();
        source.sendSuccess(() -> Component.literal(started ? "Plex retry started" : "Plex retry is not available"), false);
        return started ? 1 : 0;
    }
}
