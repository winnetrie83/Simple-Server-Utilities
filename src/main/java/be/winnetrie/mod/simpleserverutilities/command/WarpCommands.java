package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Collection;
import java.util.Locale;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.permission.PermissionContext;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportOptions;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.policy.TeleportType;
import be.winnetrie.mod.simpleserverutilities.permission.policy.WarpPolicy;
import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.warp.Warp;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class WarpCommands {

    private WarpCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("warps")
                .requires(source -> SsuModuleAccess.active("warps") && source.getEntity() instanceof ServerPlayer)

                // /warps
                .executes(context -> listWarps(context.getSource()))

                // /warps list
                .then(Commands.literal("list")
                        .executes(context -> listWarps(context.getSource())))

                // /warps tp <name>
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> teleportWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /warps set <name>
                .then(Commands.literal("set")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && WarpPolicy.canSetWarp(player))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /warps delete <name>
                .then(Commands.literal("delete")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && WarpPolicy.canDeleteWarp(player))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> deleteWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /warps info <name>
                .then(Commands.literal("info")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && WarpPolicy.canViewWarpInfo(player))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> infoWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))
                // /warps cancel
                .then(Commands.literal("cancel")
                        .executes(context -> cancelTeleport(context.getSource())))

                // /warps help
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())));
    }

    private static int listWarps(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!WarpPolicy.canUseWarps(player, context)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use warps here."));
            return 0;
        }

        Collection<Warp> warps = SimpleServerUtilities.WARPS.getAccessibleWarps(player).stream()
                .filter(warp -> !warp.isPlayerRental())
                .toList();

        int count = warps.size();
        player.sendSystemMessage(Component.literal("Available legacy server warps: " + count));

        if (warps.isEmpty()) {
            player.sendSystemMessage(Component.literal("There are no warps yet."));
            return 1;
        }

        for (Warp warp : warps) {
            player.sendSystemMessage(Component.literal(" - " + warp.getDisplayName()));
        }

        player.sendSystemMessage(Component.literal("Use /warps tp <name> to teleport."));
        return 1;
    }

    private static int teleportWarp(CommandSourceStack source, String warpName) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (!SsuModuleAccess.active("teleport")) {
            player.sendSystemMessage(Component.literal("The Teleport module is disabled."));
            return 0;
        }

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!WarpPolicy.canTeleportWarp(player, context)) {
            player.sendSystemMessage(Component.literal(TeleportPolicy.denialMessage(TeleportType.WARP, context)));
            return 0;
        }

        Warp warp = SimpleServerUtilities.WARPS.getWarp(warpName);

        if (warp == null || warp.isPlayerRental() || !SimpleServerUtilities.WARPS.canAccess(player, warp)) {
            player.sendSystemMessage(Component.literal("Server warp not found. Player-rented warps are used through Dashboard > Travel."));
            return 0;
        }

        ServerLevel level = getWarpLevel(player, warp);

        if (level == null) {
            player.sendSystemMessage(Component.literal("Warp dimension is not loaded: " + warp.getDimension()));
            return 0;
        }

        TeleportOptions options = TeleportPolicy.resolve(player, TeleportType.WARP, context);

        return SimpleServerUtilities.TELEPORTS.requestTeleport(
                player,
                "warps",
                "warp '" + warp.getDisplayName() + "'",
                options,
                level,
                warp.getX(),
                warp.getY(),
                warp.getZ(),
                warp.getYaw(),
                warp.getPitch(),
                candidate -> {
                    PermissionContext current = PermissionContext.at(candidate, candidate.blockPosition());
                    Warp currentWarp = SimpleServerUtilities.WARPS.getWarp(warpName);
                    return WarpPolicy.canTeleportWarp(candidate, current)
                            && currentWarp != null
                            && !currentWarp.isPlayerRental()
                            && SimpleServerUtilities.WARPS.canAccess(candidate, currentWarp);
                },
                candidate -> TeleportPolicy.denialMessage(TeleportType.WARP,
                        PermissionContext.at(candidate, candidate.blockPosition()))
        );
    }

    private static int setWarp(CommandSourceStack source, String warpName) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!WarpPolicy.canSetWarp(player, context)) {
            player.sendSystemMessage(Component.literal("You do not have permission to set warps here."));
            return 0;
        }

        Warp existing = SimpleServerUtilities.WARPS.getWarp(warpName);
        if (existing != null && existing.isPlayerRental()) {
            player.sendSystemMessage(Component.literal("That name belongs to a player-rented warp and can only be managed through the GUI."));
            return 0;
        }
        boolean success = SimpleServerUtilities.WARPS.setWarp(player, warpName);

        if (!success) {
            int max = WarpPolicy.getMaxWarps(player);
            player.sendSystemMessage(Component.literal("You reached the maximum amount of server warps: " + max));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Warp '" + warpName + "' set."));
        return 1;
    }

    private static int deleteWarp(CommandSourceStack source, String warpName) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!WarpPolicy.canDeleteWarp(player, context)) {
            player.sendSystemMessage(Component.literal("You do not have permission to delete warps here."));
            return 0;
        }

        Warp warp = SimpleServerUtilities.WARPS.getWarp(warpName);
        if (warp != null && warp.isPlayerRental()) {
            player.sendSystemMessage(Component.literal("Player-rented warps can only be deleted through the player or administrator GUI."));
            return 0;
        }
        boolean success = SimpleServerUtilities.WARPS.deleteWarp(warpName);

        if (!success) {
            player.sendSystemMessage(Component.literal("Server warp not found: " + warpName));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Warp '" + warpName + "' deleted."));
        return 1;
    }

    private static int infoWarp(CommandSourceStack source, String warpName) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();

        PermissionContext context = PermissionContext.at(player, player.blockPosition());

        if (!WarpPolicy.canViewWarpInfo(player, context)) {
            player.sendSystemMessage(Component.literal("You do not have permission to view warp info here."));
            return 0;
        }

        Warp warp = SimpleServerUtilities.WARPS.getWarp(warpName);

        if (warp == null) {
            player.sendSystemMessage(Component.literal("Warp not found: " + warpName));
            return 0;
        }
        if (warp.isPlayerRental()) {
            player.sendSystemMessage(Component.literal("Player-rented warp information is available through Dashboard > My Warps or Travel."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Warp: " + warp.getDisplayName()));
        player.sendSystemMessage(Component.literal("Dimension: " + warp.getDimension()));
        player.sendSystemMessage(Component.literal(
                "Location: x: " + formatCoordinate(warp.getX())
                        + " y: " + formatCoordinate(warp.getY())
                        + " z: " + formatCoordinate(warp.getZ())
        ));

        if (warp.getCreatedBy() != null) {
            player.sendSystemMessage(Component.literal("Created by: " + warp.getCreatedBy()));
        }

        return 1;
    }

    private static int help(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();

        player.sendSystemMessage(Component.literal("Warp commands:"));
        player.sendSystemMessage(Component.literal(" - /warps"));
        player.sendSystemMessage(Component.literal(" - /warps list"));
        player.sendSystemMessage(Component.literal(" - /warps tp <name>"));
        player.sendSystemMessage(Component.literal(" - /warps cancel"));

        if (WarpPolicy.canAdminWarps(player)) {
            player.sendSystemMessage(Component.literal("Admin commands:"));
            player.sendSystemMessage(Component.literal(" - /warps set <name>"));
            player.sendSystemMessage(Component.literal(" - /warps delete <name>"));
            player.sendSystemMessage(Component.literal(" - /warps info <name>"));
        }

        return 1;
    }

    private static ServerLevel getWarpLevel(ServerPlayer player, Warp warp) {
        try {
            ResourceLocation dimensionId = ResourceLocation.parse(warp.getDimension());
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

            return player.level().getServer().getLevel(dimension);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.1f", coordinate);
    }


    private static int cancelTeleport(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        if (!SsuModuleAccess.active("teleport")) {
            player.sendSystemMessage(Component.literal("The Teleport module is disabled."));
            return 0;
        }

        boolean cancelled = SimpleServerUtilities.TELEPORTS.cancel(player);

        if (!cancelled) {
            player.sendSystemMessage(Component.literal("You do not have a pending teleport."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Pending teleport cancelled."));
        return 1;
    }

    private static boolean requireModule(CommandSourceStack source) {
        if (be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("warps")) return true;
        source.sendFailure(Component.literal("Warps is disabled or blocked by a required dependency."));
        return false;
    }
}