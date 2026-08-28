package stonytark.cinemarr.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.screen.LegacyBlockPos;
import stonytark.cinemarr.screen.LegacyWorldScreens;

import java.util.List;
import java.util.UUID;

/** Small diagnostic command surface; playback controls live on each TV Controller. */
public final class LegacyVideoCommands extends CommandBase {
    private final LegacyVideoManager manager;
    private final MinecraftServer server;
    private final String unavailable;
    public LegacyVideoCommands(MinecraftServer server, LegacyVideoManager manager, String unavailable) {
        this.server=server;this.manager = manager; this.unavailable = unavailable == null ? "" : unavailable;
    }
    @Override public String getCommandName() { return "cinemarr"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/cinemarr [status|diagnostics|tv list|tv locate <uuid>|tv unregister <uuid>]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
    @Override public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        String action = arguments.length == 0 ? "status" : arguments[0].toLowerCase(java.util.Locale.ROOT);
        if ("status".equals(action)) { reply(sender, manager == null ? "Cinemarr video unavailable: " + unavailable : manager.status()); return; }
        if ("diagnostics".equals(action)) {
            if (!sender.canCommandSenderUseCommand(CinemarrSettings.operatorPermissionLevel(), "cinemarr")) throw new CommandException("Operator permission is required");
            reply(sender, manager == null ? "Plex=unavailable; reason=" + unavailable : manager.diagnostics()); return;
        }
        if("tv".equals(action)){television(sender,arguments);return;}
        throw new CommandException(getCommandUsage(sender));
    }
    private void television(ICommandSender sender,String[] arguments)throws CommandException{
        if(arguments.length<2)throw new CommandException(getCommandUsage(sender));String action=arguments[1].toLowerCase(java.util.Locale.ROOT);boolean operator=sender.canCommandSenderUseCommand(CinemarrSettings.operatorPermissionLevel(),"cinemarr");UUID owner=sender instanceof EntityPlayerMP?((EntityPlayerMP)sender).getUniqueID():null;
        if("list".equals(action)){boolean all=arguments.length>2&&"all".equalsIgnoreCase(arguments[2]);if(all&&!operator)throw new CommandException("Operator permission is required");int count=0;for(net.minecraft.world.WorldServer world:server.worldServers)if(world!=null)for(LegacyWorldScreens.Television tv:LegacyWorldScreens.get(world).televisions())if(all||(owner!=null&&owner.equals(tv.owner()))){if(count<10)reply(sender,describe(world,tv));count++;}reply(sender,(all?"Registered TVs: ":"Your registered TVs: ")+count);return;}
        if(arguments.length<3)throw new CommandException(getCommandUsage(sender));UUID id;try{id=UUID.fromString(arguments[2]);}catch(IllegalArgumentException invalid){throw new CommandException("Invalid TV UUID");}Found found=find(id);if(found==null||(!operator&&(owner==null||!owner.equals(found.tv.owner()))))throw new CommandException("TV not found or not owned by you");if("locate".equals(action)){reply(sender,describe(found.world,found.tv));return;}if("unregister".equals(action)){LegacyWorldScreens.get(found.world).removeTelevision(id);reply(sender,"Unregistered TV "+id+"; its blocks were left intact");return;}throw new CommandException(getCommandUsage(sender));
    }
    private Found find(UUID id){for(net.minecraft.world.WorldServer world:server.worldServers)if(world!=null){LegacyWorldScreens.Television tv=LegacyWorldScreens.get(world).television(id);if(tv!=null)return new Found(world,tv);}return null;}
    private static String describe(net.minecraft.world.WorldServer world,LegacyWorldScreens.Television tv){return tv.id()+" @ dimension "+world.provider.dimensionId+" "+LegacyBlockPos.x(tv.controllerPos())+" "+LegacyBlockPos.y(tv.controllerPos())+" "+LegacyBlockPos.z(tv.controllerPos())+" owner="+tv.owner()+" session="+(tv.sessionName().isEmpty()?"idle":tv.sessionName());}
    private static final class Found{final net.minecraft.world.WorldServer world;final LegacyWorldScreens.Television tv;Found(net.minecraft.world.WorldServer world,LegacyWorldScreens.Television tv){this.world=world;this.tv=tv;}}
    private static void reply(ICommandSender sender, String message) { sender.addChatMessage(new ChatComponentText(message)); }
    @Override @SuppressWarnings("rawtypes") public List addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        return arguments.length == 1 ? getListOfStringsMatchingLastWord(arguments, "status", "diagnostics", "tv") : arguments.length==2&&"tv".equalsIgnoreCase(arguments[0])?getListOfStringsMatchingLastWord(arguments,"list","locate","unregister"):null;
    }
}
