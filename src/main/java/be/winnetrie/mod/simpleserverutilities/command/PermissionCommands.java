package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionRank;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.PlayerPermissionData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.SharedSuggestionProvider;

public class PermissionCommands {

    private PermissionCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> PERMISSION_KEY_SUGGESTIONS =
        (context, builder) -> SharedSuggestionProvider.suggest(PermissionKeys.getKnownKeys(), builder);

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("permissions")
                .requires(source -> source.getEntity() instanceof ServerPlayer player
                        && PermissionService.has(player, PermissionKeys.PERMISSIONS_ADMIN))
                .executes(context -> help(context.getSource()))

                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())))

                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))

                .then(Commands.literal("save")
                        .executes(context -> save(context.getSource())))

                .then(Commands.literal("ranks")
                        .executes(context -> ranks(context.getSource())))
                
                .then(Commands.literal("keys")
                        .executes(context -> keys(context.getSource())))

                .then(Commands.literal("rank")
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .then(Commands.literal("info")
                                        .executes(context -> rankInfo(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "rank")
                                        )))
                                .then(Commands.literal("priority")
                                        .then(Commands.argument("priority", IntegerArgumentType.integer())
                                                .executes(context -> rankPriority(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rank"),
                                                        IntegerArgumentType.getInteger(context, "priority")
                                                ))))

                                .then(Commands.literal("inherit")
                                        .then(Commands.argument("parent", StringArgumentType.word())
                                                .executes(context -> rankInherit(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rank"),
                                                        StringArgumentType.getString(context, "parent")
                                                ))))

                                .then(Commands.literal("uninherit")
                                        .then(Commands.argument("parent", StringArgumentType.word())
                                                .executes(context -> rankUninherit(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rank"),
                                                        StringArgumentType.getString(context, "parent")
                                                ))))

                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                                .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> rankSet(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "rank"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .executes(context -> rankUnset(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "rank"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))

                .then(Commands.literal("player")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.literal("info")
                                        .executes(context -> playerInfo(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player")
                                        )))
                                .then(Commands.literal("addrank")
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .executes(context -> playerAddRank(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "rank")
                                                ))))
                                .then(Commands.literal("removerank")
                                        .then(Commands.argument("rank", StringArgumentType.word())
                                                .executes(context -> playerRemoveRank(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "rank")
                                                ))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> playerSet(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "player"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .executes(context -> playerUnset(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))

                .then(Commands.literal("dimension")
                        .then(Commands.argument("dimension", StringArgumentType.word())
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> dimensionSet(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "dimension"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .executes(context -> dimensionUnset(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "dimension"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))

                .then(Commands.literal("claimcontext")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .then(Commands.literal("set")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes(context -> claimContextSet(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "role"),
                                                                StringArgumentType.getString(context, "key"),
                                                                StringArgumentType.getString(context, "value")
                                                        )))))
                                .then(Commands.literal("unset")
                                        .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                                .executes(context -> claimContextUnset(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "role"),
                                                        StringArgumentType.getString(context, "key")
                                                ))))))

                .then(Commands.literal("check")
                        .then(Commands.argument("key", StringArgumentType.word())
                        .suggests(PERMISSION_KEY_SUGGESTIONS)
                                .executes(context -> check(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "key")
                                ))));
    }

    private static int reload(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        SimpleServerUtilities.PERMISSIONS.load(player.level().getServer());
        player.sendSystemMessage(Component.literal("Permissions reloaded."));
        return 1;
    }

    private static int save(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        SimpleServerUtilities.PERMISSIONS.save();
        player.sendSystemMessage(Component.literal("Permissions saved."));
        return 1;
    }

    private static int ranks(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        player.sendSystemMessage(Component.literal("Ranks: " + String.join(", ", SimpleServerUtilities.PERMISSIONS.getRankNames())));
        return 1;
    }

    private static int rankInfo(CommandSourceStack source, String rankName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PermissionRank rank = SimpleServerUtilities.PERMISSIONS.getRank(rankName);

        if (rank == null) {
            player.sendSystemMessage(Component.literal("Rank not found: " + rankName));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Rank: " + rankName));
        player.sendSystemMessage(Component.literal("Priority: " + rank.getPriority()));
        player.sendSystemMessage(Component.literal("Inherits: " + String.join(", ", rank.getInherits())));
        sendPermissions(player, rank.getPermissions());
        return 1;
    }

    private static int rankPriority(CommandSourceStack source, String rankName, int priority) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        SimpleServerUtilities.PERMISSIONS.setRankPriority(rankName, priority);
        player.sendSystemMessage(Component.literal("Priority for rank '" + rankName + "' set to " + priority + "."));
        return 1;
    }

    private static int rankSet(CommandSourceStack source, String rankName, String key, String value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        warnIfUnknownKey(player, key);
        SimpleServerUtilities.PERMISSIONS.setRankPermission(rankName, key, value);
        player.sendSystemMessage(Component.literal("Set " + key + " = " + value + " for rank '" + rankName + "'."));
        return 1;
    }

    private static int rankUnset(CommandSourceStack source, String rankName, String key) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        boolean existed = SimpleServerUtilities.PERMISSIONS.removeRankPermission(rankName, key);

        if (!existed) {
            player.sendSystemMessage(Component.literal("Permission was not set on rank '" + rankName + "': " + key));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Removed " + key + " from rank '" + rankName + "'."));
        return 1;
    }

    private static int playerInfo(CommandSourceStack source, String playerName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        PlayerPermissionData playerData = SimpleServerUtilities.PERMISSIONS.getPlayerData(targetUuid.get());

        executor.sendSystemMessage(Component.literal("Permissions for " + playerName + ":"));

        if (playerData == null) {
            executor.sendSystemMessage(Component.literal("Ranks: default"));
            executor.sendSystemMessage(Component.literal("No direct player permissions."));
            return 1;
        }

        executor.sendSystemMessage(Component.literal("Ranks: " + String.join(", ", playerData.getRanks())));
        sendPermissions(executor, playerData.getPermissions());
        return 1;
    }

    private static int playerAddRank(CommandSourceStack source, String playerName, String rankName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        SimpleServerUtilities.PERMISSIONS.addPlayerRank(targetUuid.get(), rankName);
        executor.sendSystemMessage(Component.literal("Added rank '" + rankName + "' to " + playerName + "."));
        return 1;
    }

    private static int playerRemoveRank(CommandSourceStack source, String playerName, String rankName) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        boolean existed = SimpleServerUtilities.PERMISSIONS.removePlayerRank(targetUuid.get(), rankName);

        if (!existed) {
            executor.sendSystemMessage(Component.literal(playerName + " did not have rank '" + rankName + "'."));
            return 0;
        }

        executor.sendSystemMessage(Component.literal("Removed rank '" + rankName + "' from " + playerName + "."));
        return 1;
    }

    private static int playerSet(CommandSourceStack source, String playerName, String key, String value) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        warnIfUnknownKey(executor, key);
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        SimpleServerUtilities.PERMISSIONS.setPlayerPermission(targetUuid.get(), key, value);
        executor.sendSystemMessage(Component.literal("Set " + key + " = " + value + " for " + playerName + "."));
        return 1;
    }

    private static int playerUnset(CommandSourceStack source, String playerName, String key) {
        ServerPlayer executor = (ServerPlayer) source.getEntity();
        Optional<UUID> targetUuid = findPlayerUuid(executor, playerName);

        if (targetUuid.isEmpty()) {
            executor.sendSystemMessage(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        boolean existed = SimpleServerUtilities.PERMISSIONS.removePlayerPermission(targetUuid.get(), key);

        if (!existed) {
            executor.sendSystemMessage(Component.literal("Permission was not set for " + playerName + ": " + key));
            return 0;
        }

        executor.sendSystemMessage(Component.literal("Removed " + key + " from " + playerName + "."));
        return 1;
    }

    private static int dimensionSet(CommandSourceStack source, String dimension, String key, String value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        warnIfUnknownKey(player, key);
        SimpleServerUtilities.PERMISSIONS.setDimensionPermission(dimension, key, value);
        player.sendSystemMessage(Component.literal("Set " + key + " = " + value + " for dimension " + dimension + "."));
        return 1;
    }

    private static int dimensionUnset(CommandSourceStack source, String dimension, String key) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        boolean existed = SimpleServerUtilities.PERMISSIONS.removeDimensionPermission(dimension, key);

        if (!existed) {
            player.sendSystemMessage(Component.literal("Permission was not set for dimension " + dimension + ": " + key));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Removed " + key + " from dimension " + dimension + "."));
        return 1;
    }

    private static int claimContextSet(CommandSourceStack source, String role, String key, String value) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        SimpleServerUtilities.PERMISSIONS.setPlayerClaimContextPermission(role, key, value);
        player.sendSystemMessage(Component.literal("Set " + key + " = " + value + " for player-claim role " + role + "."));
        return 1;
    }

    private static int claimContextUnset(CommandSourceStack source, String role, String key) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        boolean existed = SimpleServerUtilities.PERMISSIONS.removePlayerClaimContextPermission(role, key);

        if (!existed) {
            player.sendSystemMessage(Component.literal("Permission was not set for player-claim role " + role + ": " + key));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Removed " + key + " from player-claim role " + role + "."));
        return 1;
    }

    private static int check(CommandSourceStack source, String key) {
        ServerPlayer player = (ServerPlayer) source.getEntity();
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        boolean value = PermissionService.getBoolean(player, key, false, context);
        int intValue = PermissionService.getInt(player, key, -1, context);
        String stringValue = PermissionService.getString(player, key, "<not set>", context);

        player.sendSystemMessage(Component.literal("Permission check for: " + key));
        player.sendSystemMessage(Component.literal("Boolean value: " + value));
        player.sendSystemMessage(Component.literal("Integer value: " + intValue));
        player.sendSystemMessage(Component.literal("String value: " + stringValue));

        if (context.getRegion() != null) {
            player.sendSystemMessage(Component.literal("Effective region: " + context.getRegion().getName()));
        }

        player.sendSystemMessage(Component.literal("Dimension: " + context.getDimension()));
        player.sendSystemMessage(Component.literal("Claim role: " + context.getClaimRole()));
        return 1;
    }

    private static void sendPermissions(ServerPlayer player, Map<String, String> permissions) {
        if (permissions.isEmpty()) {
            player.sendSystemMessage(Component.literal("Permissions: none"));
            return;
        }

        player.sendSystemMessage(Component.literal("Permissions:"));

        permissions.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> player.sendSystemMessage(Component.literal(
                        " - " + entry.getKey() + " = " + entry.getValue()
                )));
    }

    private static Optional<UUID> findPlayerUuid(ServerPlayer player, String name) {
        PlayerList playerList = player.level().getServer().getPlayerList();

        ServerPlayer onlinePlayer = playerList.getPlayerByName(name);
        if (onlinePlayer != null) {
            return Optional.of(onlinePlayer.getUUID());
        }

        return Optional.empty();
    }

    private static int help(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        player.sendSystemMessage(Component.literal("Permission commands:"));
        player.sendSystemMessage(Component.literal(" - /permissions ranks"));
        player.sendSystemMessage(Component.literal(" - /permissions rank <rank> info"));
        player.sendSystemMessage(Component.literal(" - /permissions rank <rank> priority <number>"));
        player.sendSystemMessage(Component.literal(" - /permissions rank <rank> inherit <parent>"));
        player.sendSystemMessage(Component.literal(" - /permissions rank <rank> uninherit <parent>"));
        player.sendSystemMessage(Component.literal(" - /permissions rank <rank> set <key> <value>"));
        player.sendSystemMessage(Component.literal(" - /permissions rank <rank> unset <key>"));
        player.sendSystemMessage(Component.literal(" - /permissions player <player> info"));
        player.sendSystemMessage(Component.literal(" - /permissions player <player> addrank <rank>"));
        player.sendSystemMessage(Component.literal(" - /permissions player <player> removerank <rank>"));
        player.sendSystemMessage(Component.literal(" - /permissions player <player> set <key> <value>"));
        player.sendSystemMessage(Component.literal(" - /permissions player <player> unset <key>"));
        player.sendSystemMessage(Component.literal(" - /permissions dimension <dimension> set <key> <value>"));
        player.sendSystemMessage(Component.literal(" - /permissions dimension <dimension> unset <key>"));
        player.sendSystemMessage(Component.literal(" - /permissions claimcontext <owner|co_owner|member|visitor|none> set <key> <value>"));
        player.sendSystemMessage(Component.literal(" - /permissions claimcontext <owner|co_owner|member|visitor|none> unset <key>"));
        player.sendSystemMessage(Component.literal(" - /permissions check <key>"));
        player.sendSystemMessage(Component.literal(" - /permissions keys"));
        player.sendSystemMessage(Component.literal(" - /permissions reload"));
        player.sendSystemMessage(Component.literal(" - /permissions save"));
        return 1;
    }

    private static int rankInherit(CommandSourceStack source, String rankName, String parentRankName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        boolean success = SimpleServerUtilities.PERMISSIONS.addRankInheritance(rankName, parentRankName);

        if (!success) {
            player.sendSystemMessage(Component.literal(
                    "Could not make rank '" + rankName + "' inherit from '" + parentRankName + "'. This may already exist or would create a cycle."
            ));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "Rank '" + rankName + "' now inherits from '" + parentRankName + "'."
        ));
        return 1;
    }

    private static int rankUninherit(CommandSourceStack source, String rankName, String parentRankName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        boolean success = SimpleServerUtilities.PERMISSIONS.removeRankInheritance(rankName, parentRankName);

        if (!success) {
            player.sendSystemMessage(Component.literal(
                    "Rank '" + rankName + "' did not inherit from '" + parentRankName + "'."
            ));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "Rank '" + rankName + "' no longer inherits from '" + parentRankName + "'."
        ));
        return 1;
    }

    private static void warnIfUnknownKey(ServerPlayer player, String key) {
        if (PermissionKeys.isKnownKey(key)) {
            return;
        }

        player.sendSystemMessage(Component.literal(
                "Warning: unknown permission key '" + key + "'. It will be saved, but it may not affect anything."
        ));
    }

    private static int keys(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        player.sendSystemMessage(Component.literal("Known permission keys:"));

        for (String key : PermissionKeys.getKnownKeys()) {
            player.sendSystemMessage(Component.literal(" - " + key));
        }

        return 1;
    }
}
