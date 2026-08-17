package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import be.winnetrie.mod.simpleserverutilities.visualization.PlayerBorderPreferences;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BorderCommands {

    private BorderCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("borders")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("claims")
                        .then(Commands.literal("on")
                                .executes(context -> setClaims(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setClaims(context.getSource(), false))))
                .then(Commands.literal("regions")
                        .then(Commands.literal("on")
                                .executes(context -> setRegions(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setRegions(context.getSource(), false))))
                .then(Commands.literal("refresh")
                        .executes(context -> refresh(context.getSource())))
                .then(Commands.literal("color")
                        .then(Commands.literal("list")
                                .executes(context -> listColors(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .then(Commands.argument("hex", StringArgumentType.word())
                                                .executes(context -> setColor(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "category"),
                                                        StringArgumentType.getString(context, "hex")
                                                )))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("category", StringArgumentType.word())
                                        .executes(context -> resetColor(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "category")
                                        ))))
                        .then(Commands.literal("resetall")
                                .executes(context -> resetAllColors(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }

        PlayerBorderPreferences preferences = SimpleServerUtilities.BORDER_SETTINGS.preferences(player.getUUID());
        player.sendSystemMessage(Component.literal("Border visibility:"));
        player.sendSystemMessage(Component.literal(" - Claims: " + onOff(preferences.isClaimBordersVisible())));
        player.sendSystemMessage(Component.literal(" - Regions: " + onOff(preferences.isRegionBordersVisible())));
        return 1;
    }

    static int setClaims(CommandSourceStack source, boolean visible) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        if (!SsuModuleAccess.active("visualization") || !SsuModuleAccess.active("claims")
                || !PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_CLAIMS_VIEW, true)) {
            player.sendSystemMessage(Component.literal("Claim borders are not allowed by the server."));
            return 0;
        }

        SimpleServerUtilities.BORDER_SETTINGS.setClaimsVisible(player.getUUID(), visible);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        player.sendSystemMessage(Component.literal("Claim borders turned " + onOff(visible) + "."));
        return 1;
    }

    static int setRegions(CommandSourceStack source, boolean visible) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        if (!SsuModuleAccess.active("visualization") || !SsuModuleAccess.active("regions")
                || !PermissionService.getBooleanWithoutOperatorBypass(player, PermissionKeys.BORDER_REGIONS_VIEW, true)) {
            player.sendSystemMessage(Component.literal("Region borders are not allowed by the server."));
            return 0;
        }

        SimpleServerUtilities.BORDER_SETTINGS.setRegionsVisible(player.getUUID(), visible);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        player.sendSystemMessage(Component.literal("Region borders turned " + onOff(visible) + "."));
        return 1;
    }

    private static int refresh(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        SimpleServerUtilities.BORDER_VISUALIZATIONS.syncOverview(player, true);
        player.sendSystemMessage(Component.literal("Border visualization refreshed."));
        return 1;
    }

    private static int listColors(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        if (!canManageColors(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage border colors."));
            return 0;
        }

        source.sendSystemMessage(Component.literal("Configured border colors:"));
        for (BorderCategory category : BorderCategory.values()) {
            int rgb = SimpleServerUtilities.BORDER_SETTINGS.settings().getRgb(category);
            source.sendSystemMessage(Component.literal(" - " + category.serializedName() + ": " + formatHex(rgb)));
        }
        return 1;
    }

    private static int setColor(CommandSourceStack source, String rawCategory, String rawHex) {
        if (!requireModule(source)) return 0;
        if (!canManageColors(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage border colors."));
            return 0;
        }

        BorderCategory category = BorderCategory.parse(rawCategory);
        if (category == null) {
            source.sendFailure(Component.literal("Unknown border category: " + rawCategory));
            return 0;
        }

        Integer rgb = parseRgb(rawHex);
        if (rgb == null) {
            source.sendFailure(Component.literal("Invalid color. Use a six-digit RGB value such as #42F56C."));
            return 0;
        }

        SimpleServerUtilities.BORDER_SETTINGS.setColor(category, rgb);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(source.getServer());
        source.sendSystemMessage(Component.literal(
                "Border color for " + category.serializedName() + " set to " + formatHex(rgb) + "."
        ));
        return 1;
    }

    private static int resetColor(CommandSourceStack source, String rawCategory) {
        if (!requireModule(source)) return 0;
        if (!canManageColors(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage border colors."));
            return 0;
        }

        BorderCategory category = BorderCategory.parse(rawCategory);
        if (category == null) {
            source.sendFailure(Component.literal("Unknown border category: " + rawCategory));
            return 0;
        }

        SimpleServerUtilities.BORDER_SETTINGS.resetColor(category);
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(source.getServer());
        source.sendSystemMessage(Component.literal("Reset border color for " + category.serializedName() + "."));
        return 1;
    }

    private static int resetAllColors(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        if (!canManageColors(source)) {
            source.sendFailure(Component.literal("You do not have permission to manage border colors."));
            return 0;
        }

        SimpleServerUtilities.BORDER_SETTINGS.resetColors();
        SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(source.getServer());
        source.sendSystemMessage(Component.literal("Reset all border colors to their defaults."));
        return 1;
    }

    private static boolean canManageColors(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return PermissionService.getBoolean(player, PermissionKeys.VISUALIZATION_ADMIN, false);
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.literal("This command can only be used by a player."));
        return null;
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static Integer parseRgb(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        if (!normalized.matches("[0-9A-Fa-f]{6}")) {
            return null;
        }
        return Integer.parseInt(normalized, 16);
    }

    private static String formatHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static boolean requireModule(CommandSourceStack source) {
        if (be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("visualization")) return true;
        source.sendFailure(Component.literal("Visualization is disabled or blocked by a required dependency."));
        return false;
    }
}
