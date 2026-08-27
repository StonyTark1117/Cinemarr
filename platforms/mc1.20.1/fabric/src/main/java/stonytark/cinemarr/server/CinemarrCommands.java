package stonytark.cinemarr.server;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import stonytark.cinemarr.core.platform.CinemarrSettings;
public final class CinemarrCommands {public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(Commands.literal("cinemarr").executes(c->status(c.getSource())).then(Commands.literal("status").executes(c->status(c.getSource()))).then(Commands.literal("diagnostics").requires(s->s.hasPermission(CinemarrSettings.operatorPermissionLevel())).executes(c->{c.getSource().sendSuccess(()->Component.literal(CinemarrServer.instance().videoDiagnostics()),false);return 1;}))));}private static int status(net.minecraft.commands.CommandSourceStack source){source.sendSuccess(()->Component.literal(CinemarrServer.instance().videoStatus()),false);return 1;}private CinemarrCommands(){}}
