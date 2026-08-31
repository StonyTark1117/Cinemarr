package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cinemarr")
                    .executes(context -> status(context.getSource()))
                    .then(Commands.literal("status").executes(context -> status(context.getSource())))
                    .then(Commands.literal("diagnostics")
                            .requires(source -> source.hasPermission(CinemarrSettings.operatorPermissionLevel()))
                            .executes(context -> {
                                context.getSource().sendSuccess(() -> Component.literal(
                                        CinemarrServer.instance().videoDiagnostics()), false);
                                return 1;
                            }))
                    .then(Commands.literal("retry")
                            .requires(source -> source.hasPermission(CinemarrSettings.operatorPermissionLevel()))
                            .executes(context -> retry(context.getSource())));
            root.then(CinemarrTvCommands.command());
            dispatcher.register(root);
        });
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CinemarrServer.instance().videoStatus()), false);
        return 1;
    }
    private static int retry(CommandSourceStack source) { boolean started=CinemarrServer.instance().retryPlex();source.sendSuccess(()->Component.literal(started?"Cinemarr Plex connection attempt started":"Cinemarr Plex is disabled or not configured"),false);return started?1:0; }
    private CinemarrCommands() {}
}
