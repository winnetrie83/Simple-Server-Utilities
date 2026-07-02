package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.warp.Warp;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class WarpCommands {

    private WarpCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("warps")
                .requires(source -> source.getEntity() instanceof ServerPlayer)

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
                                && PermissionService.has(player, PermissionService.WARP_ADMIN))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> setWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /warps delete <name>
                .then(Commands.literal("delete")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && PermissionService.has(player, PermissionService.WARP_ADMIN))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> deleteWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /warps info <name>
                .then(Commands.literal("info")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player
                                && PermissionService.has(player, PermissionService.WARP_ADMIN))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(context -> infoWarp(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name")
                                ))))

                // /warps help
                .then(Commands.literal("help")
                        .executes(context -> help(context.getSource())));
    }

    private static int listWarps(CommandSourceStack source) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_WARPS.get()) {
            player.sendSystemMessage(Component.literal("Warps are disabled on this server."));
            return 0;
        }

        Collection<Warp> warps = SimpleServerUtilities.WARPS.getWarps();

        int count = SimpleServerUtilities.WARPS.countWarps();
        int max = Config.MAX_WARPS.get();

        if (max > 0) {
            player.sendSystemMessage(Component.literal("Warps: " + count + " / " + max));
        } else {
            player.sendSystemMessage(Component.literal("Warps: " + count));
        }

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
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_WARPS.get()) {
            player.sendSystemMessage(Component.literal("Warps are disabled on this server."));
            return 0;
        }

        if (!PermissionService.has(player, PermissionService.WARP_USE)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use warps."));
            return 0;
        }

        Warp warp = SimpleServerUtilities.WARPS.getWarp(warpName);

        if (warp == null) {
            player.sendSystemMessage(Component.literal("Warp not found: " + warpName));
            return 0;
        }

        ServerLevel level = getWarpLevel(player, warp);

        if (level == null) {
            player.sendSystemMessage(Component.literal("Warp dimension is not loaded: " + warp.getDimension()));
            return 0;
        }

        player.teleportTo(
                level,
                warp.getX(),
                warp.getY(),
                warp.getZ(),
                Set.of(),
                warp.getYaw(),
                warp.getPitch(),
                true
        );

        player.sendSystemMessage(Component.literal("Teleported to warp '" + warp.getDisplayName() + "'."));
        return 1;
    }

    private static int setWarp(CommandSourceStack source, String warpName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_WARPS.get()) {
            player.sendSystemMessage(Component.literal("Warps are disabled on this server."));
            return 0;
        }

        boolean success = SimpleServerUtilities.WARPS.setWarp(player, warpName);

        if (!success) {
            int max = Config.MAX_WARPS.get();
            player.sendSystemMessage(Component.literal("You reached the maximum amount of warps: " + max));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Warp '" + warpName + "' set."));
        return 1;
    }

    private static int deleteWarp(CommandSourceStack source, String warpName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_WARPS.get()) {
            player.sendSystemMessage(Component.literal("Warps are disabled on this server."));
            return 0;
        }

        boolean success = SimpleServerUtilities.WARPS.deleteWarp(warpName);

        if (!success) {
            player.sendSystemMessage(Component.literal("Warp not found: " + warpName));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Warp '" + warpName + "' deleted."));
        return 1;
    }

    private static int infoWarp(CommandSourceStack source, String warpName) {
        ServerPlayer player = (ServerPlayer) source.getEntity();

        if (!Config.ENABLE_WARPS.get()) {
            player.sendSystemMessage(Component.literal("Warps are disabled on this server."));
            return 0;
        }

        Warp warp = SimpleServerUtilities.WARPS.getWarp(warpName);

        if (warp == null) {
            player.sendSystemMessage(Component.literal("Warp not found: " + warpName));
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
        ServerPlayer player = (ServerPlayer) source.getEntity();

        player.sendSystemMessage(Component.literal("Warp commands:"));
        player.sendSystemMessage(Component.literal(" - /warps"));
        player.sendSystemMessage(Component.literal(" - /warps list"));
        player.sendSystemMessage(Component.literal(" - /warps tp <name>"));

        if (PermissionService.has(player, PermissionService.WARP_ADMIN)) {
            player.sendSystemMessage(Component.literal("Admin commands:"));
            player.sendSystemMessage(Component.literal(" - /warps set <name>"));
            player.sendSystemMessage(Component.literal(" - /warps delete <name>"));
            player.sendSystemMessage(Component.literal(" - /warps info <name>"));
        }

        return 1;
    }

    private static ServerLevel getWarpLevel(ServerPlayer player, Warp warp) {
        try {
            Identifier dimensionId = Identifier.parse(warp.getDimension());
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);

            return player.level().getServer().getLevel(dimension);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatCoordinate(double coordinate) {
        return String.format(Locale.ROOT, "%.1f", coordinate);
    }
}