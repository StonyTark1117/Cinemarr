package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrCommands {
    private static final CinemarrCommands INSTANCE = new CinemarrCommands();
    public static void register() { RegisterCommandsEvent.BUS.addListener(INSTANCE::commands); }

    public void commands(RegisterCommandsEvent event) {
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
        root.then(CinemarrTvCommands.command());
        event.getDispatcher().register(root);
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(CinemarrServer.instance().videoStatus()), false);
        return 1;
    }
}
