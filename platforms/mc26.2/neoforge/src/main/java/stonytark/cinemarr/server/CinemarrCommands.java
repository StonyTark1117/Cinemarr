package stonytark.cinemarr.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.network.CinemarrPayloads;
import stonytark.cinemarr.network.CinemarrNetwork;
import java.util.List;

public final class CinemarrCommands {
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cinemarr")
                .executes(c -> { CinemarrNetwork.sendToPlayer(c.getSource().getPlayerOrException(), new CinemarrPayloads.OpenScreen()); return 1; })
                .then(Commands.literal("status").executes(c -> { GlobalPlayer p = CinemarrServer.instance().player(); c.getSource().sendSuccess(() -> Component.literal(p == null ? "Cinemarr is unavailable" : p.status()), false); return 1; }));
        root.then(operator("pause", CinemarrPayloads.ControlAction.PAUSE));
        root.then(operator("resume", CinemarrPayloads.ControlAction.RESUME));
        root.then(operator("skip", CinemarrPayloads.ControlAction.SKIP));
        root.then(operator("clear", CinemarrPayloads.ControlAction.CLEAR));
        root.then(Commands.literal("reload").requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel())).executes(c -> { GlobalPlayer p = CinemarrServer.instance().player(); if (p != null) p.validatePlex(); c.getSource().sendSuccess(() -> Component.literal("Cinemarr Plex validation started"), false); return 1; }));
        root.then(Commands.literal("cache").requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel())).executes(c -> { GlobalPlayer p = CinemarrServer.instance().player(); long bytes = p == null ? 0 : p.cacheSize(); c.getSource().sendSuccess(() -> Component.literal("Cinemarr cache: " + bytes / 1024 / 1024 + " MiB"), false); return 1; }));
        root.then(Commands.literal("diagnostics").requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel())).executes(c -> { GlobalPlayer p = CinemarrServer.instance().player(); c.getSource().sendSuccess(() -> Component.literal(p == null ? "Cinemarr is unavailable" : p.diagnostics()), false); return 1; }));
        root.then(Commands.literal("station").requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel()))
                .then(Commands.literal("status").executes(c -> { GlobalPlayer p = CinemarrServer.instance().player(); c.getSource().sendSuccess(() -> Component.literal(p == null ? "Cinemarr is unavailable" : p.stationStatus()), false); return 1; }))
                .then(Commands.literal("stop").executes(c -> station(c.getSource(), CinemarrPayloads.StationAction.STOP, CinemarrPayloads.StationType.NONE, false)))
                .then(Commands.literal("library-shuffle").executes(c -> station(c.getSource(), CinemarrPayloads.StationAction.START, CinemarrPayloads.StationType.LIBRARY_SHUFFLE, false))));
        root.then(Commands.literal("autoplay").requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel()))
                .then(Commands.literal("on").executes(c -> station(c.getSource(), CinemarrPayloads.StationAction.SET_AUTOPLAY, CinemarrPayloads.StationType.AUTOPLAY, true)))
                .then(Commands.literal("off").executes(c -> station(c.getSource(), CinemarrPayloads.StationAction.SET_AUTOPLAY, CinemarrPayloads.StationType.AUTOPLAY, false))));
        root.then(Commands.literal("adventure").requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel()))
                .then(Commands.literal("status").executes(c -> { GlobalPlayer p = CinemarrServer.instance().player(); c.getSource().sendSuccess(() -> Component.literal(p == null ? "Cinemarr is unavailable" : p.stationStatus()), false); return 1; }))
                .then(Commands.literal("stop").executes(c -> station(c.getSource(), CinemarrPayloads.StationAction.STOP, CinemarrPayloads.StationType.NONE, false))));
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> operator(String name, CinemarrPayloads.ControlAction action) {
        return Commands.literal(name).requires(s -> CinemarrPermissions.has(s, CinemarrSettings.operatorPermissionLevel())).executes(c -> {
            CinemarrServer.instance().control(c.getSource().getPlayerOrException(), new CinemarrPayloads.ControlRequest(action, -1)); return 1;
        });
    }

    private static int station(CommandSourceStack source, CinemarrPayloads.StationAction action, CinemarrPayloads.StationType type, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        GlobalPlayer player = CinemarrServer.instance().player();
        if (player == null) { source.sendFailure(Component.literal("Cinemarr is unavailable")); return 0; }
        player.station(source.getPlayerOrException(), new CinemarrPayloads.StationRequest(action, type, enabled, player.stationGeneration(), List.of()));
        return 1;
    }
}
