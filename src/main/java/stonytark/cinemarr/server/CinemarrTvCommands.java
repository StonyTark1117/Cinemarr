package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owner/operator recovery commands shared by every modern loader. */
public final class CinemarrTvCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> command(){return Commands.literal("tv")
            .then(Commands.literal("list").executes(context->list(context.getSource(),false))
                    .then(Commands.literal("all").requires(CinemarrTvCommands::operator).executes(context->list(context.getSource(),true))))
            .then(Commands.literal("locate").then(Commands.argument("id",UuidArgument.uuid()).executes(context->locate(context.getSource(),UuidArgument.getUuid(context,"id")))))
            .then(Commands.literal("prune").requires(CinemarrTvCommands::operator).executes(context->prune(context.getSource())))
            .then(Commands.literal("unregister").then(Commands.argument("id",UuidArgument.uuid()).executes(context->unregister(context.getSource(),UuidArgument.getUuid(context,"id")))));
    }
    private static int list(CommandSourceStack source,boolean all){UUID owner=source.getEntity() instanceof ServerPlayer?( (ServerPlayer)source.getEntity()).getUUID():null;List<String> values=new ArrayList<>();for(ServerLevel level:source.getServer().getAllLevels())for(CinemarrWorldScreens.Television tv:CinemarrWorldScreens.get(level).televisions())if(all||(owner!=null&&owner.equals(tv.owner())))values.add(describe(level,tv));if(values.isEmpty()){source.sendSuccess(()->Component.literal("No registered TVs found"),false);return 0;}source.sendSuccess(()->Component.literal((all?"Registered TVs: ":"Your registered TVs: ")+values.size()),false);for(int index=0;index<Math.min(10,values.size());index++){String value=values.get(index);source.sendSuccess(()->Component.literal(value),false);}if(values.size()>10)source.sendSuccess(()->Component.literal("Showing first 10; use locate/unregister with a TV UUID"),false);return values.size();}
    private static int locate(CommandSourceStack source,UUID id){Found found=find(source,id);if(found==null||!authorized(source,found.tv)){source.sendFailure(Component.literal("TV not found or not owned by you"));return 0;}source.sendSuccess(()->Component.literal(describe(found.level,found.tv)),false);return 1;}
    private static int unregister(CommandSourceStack source,UUID id){Found found=find(source,id);if(found==null||!authorized(source,found.tv)){source.sendFailure(Component.literal("TV not found or not owned by you"));return 0;}CinemarrWorldScreens.get(found.level).removeTelevision(id);source.sendSuccess(()->Component.literal("Unregistered TV "+id+"; its blocks were left intact"),true);return 1;}
    private static int prune(CommandSourceStack source){int removed=0;for(ServerLevel level:source.getServer().getAllLevels())removed+=CinemarrWorldScreens.get(level).pruneInvalid();final int count=removed;source.sendSuccess(()->Component.literal("Pruned "+count+" invalid loaded TV registration(s)"),true);return removed;}
    private static boolean operator(CommandSourceStack source){return CinemarrPermissions.has(source,CinemarrSettings.operatorPermissionLevel());}
    private static boolean authorized(CommandSourceStack source,CinemarrWorldScreens.Television tv){return operator(source)||(source.getEntity() instanceof ServerPlayer&&((ServerPlayer)source.getEntity()).getUUID().equals(tv.owner()));}
    private static Found find(CommandSourceStack source,UUID id){for(ServerLevel level:source.getServer().getAllLevels()){CinemarrWorldScreens.Television tv=CinemarrWorldScreens.get(level).television(id);if(tv!=null)return new Found(level,tv);}return null;}
    private static String describe(ServerLevel level,CinemarrWorldScreens.Television tv){net.minecraft.core.BlockPos pos=net.minecraft.core.BlockPos.of(tv.controllerPos());return tv.id()+" @ "+level.dimension()+" "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" owner="+tv.owner()+" session="+(tv.sessionName().isBlank()?"idle":tv.sessionName());}
    private static final class Found{final ServerLevel level;final CinemarrWorldScreens.Television tv;Found(ServerLevel level,CinemarrWorldScreens.Television tv){this.level=level;this.tv=tv;}}
    private CinemarrTvCommands(){}
}
