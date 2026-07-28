package be.winnetrie.mod.simpleserverutilities.command;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyAccount;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionRecord;
import be.winnetrie.mod.simpleserverutilities.economy.MoneyFormat;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EconomyCommands {

    private EconomyCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildPlayerRoot() {
        return Commands.literal("economy")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> balance(context.getSource()))
                .then(Commands.literal("balance")
                        .executes(context -> balance(context.getSource())))
                .then(Commands.literal("history")
                        .executes(context -> historySelf(context.getSource(), 10))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                .executes(context -> historySelf(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "limit")
                                ))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildBalanceAlias() {
        return Commands.literal("balance")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> balance(context.getSource()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildPayAlias() {
        return Commands.literal("pay")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(context -> pay(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "amount")
                                ))));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildAdmin() {
        return Commands.literal("economy")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("balance")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> adminBalance(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")
                                ))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> adminGive(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("take")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> adminTake(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(context -> adminSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "amount")
                                        )))))
                .then(Commands.literal("history")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(context -> adminHistory(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        10
                                ))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> adminHistory(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                IntegerArgumentType.getInteger(context, "limit")
                                        )))));
    }

    private static int balance(CommandSourceStack source) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        if (!PermissionService.getBoolean(player, PermissionKeys.ECONOMY_USE, true)
                || !PermissionService.getBoolean(player, PermissionKeys.ECONOMY_BALANCE, true)) {
            player.sendSystemMessage(Component.literal("You do not have permission to view your balance."));
            return 0;
        }
        if (!SimpleServerUtilities.ECONOMY.isEnabled()) {
            player.sendSystemMessage(Component.literal("The economy module is disabled."));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "Balance: " + SimpleServerUtilities.ECONOMY.formattedBalance(player)
        ));
        return 1;
    }

    private static int pay(CommandSourceStack source, String targetName, String rawAmount) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        if (!PermissionService.getBoolean(player, PermissionKeys.ECONOMY_USE, true)
                || !PermissionService.getBoolean(player, PermissionKeys.ECONOMY_PAY, true)) {
            player.sendSystemMessage(Component.literal("You do not have permission to pay other players."));
            return 0;
        }

        long amount = parseAmount(source, rawAmount);
        if (amount < 0L) {
            return 0;
        }

        EconomyAccount sourceAccount = SimpleServerUtilities.ECONOMY.ensureAccount(player);
        EconomyAccount target = SimpleServerUtilities.ECONOMY
                .findAccountByName(source.getServer(), targetName)
                .orElse(null);
        if (target == null) {
            player.sendSystemMessage(Component.literal(
                    "No known economy account was found for '" + targetName + "'. The player must access an economy feature at least once."
            ));
            return 0;
        }

        EconomyResult result = SimpleServerUtilities.ECONOMY.transfer(
                player,
                sourceAccount,
                target,
                amount,
                "Player payment to " + target.getLastKnownName(),
                ""
        );
        if (!result.successful()) {
            player.sendSystemMessage(Component.literal("Payment failed: " + result.message()));
            return 0;
        }

        String formatted = MoneyFormat.format(amount, SimpleServerUtilities.ECONOMY.settings());
        player.sendSystemMessage(Component.literal(
                "Paid " + formatted + " to " + target.getLastKnownName() + ". New balance: "
                        + MoneyFormat.format(result.sourceBalanceMinor(), SimpleServerUtilities.ECONOMY.settings())
        ));

        ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(target.getPlayerId());
        if (onlineTarget != null) {
            onlineTarget.sendSystemMessage(Component.literal(
                    "Received " + formatted + " from " + player.getName().getString() + ". New balance: "
                            + MoneyFormat.format(result.destinationBalanceMinor(), SimpleServerUtilities.ECONOMY.settings())
            ));
        }
        return 1;
    }

    private static int historySelf(CommandSourceStack source, int limit) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        if (!PermissionService.getBoolean(player, PermissionKeys.ECONOMY_USE, true)
                || !PermissionService.getBoolean(player, PermissionKeys.ECONOMY_HISTORY, true)) {
            player.sendSystemMessage(Component.literal("You do not have permission to view transaction history."));
            return 0;
        }
        return sendHistory(source, player.getUUID(), player.getName().getString(), limit);
    }

    private static int status(CommandSourceStack source) {
        if (!canAdmin(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage the economy."));
            return 0;
        }

        var settings = SimpleServerUtilities.ECONOMY.settings();
        var statistics = SimpleServerUtilities.ECONOMY.statistics();
        source.sendSystemMessage(Component.literal("SSU Economy status:"));
        source.sendSystemMessage(Component.literal(" - Enabled: " + settings.isEnabled()));
        source.sendSystemMessage(Component.literal(" - Currency: " + settings.getCurrencyName()
                + " (" + settings.getCurrencySymbol() + ")"));
        source.sendSystemMessage(Component.literal(" - Accounts: " + statistics.accounts()));
        source.sendSystemMessage(Component.literal(" - Total supply: "
                + MoneyFormat.format(statistics.totalSupplyMinor(), settings)));
        source.sendSystemMessage(Component.literal(" - Transactions loaded: " + statistics.loadedTransactions()
                + " (prepared: " + statistics.preparedTransactions() + ")"));
        return 1;
    }

    private static int adminBalance(CommandSourceStack source, String targetName) {
        if (!canAdmin(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage the economy."));
            return 0;
        }
        EconomyAccount account = resolveTarget(source, targetName);
        if (account == null) {
            return 0;
        }
        source.sendSystemMessage(Component.literal(
                account.getLastKnownName() + " balance: "
                        + MoneyFormat.format(account.getBalanceMinor(), SimpleServerUtilities.ECONOMY.settings())
        ));
        return 1;
    }

    private static int adminGive(CommandSourceStack source, String targetName, String rawAmount) {
        if (!canAdmin(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage the economy."));
            return 0;
        }
        EconomyAccount target = resolveTarget(source, targetName);
        if (target == null) {
            return 0;
        }
        long amount = parseAmount(source, rawAmount);
        if (amount < 0L) {
            return 0;
        }
        EconomyResult result = SimpleServerUtilities.ECONOMY.give(
                actorId(source), actorName(source), target, amount, "Admin give"
        );
        return reportAdminMutation(source, target, result, "gave", amount);
    }

    private static int adminTake(CommandSourceStack source, String targetName, String rawAmount) {
        if (!canAdmin(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage the economy."));
            return 0;
        }
        EconomyAccount target = resolveTarget(source, targetName);
        if (target == null) {
            return 0;
        }
        long amount = parseAmount(source, rawAmount);
        if (amount < 0L) {
            return 0;
        }
        EconomyResult result = SimpleServerUtilities.ECONOMY.take(
                actorId(source), actorName(source), target, amount, "Admin take"
        );
        return reportAdminMutation(source, target, result, "took", amount);
    }

    private static int adminSet(CommandSourceStack source, String targetName, String rawAmount) {
        if (!canAdmin(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage the economy."));
            return 0;
        }
        EconomyAccount target = resolveTarget(source, targetName);
        if (target == null) {
            return 0;
        }
        long amount = parseAmount(source, rawAmount);
        if (amount < 0L) {
            return 0;
        }
        EconomyResult result = SimpleServerUtilities.ECONOMY.setBalance(
                actorId(source), actorName(source), target, amount, "Admin set"
        );
        if (!result.successful()) {
            source.sendFailure(Component.literal("Balance change failed: " + result.message()));
            return 0;
        }
        source.sendSystemMessage(Component.literal(
                "Set " + target.getLastKnownName() + " balance to "
                        + MoneyFormat.format(target.getBalanceMinor(), SimpleServerUtilities.ECONOMY.settings()) + "."
        ));
        return 1;
    }

    private static int adminHistory(CommandSourceStack source, String targetName, int limit) {
        if (!canAdmin(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage the economy."));
            return 0;
        }
        EconomyAccount target = resolveTarget(source, targetName);
        if (target == null) {
            return 0;
        }
        return sendHistory(source, target.getPlayerId(), target.getLastKnownName(), limit);
    }

    private static int sendHistory(CommandSourceStack source, UUID playerId, String displayName, int limit) {
        List<EconomyTransactionRecord> history = SimpleServerUtilities.ECONOMY.history(playerId, limit);
        source.sendSystemMessage(Component.literal("Recent economy transactions for " + displayName + ":"));
        if (history.isEmpty()) {
            source.sendSystemMessage(Component.literal(" - No transactions recorded."));
            return 1;
        }

        for (EconomyTransactionRecord record : history) {
            String direction;
            if (playerId.equals(record.getSourceId()) && playerId.equals(record.getDestinationId())) {
                direction = "set";
            } else if (playerId.equals(record.getSourceId())) {
                direction = "out";
            } else if (playerId.equals(record.getDestinationId())) {
                direction = "in";
            } else {
                direction = "admin";
            }
            source.sendSystemMessage(Component.literal(
                    " - [" + direction + "] " + record.getType().name().toLowerCase(java.util.Locale.ROOT)
                            + " " + MoneyFormat.format(record.getAmountMinor(), SimpleServerUtilities.ECONOMY.settings())
                            + " | " + record.getStatus().name().toLowerCase(java.util.Locale.ROOT)
                            + (record.getReason().isBlank() ? "" : " | " + record.getReason())
            ));
        }
        return 1;
    }

    private static int reportAdminMutation(
            CommandSourceStack source,
            EconomyAccount target,
            EconomyResult result,
            String verb,
            long amount
    ) {
        if (!result.successful()) {
            source.sendFailure(Component.literal("Balance change failed: " + result.message()));
            return 0;
        }
        source.sendSystemMessage(Component.literal(
                "Successfully " + verb + " "
                        + MoneyFormat.format(amount, SimpleServerUtilities.ECONOMY.settings())
                        + " for " + target.getLastKnownName() + ". New balance: "
                        + MoneyFormat.format(target.getBalanceMinor(), SimpleServerUtilities.ECONOMY.settings())
        ));
        return 1;
    }

    private static EconomyAccount resolveTarget(CommandSourceStack source, String name) {
        EconomyAccount account = SimpleServerUtilities.ECONOMY
                .findAccountByName(source.getServer(), name)
                .orElse(null);
        if (account == null) {
            source.sendFailure(Component.literal(
                    "No known economy account was found for '" + name + "'. The player must access an economy feature at least once."
            ));
        }
        return account;
    }

    private static long parseAmount(CommandSourceStack source, String rawAmount) {
        try {
            return MoneyFormat.parseMinor(rawAmount, SimpleServerUtilities.ECONOMY.settings());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return -1L;
        }
    }

    private static boolean canAdmin(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return PermissionService.getBoolean(player, PermissionKeys.ECONOMY_ADMIN, false);
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.literal("This command can only be used by a player."));
        return null;
    }

    private static UUID actorId(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
    }

    private static String actorName(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getName().getString() : "console";
    }
}
