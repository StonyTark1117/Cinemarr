package stonytark.cinemarr.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import stonytark.cinemarr.core.platform.CinemarrSettings;

import java.util.List;

/** Small diagnostic command surface; playback controls live on each TV Controller. */
public final class LegacyVideoCommands extends CommandBase {
    private final LegacyVideoManager manager;
    private final String unavailable;
    public LegacyVideoCommands(LegacyVideoManager manager, String unavailable) {
        this.manager = manager; this.unavailable = unavailable == null ? "" : unavailable;
    }
    @Override public String getCommandName() { return "cinemarr"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/cinemarr [status|diagnostics]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
    @Override public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        String action = arguments.length == 0 ? "status" : arguments[0].toLowerCase(java.util.Locale.ROOT);
        if ("status".equals(action)) { reply(sender, manager == null ? "Cinemarr video unavailable: " + unavailable : manager.status()); return; }
        if ("diagnostics".equals(action)) {
            if (!sender.canCommandSenderUseCommand(CinemarrSettings.operatorPermissionLevel(), "cinemarr")) throw new CommandException("Operator permission is required");
            reply(sender, manager == null ? "Plex=unavailable; reason=" + unavailable : manager.diagnostics()); return;
        }
        throw new CommandException(getCommandUsage(sender));
    }
    private static void reply(ICommandSender sender, String message) { sender.addChatMessage(new ChatComponentText(message)); }
    @Override @SuppressWarnings("rawtypes") public List addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        return arguments.length == 1 ? getListOfStringsMatchingLastWord(arguments, "status", "diagnostics") : null;
    }
}
