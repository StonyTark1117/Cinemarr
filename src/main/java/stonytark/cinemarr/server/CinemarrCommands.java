package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import stonytark.cinemarr.core.platform.CinemarrSettings;

/** Small server-safe command surface; playback controls remain bound to owned TV Controllers. */
public final class CinemarrCommands {
    @SubscribeEvent public void register(RegisterCommandsEvent event){
        LiteralArgumentBuilder<CommandSourceStack> root=Commands.literal("cinemarr")
                .executes(context->status(context.getSource()))
                .then(Commands.literal("status").executes(context->status(context.getSource())))
                .then(Commands.literal("diagnostics").requires(source->source.hasPermission(CinemarrSettings.operatorPermissionLevel()))
                        .executes(context->{context.getSource().sendSuccess(()->Component.literal(CinemarrServer.instance().videoDiagnostics()),false);return 1;}))
                .then(CinemarrTvCommands.command());
        event.getDispatcher().register(root);
    }
    private static int status(CommandSourceStack source){source.sendSuccess(()->Component.literal(CinemarrServer.instance().videoStatus()),false);return 1;}
}
