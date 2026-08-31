package stonytark.cinemarr.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.screen.LegacyWorldScreens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Diagnostic and owner/operator TV recovery commands for Forge 1.7.10. */
public final class LegacyVideoCommands extends CommandBase {
    private static final int PAGE_SIZE = 8;
    private final MinecraftServer server;

    public LegacyVideoCommands(MinecraftServer server) { this.server=server; }
    @Override public String getCommandName(){return "cinemarr";}
    @Override public String getCommandUsage(ICommandSender sender){return "/cinemarr [status|diagnostics|retry|tv list [page]|tv list all [page]|tv list owner <player> [page]|tv locate <uuid>|tv unregister <uuid>|tv prune]";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender){return true;}

    @Override public void processCommand(ICommandSender sender,String[] arguments)throws CommandException{
        String action=arguments.length==0?"status":arguments[0].toLowerCase(Locale.ROOT);
        if("status".equals(action)){reply(sender,Cinemarr.videoStatus());return;}
        if("diagnostics".equals(action)){requireOperator(sender);reply(sender,Cinemarr.videoDiagnostics());return;}
        if("retry".equals(action)){requireOperator(sender);boolean started=Cinemarr.retryPlex();reply(sender,started?"Plex retry started":"Plex retry is not available");return;}
        if("tv".equals(action)){television(sender,arguments);return;}
        throw new CommandException(getCommandUsage(sender));
    }

    private void television(ICommandSender sender,String[] arguments)throws CommandException{
        if(arguments.length<2)throw new CommandException(getCommandUsage(sender));
        String action=arguments[1].toLowerCase(Locale.ROOT);
        if("list".equals(action)){list(sender,arguments);return;}
        if("prune".equals(action)){requireOperator(sender);int removed=0;for(net.minecraft.world.WorldServer world:server.worldServers)if(world!=null)removed+=LegacyWorldScreens.get(world).pruneInvalid();reply(sender,"Pruned "+removed+" invalid loaded TV registration(s); unloaded registrations were left intact");return;}
        if(arguments.length<3)throw new CommandException(getCommandUsage(sender));
        UUID id;try{id=UUID.fromString(arguments[2]);}catch(IllegalArgumentException invalid){throw new CommandException("Invalid TV UUID");}
        TelevisionLifecycle.Registration value=TelevisionLifecycle.registration(id);
        if(value==null||!authorized(sender,value))throw new CommandException("TV not found or not owned by you");
        if("locate".equals(action)){reply(sender,describe(value));return;}
        if("unregister".equals(action)){if(!TelevisionLifecycle.unregister(id))throw new CommandException("TV was already unregistered");reply(sender,"Unregistered TV "+id+"; its blocks were left intact");return;}
        throw new CommandException(getCommandUsage(sender));
    }

    private void list(ICommandSender sender,String[] arguments)throws CommandException{
        UUID owner=sender instanceof EntityPlayerMP?((EntityPlayerMP)sender).getUniqueID():null;
        String label="Your";int page=1;
        if(arguments.length>2&&"all".equalsIgnoreCase(arguments[2])){requireOperator(sender);owner=null;label="All";if(arguments.length>3)page=page(arguments[3]);}
        else if(arguments.length>2&&"owner".equalsIgnoreCase(arguments[2])){requireOperator(sender);if(arguments.length<4)throw new CommandException(getCommandUsage(sender));try{owner=UUID.fromString(arguments[3]);label=arguments[3]+"'s";}catch(IllegalArgumentException notUuid){EntityPlayerMP player=online(arguments[3]);if(player==null)throw new CommandException("Owner must be an online player name or UUID");owner=player.getUniqueID();label=player.getCommandSenderName()+"'s";}if(arguments.length>4)page=page(arguments[4]);}
        else if(arguments.length>2)page=page(arguments[2]);
        if(owner==null&&!isOperator(sender)&&!(sender instanceof EntityPlayerMP))throw new CommandException("A player must run this command, or use 'tv list all'");
        List<TelevisionLifecycle.Registration> values=new ArrayList<TelevisionLifecycle.Registration>();
        for(TelevisionLifecycle.Registration value:TelevisionLifecycle.registrations())if(owner==null||owner.equals(value.owner()))values.add(value);
        Collections.sort(values,new Comparator<TelevisionLifecycle.Registration>(){@Override public int compare(TelevisionLifecycle.Registration a,TelevisionLifecycle.Registration b){int result=a.dimension().compareTo(b.dimension());if(result==0)result=Integer.compare(a.controllerX(),b.controllerX());if(result==0)result=Integer.compare(a.controllerY(),b.controllerY());if(result==0)result=Integer.compare(a.controllerZ(),b.controllerZ());return result==0?a.id().toString().compareTo(b.id().toString()):result;}});
        int pages=Math.max(1,(values.size()+PAGE_SIZE-1)/PAGE_SIZE);if(page>pages)throw new CommandException("Page "+page+" does not exist; last page is "+pages);
        reply(sender,label+" registered TVs: "+values.size()+" (page "+page+"/"+pages+")");int first=(page-1)*PAGE_SIZE,end=Math.min(values.size(),first+PAGE_SIZE);for(int index=first;index<end;index++)reply(sender,describe(values.get(index)));
    }

    private EntityPlayerMP online(String name){for(Object value:server.getConfigurationManager().playerEntityList)if(value instanceof EntityPlayerMP&&((EntityPlayerMP)value).getCommandSenderName().equalsIgnoreCase(name))return (EntityPlayerMP)value;return null;}
    private static int page(String value)throws CommandException{try{int page=Integer.parseInt(value);if(page<1)throw new NumberFormatException();return page;}catch(NumberFormatException invalid){throw new CommandException("Page must be a positive integer");}}
    private static boolean authorized(ICommandSender sender,TelevisionLifecycle.Registration value){return isOperator(sender)||(sender instanceof EntityPlayerMP&&((EntityPlayerMP)sender).getUniqueID().equals(value.owner()));}
    private static boolean isOperator(ICommandSender sender){return sender.canCommandSenderUseCommand(CinemarrSettings.operatorPermissionLevel(),"cinemarr");}
    private static void requireOperator(ICommandSender sender)throws CommandException{if(!isOperator(sender))throw new CommandException("Operator permission is required");}
    private static String describe(TelevisionLifecycle.Registration value){return value.id()+" dimension="+value.dimension()+" controller="+value.controllerX()+","+value.controllerY()+","+value.controllerZ()+" owner="+value.owner()+" pixels="+value.pixelCount()+" session="+(value.sessionName().isEmpty()?"idle":value.sessionName())+" validity="+value.validation().name().toLowerCase(Locale.ROOT)+" playback="+(value.attached()?"attached":"detached");}
    private static void reply(ICommandSender sender,String message){sender.addChatMessage(new ChatComponentText(message));}
    @Override @SuppressWarnings("rawtypes") public List addTabCompletionOptions(ICommandSender sender,String[] arguments){return arguments.length==1?getListOfStringsMatchingLastWord(arguments,"status","diagnostics","retry","tv"):arguments.length==2&&"tv".equalsIgnoreCase(arguments[0])?getListOfStringsMatchingLastWord(arguments,"list","locate","unregister","prune"):arguments.length==3&&"tv".equalsIgnoreCase(arguments[0])&&"list".equalsIgnoreCase(arguments[1])?getListOfStringsMatchingLastWord(arguments,"all","owner"):null;}
}
