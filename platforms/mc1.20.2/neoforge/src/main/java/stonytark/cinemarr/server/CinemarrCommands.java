package stonytark.cinemarr.server;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrCommands {
    private static final CinemarrCommands INSTANCE = new CinemarrCommands();
    public static void register() { NeoForge.EVENT_BUS.register(INSTANCE); }

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> root=Commands.literal("cinemarr")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("diagnostics")
                        .requires(source -> source.hasPermission(CinemarrSettings.operatorPermissionLevel()))
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal(CinemarrServer.instance().videoDiagnostics()), false);
                            return 1;
                        }));
        root.then(CinemarrTvCommands.command());
        event.getDispatcher().register(root);
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CinemarrServer.instance().videoStatus()), false);
        return 1;
    }
}
