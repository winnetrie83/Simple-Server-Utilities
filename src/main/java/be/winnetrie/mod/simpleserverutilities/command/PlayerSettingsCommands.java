package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.MinimapService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.settings.MinimapPosition;
import be.winnetrie.mod.simpleserverutilities.settings.MinimapShape;
import be.winnetrie.mod.simpleserverutilities.settings.PlayerUiPreferences;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Fallback commands used by the current UI and by the upcoming settings page. */
public final class PlayerSettingsCommands {

    private PlayerSettingsCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("settings")
                .requires(source -> source.getEntity() instanceof ServerPlayer player
                        && PermissionService.getBoolean(player, PermissionKeys.SETTINGS_USE, true))
                .executes(context -> show(context.getSource()))
                .then(Commands.literal("hints")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setHints(
                                        context.getSource(),
                                        BoolArgumentType.getBool(context, "enabled")
                                ))))
                .then(Commands.literal("minimap")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setMinimapEnabled(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        ))))
                        .then(Commands.literal("size")
                                .then(Commands.argument("pixels", IntegerArgumentType.integer(64, 256))
                                        .executes(context -> setMinimapSize(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "pixels")
                                        ))))
                        .then(Commands.literal("shape")
                                .then(Commands.argument("shape", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.List.of("circle", "rectangle"), builder))
                                        .executes(context -> setMinimapShape(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "shape")
                                        ))))
                        .then(Commands.literal("position")
                                .then(Commands.argument("position", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.List.of("top_left", "top_right", "bottom_left", "bottom_right"),
                                                builder))
                                        .executes(context -> setMinimapPosition(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "position")
                                        ))))
                        .then(Commands.literal("northup")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setNorthUp(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        ))))
                        .then(Commands.literal("claims")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setClaimOverlay(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        ))))
                        .then(Commands.literal("regions")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setRegionOverlay(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        )))));
    }

    private static int show(CommandSourceStack source) {
        ServerPlayer player = player(source);
        PlayerUiPreferences value = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        player.sendSystemMessage(Component.literal("SSU player settings:"));
        player.sendSystemMessage(Component.literal(" Dashboard hints: " + value.isDashboardHints()));
        player.sendSystemMessage(Component.literal(" Minimap enabled: " + value.isMinimapEnabled()));
        player.sendSystemMessage(Component.literal(" Minimap size: " + value.getMinimapSize() + " px"));
        player.sendSystemMessage(Component.literal(" Minimap shape: " + value.getMinimapShape().name().toLowerCase()));
        player.sendSystemMessage(Component.literal(" Minimap position: " + value.getMinimapPosition().name().toLowerCase()));
        player.sendSystemMessage(Component.literal(" North up: " + value.isMinimapNorthUp()));
        player.sendSystemMessage(Component.literal(" Claim overlay: " + value.isMinimapShowClaims()));
        player.sendSystemMessage(Component.literal(" Region overlay: " + value.isMinimapShowRegions()));
        return 1;
    }

    private static int setHints(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setDashboardHints(enabled), "Dashboard hints: " + enabled);
    }

    private static int setMinimapEnabled(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = player(source);
        if (enabled && !PermissionService.getBoolean(player, PermissionKeys.MINIMAP_USE, true)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use the SSU minimap."));
            return 0;
        }
        return update(source, value -> value.setMinimapEnabled(enabled), "Minimap enabled: " + enabled);
    }

    private static int setMinimapSize(CommandSourceStack source, int pixels) {
        return update(source, value -> value.setMinimapSize(pixels), "Minimap size: " + pixels + " px");
    }

    private static int setMinimapShape(CommandSourceStack source, String rawShape) {
        try {
            MinimapShape shape = MinimapShape.valueOf(rawShape.trim().toUpperCase(java.util.Locale.ROOT));
            return update(source, value -> value.setMinimapShape(shape), "Minimap shape: " + rawShape);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Shape must be circle or rectangle."));
            return 0;
        }
    }

    private static int setMinimapPosition(CommandSourceStack source, String rawPosition) {
        try {
            MinimapPosition position = MinimapPosition.valueOf(
                    rawPosition.trim().toUpperCase(java.util.Locale.ROOT)
            );
            return update(source, value -> value.setMinimapPosition(position), "Minimap position: " + rawPosition);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(
                    "Position must be top_left, top_right, bottom_left or bottom_right."
            ));
            return 0;
        }
    }

    private static int setNorthUp(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setMinimapNorthUp(enabled), "Minimap north-up: " + enabled);
    }

    private static int setClaimOverlay(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setMinimapShowClaims(enabled), "Minimap claim overlay: " + enabled);
    }

    private static int setRegionOverlay(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setMinimapShowRegions(enabled), "Minimap region overlay: " + enabled);
    }

    private static int update(
            CommandSourceStack source,
            java.util.function.Consumer<PlayerUiPreferences> mutation,
            String confirmation
    ) {
        ServerPlayer player = player(source);
        PlayerUiPreferences value = SimpleServerUtilities.UI_PREFERENCES.ensurePlayer(player);
        mutation.accept(value);
        value.normalize();
        SimpleServerUtilities.UI_PREFERENCES.save();
        player.sendSystemMessage(Component.literal(confirmation));
        MinimapService.send(player);
        return 1;
    }

    private static ServerPlayer player(CommandSourceStack source) {
        return (ServerPlayer) source.getEntity();
    }
}
