package be.winnetrie.mod.simpleserverutilities.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.transaction.SsuTransactionManager;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionCatalog;
import be.winnetrie.mod.simpleserverutilities.permission.PlayerPermissionData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Extensible action registry. Every list is validated first and then committed atomically
 * through the shared reversible transaction coordinator.
 */
public final class ContentActionEngine {
    private final Map<String, ContentActionHandler> handlers = new LinkedHashMap<>();
    private final ContentProgressionManager progression;
    private final SsuTransactionManager transactions;

    public ContentActionEngine(ContentProgressionManager progression, SsuTransactionManager transactions) {
        this.progression = Objects.requireNonNull(progression, "progression");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        registerBuiltIns();
    }

    public synchronized void register(String rawType, ContentActionHandler handler) {
        String type = ContentId.require(rawType, "Action type");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("Duplicate content action handler: " + type);
        }
    }

    public synchronized boolean isRegistered(String rawType) {
        return handlers.containsKey(ContentId.normalize(rawType));
    }

    public synchronized int registeredTypeCount() {
        return handlers.size();
    }

    /** Stable insertion-order snapshot for administrator editors. */
    public synchronized java.util.List<String> registeredTypes() {
        return java.util.List.copyOf(handlers.keySet());
    }

    public ExecutionResult execute(List<ContentAction> rawActions, ContentActionContext context) {
        if (rawActions == null || rawActions.isEmpty()) return ExecutionResult.successWithoutTransaction();
        ArrayList<PreparedContentAction> prepared = new ArrayList<>(rawActions.size());
        try {
            if (rawActions.size() > 256) throw new IllegalArgumentException("At most 256 actions may execute together.");
            for (int actionIndex = 0; actionIndex < rawActions.size(); actionIndex++) {
                ContentAction rawAction = rawActions.get(actionIndex);
                if (rawAction == null) throw new IllegalArgumentException("Action list contains an empty entry.");
                ContentAction action = rawAction.normalize();
                ContentActionHandler handler;
                synchronized (this) {
                    handler = handlers.get(action.type());
                }
                if (handler == null) throw new IllegalArgumentException("Unknown action type: " + action.type());
                PreparedContentAction step = handler.prepare(action, actionContext(context, actionIndex), progression);
                if (step == null) throw new IllegalArgumentException("Action handler returned no step: " + action.type());
                prepared.add(step);
            }
        } catch (RuntimeException exception) {
            return ExecutionResult.validationFailed(exception.getMessage());
        }

        String module = context == null || context.sourceModule().isBlank() ? "content" : context.sourceModule();
        String source = context == null || context.sourceId().isBlank() ? "actions" : context.sourceId();
        String rawKey = context == null ? "" : context.idempotencyKey();
        String key = rawKey.isBlank() ? "" : namespacedTransactionKey(module, source, rawKey);
        SsuTransactionManager.TransactionResult result = transactions.execute(
                module, source, key, prepared.stream().map(PreparedContentAction::step).toList());
        boolean committed = result.status() == SsuTransactionManager.Status.SUCCESS
                || result.status() == SsuTransactionManager.Status.DUPLICATE;
        return new ExecutionResult(committed, result.status().name().toLowerCase(Locale.ROOT), result.error(), result);
    }

    private static ContentActionContext actionContext(ContentActionContext context, int actionIndex) {
        if (context == null) return null;
        String key = context.idempotencyKey();
        if (!key.isBlank()) key = key + ":action:" + actionIndex;
        return new ContentActionContext(context.server(), context.player(), context.sourceModule(),
                context.sourceId(), key, context.variables());
    }

    private static String namespacedTransactionKey(String module, String source, String rawKey) {
        String key = rawKey.trim();
        if (key.length() > 256) key = key.substring(0, 256);
        return "content:" + ContentId.require(module, "Transaction module") + ":"
                + ContentId.require(source, "Transaction source") + ":" + key;
    }

    private void registerBuiltIns() {
        register("set_player_flag", (action, context, data) -> {
            ServerPlayer player = requirePlayer(context);
            String key = required(action, "key");
            boolean value = booleanParameter(action, "value", true);
            boolean before = data.hasPlayerFlag(player.getUUID(), key);
            return step("Set player flag " + key,
                    () -> data.setPlayerFlag(player, key, value),
                    () -> data.setPlayerFlag(player, key, before));
        });
        register("set_server_flag", (action, context, data) -> {
            String key = required(action, "key");
            boolean value = booleanParameter(action, "value", true);
            boolean before = data.hasServerFlag(key);
            return step("Set server flag " + key,
                    () -> data.setServerFlag(key, value),
                    () -> data.setServerFlag(key, before));
        });
        register("set_player_counter", (action, context, data) -> {
            ServerPlayer player = requirePlayer(context);
            String key = required(action, "key");
            long value = longParameter(action, "amount");
            long before = data.playerCounter(player.getUUID(), key);
            return step("Set player counter " + key,
                    () -> data.setPlayerCounter(player, key, value),
                    () -> data.setPlayerCounter(player, key, before));
        });
        register("add_player_counter", (action, context, data) -> {
            ServerPlayer player = requirePlayer(context);
            String key = required(action, "key");
            long amount = longParameter(action, "amount");
            long before = data.playerCounter(player.getUUID(), key);
            return step("Add player counter " + key,
                    () -> data.addPlayerCounter(player, key, amount),
                    () -> data.setPlayerCounter(player, key, before));
        });
        register("set_server_counter", (action, context, data) -> {
            String key = required(action, "key");
            long value = longParameter(action, "amount");
            long before = data.serverCounter(key);
            return step("Set server counter " + key,
                    () -> data.setServerCounter(key, value),
                    () -> data.setServerCounter(key, before));
        });
        register("add_server_counter", (action, context, data) -> {
            String key = required(action, "key");
            long amount = longParameter(action, "amount");
            long before = data.serverCounter(key);
            return step("Add server counter " + key,
                    () -> data.addServerCounter(key, amount),
                    () -> data.setServerCounter(key, before));
        });
        register("set_player_unlock", (action, context, data) -> {
            ServerPlayer player = requirePlayer(context);
            String key = required(action, "key");
            boolean value = booleanParameter(action, "value", true);
            boolean before = data.isPlayerUnlocked(player.getUUID(), key);
            return step("Set player unlock " + key,
                    () -> data.setPlayerUnlocked(player, key, value),
                    () -> data.setPlayerUnlocked(player, key, before));
        });
        register("set_server_unlock", (action, context, data) -> {
            String key = required(action, "key");
            boolean value = booleanParameter(action, "value", true);
            boolean before = data.isServerUnlocked(key);
            return step("Set server unlock " + key,
                    () -> data.setServerUnlocked(key, value),
                    () -> data.setServerUnlocked(key, before));
        });
        register("set_reputation", (action, context, data) -> {
            ServerPlayer player = requirePlayer(context);
            String faction = required(action, "faction");
            long value = longParameter(action, "amount");
            int before = data.reputation(player.getUUID(), faction);
            return step("Set reputation " + faction,
                    () -> data.setReputation(player, faction, value),
                    () -> data.setReputation(player, faction, before));
        });
        register("add_reputation", (action, context, data) -> {
            ServerPlayer player = requirePlayer(context);
            String faction = required(action, "faction");
            long amount = longParameter(action, "amount");
            int before = data.reputation(player.getUUID(), faction);
            return step("Add reputation " + faction,
                    () -> data.addReputation(player, faction, amount),
                    () -> data.setReputation(player, faction, before));
        });
        register("set_permission", (action, context, data) -> permissionStep(action, context, false));
        register("grant_permission", (action, context, data) -> permissionStep(action, context, true));
        register("unset_permission", (action, context, data) -> {
            if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
                throw new IllegalArgumentException("The permission system is disabled.");
            }
            ServerPlayer player = requirePlayer(context);
            String permission = required(action, "permission").toLowerCase(Locale.ROOT);
            String before = personalPermission(player, permission);
            return step("Unset permission " + permission,
                    () -> SimpleServerUtilities.PERMISSIONS.removePlayerPermission(player.getUUID(), permission),
                    () -> restorePermission(player, permission, before));
        });
    }

    private static PreparedContentAction permissionStep(ContentAction action, ContentActionContext context, boolean grantAlias) {
        if (!Config.ENABLE_PERMISSION_SYSTEM.get()) {
            throw new IllegalArgumentException("The permission system is disabled.");
        }
        ServerPlayer player = requirePlayer(context);
        String permission = required(action, "permission").toLowerCase(Locale.ROOT);
        String rawValue = action.parameter("value");
        if (rawValue.isBlank() && grantAlias) rawValue = "true";
        if (rawValue.isBlank()) throw new IllegalArgumentException("Missing action parameter: value");
        String value = PermissionCatalog.normalizeValue(permission, rawValue);
        String before = personalPermission(player, permission);
        return step("Set permission " + permission,
                () -> SimpleServerUtilities.PERMISSIONS.setPlayerPermission(player.getUUID(), permission, value),
                () -> restorePermission(player, permission, before));
    }

    private static String personalPermission(ServerPlayer player, String permission) {
        PlayerPermissionData data = SimpleServerUtilities.PERMISSIONS.getPlayerData(player.getUUID());
        return data == null ? null : data.getPermissions().get(permission);
    }

    private static void restorePermission(ServerPlayer player, String permission, String previous) {
        if (previous == null) SimpleServerUtilities.PERMISSIONS.removePlayerPermission(player.getUUID(), permission);
        else SimpleServerUtilities.PERMISSIONS.setPlayerPermission(player.getUUID(), permission, previous);
    }

    private static PreparedContentAction step(String description, ThrowingRunnable apply, ThrowingRunnable rollback) {
        return new PreparedContentAction(description, new SsuTransactionManager.TransactionStep() {
            @Override public void apply() throws Exception { apply.run(); }
            @Override public void rollback() throws Exception { rollback.run(); }
        });
    }

    private static ServerPlayer requirePlayer(ContentActionContext context) {
        if (context == null || context.player() == null) throw new IllegalArgumentException("This action requires a player.");
        return context.player();
    }

    private static String required(ContentAction action, String key) {
        String value = action.parameter(key);
        if (value.isBlank()) throw new IllegalArgumentException("Missing action parameter: " + key);
        return value;
    }

    private static long longParameter(ContentAction action, String key) {
        try {
            return Long.parseLong(required(action, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Action parameter '" + key + "' must be a whole number.");
        }
    }

    private static boolean booleanParameter(ContentAction action, String key, boolean fallback) {
        String value = action.parameter(key);
        if (value.isBlank()) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "allow", "on" -> true;
            case "false", "no", "0", "deny", "off" -> false;
            default -> throw new IllegalArgumentException("Action parameter '" + key + "' must be true or false.");
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public record ExecutionResult(
            boolean successful,
            String status,
            String error,
            SsuTransactionManager.TransactionResult transaction
    ) {
        public ExecutionResult {
            status = status == null ? "" : status;
            error = error == null ? "" : error;
        }

        static ExecutionResult successWithoutTransaction() {
            return new ExecutionResult(true, "success", "", null);
        }

        static ExecutionResult validationFailed(String error) {
            return new ExecutionResult(false, "validation_failed", error, null);
        }
    }
}
