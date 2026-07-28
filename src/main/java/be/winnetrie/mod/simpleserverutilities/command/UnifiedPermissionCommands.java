package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionRank;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.PlayerPermissionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single public command surface for rank and personal permissions.
 *
 * Rank permissions are the base. Personal permissions always override them.
 */
public final class UnifiedPermissionCommands {

    private static final SuggestionProvider<CommandSourceStack> PERMISSION_KEYS =
            (context, builder) -> SharedSuggestionProvider.suggest(PermissionKeys.getKnownKeys(), builder);

    private static final SuggestionProvider<CommandSourceStack> RANKS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    SimpleServerUtilities.PERMISSIONS.getRankNames(), builder);

    private UnifiedPermissionCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildRank() {
        return Commands.literal("rank")
                .requires(UnifiedPermissionCommands::canAdmin)
                .executes(context -> rankHelp(context.getSource()))
                .then(Commands.literal("list")
                        .executes(context -> listRanks(context.getSource())))
                .then(Commands.literal("create")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .executes(context -> createRank(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "rank")
                                ))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(RANKS)
                                .executes(context -> deleteRank(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "rank")
                                ))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("old", StringArgumentType.word())
                                .suggests(RANKS)
                                .then(Commands.argument("new", StringArgumentType.word())
                                        .executes(context -> renameRank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "old"),
                                                StringArgumentType.getString(context, "new")
                                        )))))
                .then(Commands.literal("setdefault")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(RANKS)
                                .executes(context -> setDefaultRank(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "rank")
                                ))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests(RANKS)
                                        .executes(context -> assignRank(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "rank")
                                        )))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> assignRank(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        SimpleServerUtilities.PERMISSIONS.getDefaultRankName()
                                ))))
                .then(Commands.literal("info")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(RANKS)
                                .executes(context -> rankInfo(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "rank")
                                ))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildPermission() {
        return Commands.literal("perm")
                .requires(UnifiedPermissionCommands::canAdmin)
                .executes(context -> permissionHelp(context.getSource()))
                .then(Commands.literal("rank")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests(RANKS)
                                .then(Commands.literal("list")
                                        .executes(context -> rankInfo(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "rank")
                                        )))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(PERMISSION_KEYS)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> setRankPermission(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "rank"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(PERMISSION_KEYS)
                                                .executes(context -> unsetRankPermission(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rank"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.literal("list")
                                        .executes(context -> playerInfo(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player")
                                        )))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(PERMISSION_KEYS)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> setPlayerPermission(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "player"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(PERMISSION_KEYS)
                                                .executes(context -> unsetPlayerPermission(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEYS)
                                        .executes(context -> check(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "key")
                                        )))));
    }

    private static boolean canAdmin(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return PermissionService.has(player, PermissionKeys.PERMISSIONS_ADMIN);
    }

    private static int rankHelp(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("Rank commands:"));
        source.sendSystemMessage(Component.literal(" /ssu rank list"));
        source.sendSystemMessage(Component.literal(" /ssu rank create|delete|info <rank>"));
        source.sendSystemMessage(Component.literal(" /ssu rank rename <old> <new>"));
        source.sendSystemMessage(Component.literal(" /ssu rank setdefault <rank>"));
        source.sendSystemMessage(Component.literal(" /ssu rank assign <player> <rank>"));
        source.sendSystemMessage(Component.literal(" /ssu rank reset <player>"));
        return 1;
    }

    private static int permissionHelp(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("Permission commands:"));
        source.sendSystemMessage(Component.literal(" /ssu perm rank <rank> list|set|unset ..."));
        source.sendSystemMessage(Component.literal(" /ssu perm player <player> list|set|unset ..."));
        source.sendSystemMessage(Component.literal(" /ssu perm check <online-player> <key>"));
        source.sendSystemMessage(Component.literal("Personal values always override rank values."));
        return 1;
    }

    private static int listRanks(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal(
                "Ranks: " + String.join(", ", SimpleServerUtilities.PERMISSIONS.getRankNames())
        ));
        source.sendSystemMessage(Component.literal(
                "Default rank: " + SimpleServerUtilities.PERMISSIONS.getDefaultRankName()
        ));
        return 1;
    }

    private static int createRank(CommandSourceStack source, String rankName) {
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) != null) {
            source.sendFailure(Component.literal("Rank already exists: " + rankName));
            return 0;
        }
        SimpleServerUtilities.PERMISSIONS.getOrCreateRank(rankName);
        SimpleServerUtilities.PERMISSIONS.save();
        source.sendSystemMessage(Component.literal("Created rank '" + rankName + "'."));
        return 1;
    }

    private static int deleteRank(CommandSourceStack source, String rankName) {
        if (!SimpleServerUtilities.PERMISSIONS.deleteRank(rankName)) {
            source.sendFailure(Component.literal(
                    "Could not delete rank. The default/admin rank cannot be deleted, or the rank does not exist."
            ));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Deleted rank '" + rankName + "'."));
        return 1;
    }

    private static int renameRank(CommandSourceStack source, String oldName, String newName) {
        if (!SimpleServerUtilities.PERMISSIONS.renameRank(oldName, newName)) {
            source.sendFailure(Component.literal("Could not rename rank. Check whether both names are valid and unique."));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Renamed rank '" + oldName + "' to '" + newName + "'."));
        return 1;
    }

    private static int setDefaultRank(CommandSourceStack source, String rankName) {
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null) {
            source.sendFailure(Component.literal("Rank not found: " + rankName));
            return 0;
        }
        SimpleServerUtilities.PERMISSIONS.setDefaultRankName(rankName);
        source.sendSystemMessage(Component.literal("Default rank is now '" + rankName + "'."));
        return 1;
    }

    private static int assignRank(CommandSourceStack source, String playerName, String rankName) {
        UUID playerId = findPlayerId(source, playerName);
        if (playerId == null) {
            source.sendFailure(Component.literal("Unknown player: " + playerName));
            return 0;
        }
        if (SimpleServerUtilities.PERMISSIONS.getRank(rankName) == null) {
            source.sendFailure(Component.literal("Rank not found: " + rankName));
            return 0;
        }
        SimpleServerUtilities.PERMISSIONS.assignPlayerRank(playerId, rankName);
        source.sendSystemMessage(Component.literal("Assigned rank '" + rankName + "' to " + playerName + "."));
        return 1;
    }

    private static int rankInfo(CommandSourceStack source, String rankName) {
        PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(rankName);
        if (rank == null) {
            source.sendFailure(Component.literal("Rank not found: " + rankName));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Rank: " + rankName));
        source.sendSystemMessage(Component.literal("Priority: " + rank.getPriority()));
        source.sendSystemMessage(Component.literal("Inherits: " + String.join(", ", rank.getInherits())));
        sendPermissions(source, rank.getPermissions());
        return 1;
    }

    private static int setRankPermission(CommandSourceStack source, String rankName, String key, String value) {
        SimpleServerUtilities.PERMISSIONS.setRankPermission(rankName, key, value);
        source.sendSystemMessage(Component.literal("Rank permission set: " + key + " = " + value));
        return 1;
    }

    private static int unsetRankPermission(CommandSourceStack source, String rankName, String key) {
        if (!SimpleServerUtilities.PERMISSIONS.removeRankPermission(rankName, key)) {
            source.sendFailure(Component.literal("Permission was not directly set on rank: " + key));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Rank permission removed: " + key));
        return 1;
    }

    private static int playerInfo(CommandSourceStack source, String playerName) {
        UUID playerId = findPlayerId(source, playerName);
        if (playerId == null) {
            source.sendFailure(Component.literal("Unknown player: " + playerName));
            return 0;
        }
        PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(playerId);
        if (data == null) {
            source.sendSystemMessage(Component.literal("Rank: " + SimpleServerUtilities.PERMISSIONS.getDefaultRankName()));
            source.sendSystemMessage(Component.literal("Personal permissions: none"));
            return 1;
        }
        source.sendSystemMessage(Component.literal("Player: " + data.getLastKnownName() + " (" + playerId + ")"));
        source.sendSystemMessage(Component.literal("Rank(s): " + String.join(", ", data.getRanks())));
        source.sendSystemMessage(Component.literal("Personal permissions (highest priority):"));
        sendPermissions(source, data.getPermissions());
        return 1;
    }

    private static int setPlayerPermission(CommandSourceStack source, String playerName, String key, String value) {
        UUID playerId = findPlayerId(source, playerName);
        if (playerId == null) {
            source.sendFailure(Component.literal("Unknown player: " + playerName));
            return 0;
        }
        SimpleServerUtilities.PERMISSIONS.setPlayerPermission(playerId, key, value);
        source.sendSystemMessage(Component.literal("Personal permission set: " + key + " = " + value));
        return 1;
    }

    private static int unsetPlayerPermission(CommandSourceStack source, String playerName, String key) {
        UUID playerId = findPlayerId(source, playerName);
        if (playerId == null) {
            source.sendFailure(Component.literal("Unknown player: " + playerName));
            return 0;
        }
        if (!SimpleServerUtilities.PERMISSIONS.removePlayerPermission(playerId, key)) {
            source.sendFailure(Component.literal("Personal permission was not set: " + key));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Personal permission removed: " + key));
        return 1;
    }

    private static int check(CommandSourceStack source, String playerName, String key) {
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Permission checks require an online player: " + playerName));
            return 0;
        }
        PermissionContext context = PermissionContext.at(target, target.blockPosition());
        String personal = SimpleServerUtilities.PERMISSIONS.resolvePersonalValue(target, key);
        String effective = SimpleServerUtilities.PERMISSIONS.resolveValue(target, key, context);
        source.sendSystemMessage(Component.literal("Permission check for " + target.getName().getString() + ":"));
        source.sendSystemMessage(Component.literal(" Key: " + key));
        source.sendSystemMessage(Component.literal(" Personal override: " + (personal == null ? "<none>" : personal)));
        source.sendSystemMessage(Component.literal(" Effective value: " + (effective == null ? "<unset>" : effective)));
        return 1;
    }

    private static UUID findPlayerId(CommandSourceStack source, String playerName) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            SimpleServerUtilities.PERMISSIONS.ensurePlayerProfile(online);
            return online.getUUID();
        }
        return SimpleServerUtilities.PERMISSIONS.findKnownPlayerId(playerName);
    }

    private static void sendPermissions(CommandSourceStack source, Map<String, String> permissions) {
        if (permissions.isEmpty()) {
            source.sendSystemMessage(Component.literal(" Permissions: none"));
            return;
        }
        permissions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> source.sendSystemMessage(Component.literal(
                        " - " + entry.getKey() + " = " + entry.getValue()
                )));
    }
}
