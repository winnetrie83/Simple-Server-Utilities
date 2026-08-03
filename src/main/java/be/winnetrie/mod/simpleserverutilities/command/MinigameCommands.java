package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameEditorService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player and administrator commands for the independent Minigame Framework. */
public final class MinigameCommands {
    private MinigameCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("minigame")
                .then(Commands.literal("open").executes(context -> open(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("join")
                        .then(Commands.argument("minigame", StringArgumentType.word())
                                .executes(context -> join(context.getSource(), StringArgumentType.getString(context, "minigame")))))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
                .then(Commands.literal("edit")
                        .executes(context -> edit(context.getSource(), ""))
                        .then(Commands.argument("minigame", StringArgumentType.word())
                                .executes(context -> edit(context.getSource(), StringArgumentType.getString(context, "minigame")))))
                .then(Commands.literal("force-start")
                        .then(Commands.argument("minigame", StringArgumentType.word())
                                .executes(context -> forceStart(context.getSource(), StringArgumentType.getString(context, "minigame")))))
                .then(Commands.literal("finish").executes(context -> finish(context.getSource())))
                .then(Commands.literal("release-arena")
                        .then(Commands.argument("minigame_or_arena", StringArgumentType.word())
                                .executes(context -> releaseArena(context.getSource(), StringArgumentType.getString(context, "minigame_or_arena")))))
                .then(Commands.literal("score")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> score(context.getSource(), StringArgumentType.getString(context, "player"), IntegerArgumentType.getInteger(context, "amount"), false)))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> score(context.getSource(), StringArgumentType.getString(context, "player"), IntegerArgumentType.getInteger(context, "amount"), true))))));
    }

    private static int open(CommandSourceStack source) {
        ServerPlayer player = player(source); if (player == null) return 0;
        SimpleServerUtilities.MINIGAMES.open(player); return 1;
    }

    private static int join(CommandSourceStack source, String id) {
        ServerPlayer player = player(source); if (player == null) return 0;
        try { player.sendSystemMessage(Component.literal(SimpleServerUtilities.MINIGAMES.joinQueue(player, id))); return 1; }
        catch (RuntimeException exception) { source.sendFailure(Component.literal(message(exception))); return 0; }
    }

    private static int leave(CommandSourceStack source) {
        ServerPlayer player = player(source); if (player == null) return 0;
        player.sendSystemMessage(Component.literal(SimpleServerUtilities.MINIGAMES.leave(player, true))); return 1;
    }

    private static int status(CommandSourceStack source) {
        if (!canAdmin(source)) { source.sendFailure(Component.literal("Minigame administrator permission is required.")); return 0; }
        var snapshot = SimpleServerUtilities.MINIGAMES.snapshot();
        source.sendSystemMessage(Component.literal("SSU Minigames: definitions=" + snapshot.definitions()
                + ", queued=" + snapshot.queuedPlayers() + ", matches=" + snapshot.matches()
                + ", reserved=" + snapshot.reservedArenas() + ", resetting=" + snapshot.resettingArenas()
                + ", blocked=" + snapshot.blockedArenas() + ", recoveries=" + snapshot.pendingRecoveries()));
        return 1;
    }

    private static int edit(CommandSourceStack source, String id) {
        ServerPlayer player = player(source); if (player == null) return 0;
        if (!PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false)) {
            source.sendFailure(Component.literal("Minigame administrator permission is required.")); return 0;
        }
        MinigameEditorService.open(player, id); return 1;
    }

    private static int forceStart(CommandSourceStack source, String id) {
        if (!canAdmin(source)) { source.sendFailure(Component.literal("Minigame administrator permission is required.")); return 0; }
        try { source.sendSystemMessage(Component.literal(SimpleServerUtilities.MINIGAMES.forceStart(id))); return 1; }
        catch (RuntimeException exception) { source.sendFailure(Component.literal(message(exception))); return 0; }
    }

    private static int finish(CommandSourceStack source) {
        ServerPlayer player = player(source); if (player == null) return 0;
        if (!PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false)) {
            source.sendFailure(Component.literal("Minigame administrator permission is required.")); return 0;
        }
        try { source.sendSystemMessage(Component.literal(SimpleServerUtilities.MINIGAMES.finishPlayerMatch(player, "Finished by an administrator."))); return 1; }
        catch (RuntimeException exception) { source.sendFailure(Component.literal(message(exception))); return 0; }
    }

    private static int releaseArena(CommandSourceStack source, String target) {
        if (!canAdmin(source)) { source.sendFailure(Component.literal("Minigame administrator permission is required.")); return 0; }
        source.sendSystemMessage(Component.literal(SimpleServerUtilities.MINIGAMES.releaseBlockedArena(target)));
        return 1;
    }

    private static int score(CommandSourceStack source, String playerName, int amount, boolean set) {
        if (!canAdmin(source)) { source.sendFailure(Component.literal("Minigame administrator permission is required.")); return 0; }
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) { source.sendFailure(Component.literal("That player is not online.")); return 0; }
        try {
            if (set) SimpleServerUtilities.MINIGAMES.setScore(target.getUUID(), amount);
            else SimpleServerUtilities.MINIGAMES.addScore(target.getUUID(), amount);
            source.sendSystemMessage(Component.literal((set ? "Set" : "Changed") + " minigame score for " + target.getName().getString() + "."));
            return 1;
        } catch (RuntimeException exception) { source.sendFailure(Component.literal(message(exception))); return 0; }
    }

    private static ServerPlayer player(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        source.sendFailure(Component.literal("This command can only be used by a player.")); return null;
    }

    private static boolean canAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player)
                || PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false);
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? "The minigame operation failed safely." : exception.getMessage();
    }
}
