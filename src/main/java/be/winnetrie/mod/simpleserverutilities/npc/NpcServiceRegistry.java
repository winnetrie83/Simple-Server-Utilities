package be.winnetrie.mod.simpleserverutilities.npc;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportType;
import be.winnetrie.mod.simpleserverutilities.permission.policy.WarpPolicy;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnService;
import be.winnetrie.mod.simpleserverutilities.warp.Warp;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Extensible, closed and server-authoritative service router used by dialogue choices. */
public final class NpcServiceRegistry {
    private final Map<String, RegisteredService> services = new LinkedHashMap<>();

    public NpcServiceRegistry() {
        registerBuiltIns();
    }

    /** Registers a custom service whose own handler performs all validation. */
    public synchronized void register(String rawId, Handler handler) {
        register(rawId, (player, instance, definition, target) -> ServiceResult.ok(false, ""), handler);
    }

    /** Registers a service with a side-effect-free preflight validator. */
    public synchronized void register(String rawId, Validator validator, Handler handler) {
        String id = normalize(rawId);
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(handler, "handler");
        if (services.putIfAbsent(id, new RegisteredService(validator, handler)) != null) {
            throw new IllegalStateException("Duplicate NPC service: " + id);
        }
    }

    public synchronized boolean isRegistered(String rawId) {
        return rawId == null || rawId.isBlank() || services.containsKey(normalize(rawId));
    }

    /** Stable snapshot used by the NPC Functions editor. */
    public synchronized List<String> serviceIds() {
        return List.copyOf(new ArrayList<>(services.keySet()));
    }

    /** Performs permission/module/target checks before transactional dialogue actions commit. */
    public ServiceResult validate(ServerPlayer player, NpcInstance instance, NpcDefinition definition,
                                  String rawService, String target) {
        if (rawService == null || rawService.isBlank()) return ServiceResult.ok(false, "");
        RegisteredService registered = registered(rawService);
        if (registered == null) return ServiceResult.fail("Unknown NPC service: " + normalize(rawService));
        try {
            return registered.validator.validate(player, instance, definition, safeTarget(target));
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("NPC service '{}' preflight failed safely.", rawService, exception);
            return ServiceResult.fail("The NPC service could not be validated safely.");
        }
    }

    public ServiceResult execute(ServerPlayer player, NpcInstance instance, NpcDefinition definition,
                                 String rawService, String target) {
        if (rawService == null || rawService.isBlank()) return ServiceResult.ok(false, "");
        RegisteredService registered = registered(rawService);
        String service = normalize(rawService);
        if (registered == null) return ServiceResult.fail("Unknown NPC service: " + service);
        try {
            return registered.handler.execute(player, instance, definition, safeTarget(target));
        } catch (RuntimeException exception) {
            SimpleServerUtilities.LOGGER.error("NPC service '{}' failed safely.", service, exception);
            return ServiceResult.fail("The NPC service failed safely.");
        }
    }

    private synchronized RegisteredService registered(String rawService) {
        return services.get(normalize(rawService));
    }

    private void registerBuiltIns() {
        register("mail",
                (player, instance, definition, target) -> validateMail(player),
                (player, instance, definition, target) -> {
                    ServiceResult validation = validateMail(player);
                    if (!validation.successful()) return validation;
                    SimpleServerUtilities.MAIL.openMailbox(player);
                    return ServiceResult.ok(true, "Mailbox opened.");
                });
        register("auction_house",
                (player, instance, definition, target) -> validateAuctionHouse(player),
                (player, instance, definition, target) -> {
                    ServiceResult validation = validateAuctionHouse(player);
                    if (!validation.successful()) return validation;
                    SimpleServerUtilities.AUCTION_HOUSE.openTrusted(player);
                    return ServiceResult.ok(true, "Auction House opened.");
                });
        register("ssu_menu",
                (player, instance, definition, target) -> servicePermission(player, PermissionKeys.NPCS_SERVICE_MENU)
                        ? ServiceResult.ok(false, "") : ServiceResult.fail("You cannot use this NPC menu service."),
                (player, instance, definition, target) -> {
                    if (!servicePermission(player, PermissionKeys.NPCS_SERVICE_MENU)) {
                        return ServiceResult.fail("You cannot use this NPC menu service.");
                    }
                    SimpleServerUtilities.MENUS.open(player);
                    return ServiceResult.ok(true, "SSU menu opened.");
                });
        register("heal",
                (player, instance, definition, target) -> servicePermission(player, PermissionKeys.NPCS_SERVICE_HEAL)
                        ? ServiceResult.ok(false, "") : ServiceResult.fail("You cannot use NPC healing services."),
                (player, instance, definition, target) -> {
                    if (!servicePermission(player, PermissionKeys.NPCS_SERVICE_HEAL)) {
                        return ServiceResult.fail("You cannot use NPC healing services.");
                    }
                    float before = player.getHealth();
                    player.setHealth(player.getMaxHealth());
                    player.getFoodData().setFoodLevel(20);
                    return ServiceResult.ok(true,
                            before >= player.getMaxHealth() ? "You are already fully healed." : "You have been healed.");
                });
        register("spawn",
                (player, instance, definition, target) -> servicePermission(player, PermissionKeys.NPCS_SERVICE_TELEPORT)
                        ? ServiceResult.ok(false, "") : ServiceResult.fail("You cannot use NPC teleport services."),
                (player, instance, definition, target) -> {
                    if (!servicePermission(player, PermissionKeys.NPCS_SERVICE_TELEPORT)) {
                        return ServiceResult.fail("You cannot use NPC teleport services.");
                    }
                    return SpawnService.requestTeleport(player) > 0
                            ? ServiceResult.ok(true, "Server-spawn teleport requested.")
                            : ServiceResult.fail("Server-spawn teleport failed.");
                });
        register("warp",
                (player, instance, definition, target) -> validateWarp(player, target),
                (player, instance, definition, target) -> teleportWarp(player, target));
    }

    private static ServiceResult validateMail(ServerPlayer player) {
        if (!servicePermission(player, PermissionKeys.NPCS_SERVICE_MAIL)) {
            return ServiceResult.fail("You cannot use NPC mail services.");
        }
        if (!PermissionService.getBoolean(player, PermissionKeys.MAIL_ACCESS, true)) {
            return ServiceResult.fail("You have not unlocked mailbox access.");
        }
        if (!Config.ENABLE_MAIL.get() || !SimpleServerUtilities.CORE.modules().isActive("mail")) {
            return ServiceResult.fail("The mail module is disabled.");
        }
        return ServiceResult.ok(false, "");
    }

    private static ServiceResult validateAuctionHouse(ServerPlayer player) {
        if (!servicePermission(player, PermissionKeys.NPCS_SERVICE_AUCTION_HOUSE)) {
            return ServiceResult.fail("You cannot use NPC Auction House services.");
        }
        if (!PermissionService.getBoolean(player, PermissionKeys.AUCTION_HOUSE_ACCESS, true)) {
            return ServiceResult.fail("You do not have permission to use the Auction House.");
        }
        if (!Config.ENABLE_AUCTION_HOUSE.get()
                || !SimpleServerUtilities.CORE.modules().isActive("auction_house")) {
            return ServiceResult.fail("The Auction House is disabled.");
        }
        return ServiceResult.ok(false, "");
    }

    private static ServiceResult validateWarp(ServerPlayer player, String target) {
        if (!servicePermission(player, PermissionKeys.NPCS_SERVICE_TELEPORT)) {
            return ServiceResult.fail("You cannot use NPC teleport services.");
        }
        if (target.isBlank()) return ServiceResult.fail("The dialogue choice has no warp target.");
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        if (!WarpPolicy.canTeleportWarp(player, context)) {
            return ServiceResult.fail(TeleportPolicy.denialMessage(TeleportType.WARP, context));
        }
        Warp warp = SimpleServerUtilities.WARPS.getWarp(target);
        if (warp == null) return ServiceResult.fail("Warp not found: " + target);
        return resolveLevel(player, warp) == null
                ? ServiceResult.fail("Warp dimension is invalid or unavailable.")
                : ServiceResult.ok(false, "");
    }

    private static ServiceResult teleportWarp(ServerPlayer player, String target) {
        ServiceResult validation = validateWarp(player, target);
        if (!validation.successful()) return validation;
        PermissionContext context = PermissionContext.at(player, player.blockPosition());
        Warp warp = SimpleServerUtilities.WARPS.getWarp(target);
        ServerLevel level = resolveLevel(player, warp);
        if (warp == null || level == null) return ServiceResult.fail("Warp dimension is no longer available.");
        int result = SimpleServerUtilities.TELEPORTS.requestTeleport(
                player, "warps", "warp '" + warp.getDisplayName() + "'",
                TeleportPolicy.resolve(player, TeleportType.WARP, context), level,
                warp.getX(), warp.getY(), warp.getZ(), warp.getYaw(), warp.getPitch(),
                candidate -> WarpPolicy.canTeleportWarp(candidate,
                        PermissionContext.at(candidate, candidate.blockPosition())),
                candidate -> TeleportPolicy.denialMessage(TeleportType.WARP,
                        PermissionContext.at(candidate, candidate.blockPosition())));
        return result > 0
                ? ServiceResult.ok(true, "Warp teleport requested.")
                : ServiceResult.fail("Warp teleport failed.");
    }

    private static ServerLevel resolveLevel(ServerPlayer player, Warp warp) {
        if (warp == null) return null;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(warp.getDimension()));
            return player.level().getServer().getLevel(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean servicePermission(ServerPlayer player, String permission) {
        return PermissionService.getBoolean(player, permission, true);
    }

    private static String safeTarget(String target) {
        String safe = target == null ? "" : target.trim();
        return safe.length() <= 256 ? safe : safe.substring(0, 256);
    }

    private static String normalize(String value) {
        String result = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!result.matches("[a-z0-9._]{1,64}")) {
            throw new IllegalArgumentException("Invalid NPC service ID: " + value);
        }
        return result;
    }

    @FunctionalInterface
    public interface Validator {
        ServiceResult validate(ServerPlayer player, NpcInstance instance, NpcDefinition definition, String target);
    }

    @FunctionalInterface
    public interface Handler {
        ServiceResult execute(ServerPlayer player, NpcInstance instance, NpcDefinition definition, String target);
    }

    private record RegisteredService(Validator validator, Handler handler) {
    }

    public record ServiceResult(boolean successful, boolean closeDialogue, String message) {
        public ServiceResult {
            message = message == null ? "" : message;
        }

        public static ServiceResult ok(boolean close, String message) {
            return new ServiceResult(true, close, message);
        }

        public static ServiceResult fail(String message) {
            return new ServiceResult(false, false, message);
        }
    }
}
