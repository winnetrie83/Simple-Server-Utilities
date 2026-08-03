package be.winnetrie.mod.simpleserverutilities.content;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.server.level.ServerPlayer;

/** Extensible, fail-closed condition registry shared by all content definitions. */
public final class ContentConditionEngine {
    private final Map<String, ContentConditionHandler> handlers = new LinkedHashMap<>();
    private final ContentProgressionManager progression;

    public ContentConditionEngine(ContentProgressionManager progression) {
        this.progression = Objects.requireNonNull(progression, "progression");
        registerBuiltIns();
    }

    public synchronized void register(String rawType, ContentConditionHandler handler) {
        String type = ContentId.require(rawType, "Condition type");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("Duplicate content condition handler: " + type);
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

    public ContentConditionResult evaluate(ContentCondition rawCondition, ContentConditionContext context) {
        if (rawCondition == null) return ContentConditionResult.allow("No condition.");
        final ContentCondition condition;
        try {
            condition = rawCondition.normalize();
        } catch (RuntimeException exception) {
            return ContentConditionResult.deny("Invalid condition: " + exception.getMessage());
        }
        ContentConditionHandler handler;
        synchronized (this) {
            handler = handlers.get(condition.type());
        }
        if (handler == null) return ContentConditionResult.deny("Unknown condition type: " + condition.type());
        try {
            ContentConditionResult result = handler.evaluate(condition, context, this, progression);
            return result == null ? ContentConditionResult.deny("Condition handler returned no result.") : result;
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("Content condition '{}' failed safely.", condition.type(), exception);
            return ContentConditionResult.deny("Condition evaluation failed safely.");
        }
    }

    public boolean matches(ContentCondition condition, ContentConditionContext context) {
        return evaluate(condition, context).matched();
    }

    private void registerBuiltIns() {
        register("always", (condition, context, engine, data) -> ContentConditionResult.allow("Always available."));
        register("never", (condition, context, engine, data) -> ContentConditionResult.deny("Never available."));
        register("all", (condition, context, engine, data) -> {
            for (ContentCondition child : condition.children()) {
                ContentConditionResult result = engine.evaluate(child, context);
                if (!result.matched()) return result;
            }
            return ContentConditionResult.allow("All conditions matched.");
        });
        register("any", (condition, context, engine, data) -> {
            if (condition.children().isEmpty()) return ContentConditionResult.deny("No alternative conditions were defined.");
            String lastReason = "No condition matched.";
            for (ContentCondition child : condition.children()) {
                ContentConditionResult result = engine.evaluate(child, context);
                if (result.matched()) return result;
                lastReason = result.reason();
            }
            return ContentConditionResult.deny(lastReason);
        });
        register("not", (condition, context, engine, data) -> {
            if (condition.children().size() != 1) return ContentConditionResult.deny("NOT requires exactly one child.");
            ContentConditionResult result = engine.evaluate(condition.children().getFirst(), context);
            return result.matched()
                    ? ContentConditionResult.deny("Negated condition matched.")
                    : ContentConditionResult.allow("Negated condition did not match.");
        });
        register("permission", (condition, context, engine, data) -> {
            ServerPlayer player = requirePlayer(context);
            String permission = required(condition, "permission");
            boolean fallback = booleanParameter(condition, "fallback", false);
            boolean allowed = PermissionService.getBoolean(player, permission, fallback);
            return allowed ? ContentConditionResult.allow("Permission granted: " + permission)
                    : ContentConditionResult.deny("Missing permission: " + permission);
        });
        register("player_flag", (condition, context, engine, data) -> {
            ServerPlayer player = requirePlayer(context);
            String key = required(condition, "key");
            boolean expected = booleanParameter(condition, "value", true);
            boolean actual = data.hasPlayerFlag(player.getUUID(), key);
            return actual == expected ? ContentConditionResult.allow("Player flag matched: " + key)
                    : ContentConditionResult.deny("Player flag did not match: " + key);
        });
        register("server_flag", (condition, context, engine, data) -> {
            String key = required(condition, "key");
            boolean expected = booleanParameter(condition, "value", true);
            boolean actual = data.hasServerFlag(key);
            return actual == expected ? ContentConditionResult.allow("Server flag matched: " + key)
                    : ContentConditionResult.deny("Server flag did not match: " + key);
        });
        register("player_counter_at_least", (condition, context, engine, data) -> comparePlayerCounter(condition, context, data, true));
        register("player_counter_at_most", (condition, context, engine, data) -> comparePlayerCounter(condition, context, data, false));
        register("server_counter_at_least", (condition, context, engine, data) -> compareServerCounter(condition, data, true));
        register("server_counter_at_most", (condition, context, engine, data) -> compareServerCounter(condition, data, false));
        register("player_unlocked", (condition, context, engine, data) -> {
            ServerPlayer player = requirePlayer(context);
            String key = required(condition, "key");
            boolean expected = booleanParameter(condition, "value", true);
            boolean actual = data.isPlayerUnlocked(player.getUUID(), key);
            return actual == expected ? ContentConditionResult.allow("Player unlock matched: " + key)
                    : ContentConditionResult.deny("Player unlock did not match: " + key);
        });
        register("server_unlocked", (condition, context, engine, data) -> {
            String key = required(condition, "key");
            boolean expected = booleanParameter(condition, "value", true);
            boolean actual = data.isServerUnlocked(key);
            return actual == expected ? ContentConditionResult.allow("Server unlock matched: " + key)
                    : ContentConditionResult.deny("Server unlock did not match: " + key);
        });
        register("reputation_at_least", (condition, context, engine, data) -> compareReputation(condition, context, data, true));
        register("reputation_at_most", (condition, context, engine, data) -> compareReputation(condition, context, data, false));
        register("module_enabled", (condition, context, engine, data) -> {
            String featureName = required(condition, "feature");
            final ContentFeature feature;
            try {
                feature = ContentFeature.valueOf(featureName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return ContentConditionResult.deny("Unknown content feature: " + featureName);
            }
            boolean expected = booleanParameter(condition, "value", true);
            boolean actual = ContentAccessPolicy.moduleEnabled(feature);
            return actual == expected ? ContentConditionResult.allow("Module state matched: " + featureName)
                    : ContentConditionResult.deny("Module state did not match: " + featureName);
        });
    }

    private static ContentConditionResult comparePlayerCounter(
            ContentCondition condition, ContentConditionContext context,
            ContentProgressionManager data, boolean atLeast) {
        ServerPlayer player = requirePlayer(context);
        String key = required(condition, "key");
        long expected = longParameter(condition, "amount");
        long actual = data.playerCounter(player.getUUID(), key);
        boolean matched = atLeast ? actual >= expected : actual <= expected;
        return matched ? ContentConditionResult.allow("Player counter matched: " + key)
                : ContentConditionResult.deny("Player counter requirement failed: " + key);
    }

    private static ContentConditionResult compareServerCounter(
            ContentCondition condition, ContentProgressionManager data, boolean atLeast) {
        String key = required(condition, "key");
        long expected = longParameter(condition, "amount");
        long actual = data.serverCounter(key);
        boolean matched = atLeast ? actual >= expected : actual <= expected;
        return matched ? ContentConditionResult.allow("Server counter matched: " + key)
                : ContentConditionResult.deny("Server counter requirement failed: " + key);
    }

    private static ContentConditionResult compareReputation(
            ContentCondition condition, ContentConditionContext context,
            ContentProgressionManager data, boolean atLeast) {
        ServerPlayer player = requirePlayer(context);
        String faction = required(condition, "faction");
        long expected = longParameter(condition, "amount");
        int actual = data.reputation(player.getUUID(), faction);
        boolean matched = atLeast ? actual >= expected : actual <= expected;
        return matched ? ContentConditionResult.allow("Reputation matched: " + faction)
                : ContentConditionResult.deny("Reputation requirement failed: " + faction);
    }

    private static ServerPlayer requirePlayer(ContentConditionContext context) {
        if (context == null || context.player() == null) {
            throw new IllegalArgumentException("This condition requires a player.");
        }
        return context.player();
    }

    private static String required(ContentCondition condition, String key) {
        String value = condition.parameter(key);
        if (value.isBlank()) throw new IllegalArgumentException("Missing condition parameter: " + key);
        return value;
    }

    private static long longParameter(ContentCondition condition, String key) {
        try {
            return Long.parseLong(required(condition, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Condition parameter '" + key + "' must be a whole number.");
        }
    }

    private static boolean booleanParameter(ContentCondition condition, String key, boolean fallback) {
        String value = condition.parameter(key);
        if (value.isBlank()) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "allow", "on" -> true;
            case "false", "no", "0", "deny", "off" -> false;
            default -> throw new IllegalArgumentException("Condition parameter '" + key + "' must be true or false.");
        };
    }
}
