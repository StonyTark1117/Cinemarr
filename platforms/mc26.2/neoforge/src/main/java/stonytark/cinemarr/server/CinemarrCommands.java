package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrCommands {
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cinemarr")
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("diagnostics")
                        .requires(source -> CinemarrPermissions.has(source, CinemarrSettings.operatorPermissionLevel()))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal(
                                    CinemarrServer.instance().videoDiagnostics()), false);
                            return 1;
                        }));
        event.getDispatcher().register(root);
    }
    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CinemarrServer.instance().videoStatus()), false);
        return 1;
    }
}
