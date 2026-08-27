package stonytark.cinemarr.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.platform.CinemarrSettings;

public final class CinemarrCommands {
    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->register(dispatcher));}
    public static void registerQuiltFallback(CommandDispatcher<CommandSourceStack> dispatcher){if(dispatcher.getRoot().getChild("cinemarr")==null)register(dispatcher);}
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        LiteralArgumentBuilder<CommandSourceStack> root=Commands.literal("cinemarr")
                .executes(context->status(context.getSource()))
                .then(Commands.literal("status").executes(context->status(context.getSource())))
                .then(Commands.literal("diagnostics").requires(source->CinemarrPermissions.has(source,CinemarrSettings.operatorPermissionLevel())).executes(context->{context.getSource().sendSuccess(()->Component.literal(CinemarrServer.instance().videoDiagnostics()),false);return 1;}));
        dispatcher.register(root);
    }
    private static int status(CommandSourceStack source){source.sendSuccess(()->Component.literal(CinemarrServer.instance().videoStatus()),false);return 1;}
    private CinemarrCommands(){}
}
