package stonytark.cinemarr.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import stonytark.cinemarr.core.platform.CinemarrSettings;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import stonytark.cinemarr.screen.CinemarrWorldScreens;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Owner/operator recovery commands shared by every modern loader. */
public final class CinemarrTvCommands {
    private static final int PAGE_SIZE = 8;

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("tv")
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), own(context.getSource()), "Your", 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(), own(context.getSource()), "Your",
                                        IntegerArgumentType.getInteger(context, "page"))))
                        .then(Commands.literal("all").requires(CinemarrTvCommands::operator)
                                .executes(context -> list(context.getSource(), null, "All", 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> list(context.getSource(), null, "All",
                                                IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("owner").requires(CinemarrTvCommands::operator)
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .executes(context -> listOwner(context.getSource(), StringArgumentType.getString(context, "owner"), 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(context -> listOwner(context.getSource(), StringArgumentType.getString(context, "owner"),
                                                        IntegerArgumentType.getInteger(context, "page")))))))
                .then(Commands.literal("locate")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> locate(context.getSource(), UuidArgument.getUuid(context, "id")))))
                .then(Commands.literal("prune").requires(CinemarrTvCommands::operator)
                        .executes(context -> prune(context.getSource())))
                .then(Commands.literal("unregister")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(context -> unregister(context.getSource(), UuidArgument.getUuid(context, "id")))));
    }

    private static int listOwner(CommandSourceStack source, String requestedOwner, int page) {
        UUID owner;
        String label;
        try {
            owner = UUID.fromString(requestedOwner);
            label = requestedOwner + "'s";
        } catch (IllegalArgumentException notUuid) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(requestedOwner);
            if (player == null) {
                source.sendFailure(Component.literal("Owner must be an online player name or UUID"));
                return 0;
            }
            owner = player.getUUID();
            label = player.getName().getString() + "'s";
        }
        return list(source, owner, label, page);
    }

    private static int list(CommandSourceStack source, UUID owner, String label, int page) {
        if (owner == null && !operator(source) && !(source.getEntity() instanceof ServerPlayer)) {
            source.sendFailure(Component.literal("A player must run this command, or use 'tv list all'"));
            return 0;
        }
        List<TelevisionLifecycle.Registration> values = new ArrayList<>();
        for (TelevisionLifecycle.Registration value : TelevisionLifecycle.registrations()) {
            if (owner == null || owner.equals(value.owner())) values.add(value);
        }
        values.sort(Comparator.comparing(TelevisionLifecycle.Registration::dimension)
                .thenComparingInt(TelevisionLifecycle.Registration::controllerX)
                .thenComparingInt(TelevisionLifecycle.Registration::controllerY)
                .thenComparingInt(TelevisionLifecycle.Registration::controllerZ)
                .thenComparing(value -> value.id().toString()));
        int pages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            source.sendFailure(Component.literal("Page " + page + " does not exist; last page is " + pages));
            return 0;
        }
        int first = (page - 1) * PAGE_SIZE;
        int end = Math.min(values.size(), first + PAGE_SIZE);
        source.sendSuccess(() -> Component.literal(label + " registered TVs: " + values.size()
                + " (page " + page + "/" + pages + ")"), false);
        for (int index = first; index < end; index++) {
            String description = describe(values.get(index));
            source.sendSuccess(() -> Component.literal(description), false);
        }
        return values.size();
    }

    private static int locate(CommandSourceStack source, UUID id) {
        TelevisionLifecycle.Registration value = TelevisionLifecycle.registration(id);
        if (value == null || !authorized(source, value)) {
            source.sendFailure(Component.literal("TV not found or not owned by you"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describe(value)), false);
        return 1;
    }

    private static int unregister(CommandSourceStack source, UUID id) {
        TelevisionLifecycle.Registration value = TelevisionLifecycle.registration(id);
        if (value == null || !authorized(source, value)) {
            source.sendFailure(Component.literal("TV not found or not owned by you"));
            return 0;
        }
        if (!TelevisionLifecycle.unregister(id)) {
            source.sendFailure(Component.literal("TV was already unregistered"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Unregistered TV " + id + "; its blocks were left intact"), true);
        return 1;
    }

    private static int prune(CommandSourceStack source) {
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) removed += CinemarrWorldScreens.get(level).pruneInvalid();
        final int count = removed;
        source.sendSuccess(() -> Component.literal("Pruned " + count
                + " invalid loaded TV registration(s); unloaded registrations were left intact"), true);
        return removed;
    }

    private static UUID own(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer ? ((ServerPlayer) source.getEntity()).getUUID() : null;
    }

    private static boolean operator(CommandSourceStack source) {
        return CinemarrPermissions.has(source, CinemarrSettings.operatorPermissionLevel());
    }

    private static boolean authorized(CommandSourceStack source, TelevisionLifecycle.Registration value) {
        return operator(source) || (source.getEntity() instanceof ServerPlayer
                && ((ServerPlayer) source.getEntity()).getUUID().equals(value.owner()));
    }

    private static String describe(TelevisionLifecycle.Registration value) {
        return value.id() + " dimension=" + value.dimension()
                + " controller=" + value.controllerX() + "," + value.controllerY() + "," + value.controllerZ()
                + " owner=" + value.owner() + " pixels=" + value.pixelCount()
                + " session=" + (value.sessionName().isEmpty() ? "idle" : value.sessionName())
                + " validity=" + value.validation().name().toLowerCase(java.util.Locale.ROOT)
                + " playback=" + (value.attached() ? "attached" : "detached");
    }

    private CinemarrTvCommands() {}
}
