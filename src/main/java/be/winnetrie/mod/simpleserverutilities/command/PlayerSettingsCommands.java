package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.MinimapService;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.settings.MinimapPosition;
import be.winnetrie.mod.simpleserverutilities.settings.MinimapShape;
import be.winnetrie.mod.simpleserverutilities.settings.PlayerUiPreferences;
import be.winnetrie.mod.simpleserverutilities.utilitymining.MiningActivationMode;
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
                                        )))))
                .then(Commands.literal("worldmap")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("claims")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setWorldMapClaimOverlay(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        ))))
                        .then(Commands.literal("regions")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setWorldMapRegionOverlay(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        )))))
                .then(Commands.literal("treecapitator")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setTreecapitatorEnabled(
                                                context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("activation")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.List.of("sneak", "keybind"), builder))
                                        .executes(context -> setTreecapitatorActivation(
                                                context.getSource(), StringArgumentType.getString(context, "mode")))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("hex", StringArgumentType.word())
                                        .executes(context -> setTreecapitatorColor(
                                                context.getSource(), StringArgumentType.getString(context, "hex")))))
                        .then(Commands.literal("brightness")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(10, 100))
                                        .executes(context -> setTreecapitatorBrightness(
                                                context.getSource(), IntegerArgumentType.getInteger(context, "percent"))))))
                .then(Commands.literal("veinminer")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setVeinminerEnabled(
                                                context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                        .then(Commands.literal("activation")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.List.of("sneak", "keybind"), builder))
                                        .executes(context -> setVeinminerActivation(
                                                context.getSource(), StringArgumentType.getString(context, "mode")))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("hex", StringArgumentType.word())
                                        .executes(context -> setVeinminerColor(
                                                context.getSource(), StringArgumentType.getString(context, "hex")))))
                        .then(Commands.literal("brightness")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(10, 100))
                                        .executes(context -> setVeinminerBrightness(
                                                context.getSource(), IntegerArgumentType.getInteger(context, "percent"))))))
                .then(Commands.literal("mail")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("auto_delete_private")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setMailAutoDeletePlayer(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        ))))
                        .then(Commands.literal("auto_delete_server")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setMailAutoDeleteSystem(
                                                context.getSource(),
                                                BoolArgumentType.getBool(context, "enabled")
                                        ))))
                        .then(Commands.literal("auto_delete_auction")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setMailAutoDeleteAuction(
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
        player.sendSystemMessage(Component.literal(" Minimap claim overlay: " + value.isMinimapShowClaims()));
        player.sendSystemMessage(Component.literal(" Minimap region overlay: " + value.isMinimapShowRegions()));
        player.sendSystemMessage(Component.literal(" World-map claim overlay: " + value.isWorldMapShowClaims()));
        player.sendSystemMessage(Component.literal(" World-map region overlay: " + value.isWorldMapShowRegions()));
        player.sendSystemMessage(Component.literal(" Treecapitator: " + value.isTreecapitatorEnabled()
                + " | activation " + value.getTreecapitatorActivation().name().toLowerCase()
                + " | color " + colorHex(value.getTreecapitatorOutlineColor())
                + " | glow " + value.getTreecapitatorOutlineBrightness() + "%"));
        player.sendSystemMessage(Component.literal(" Veinminer: " + value.isVeinminerEnabled()
                + " | activation " + value.getVeinminerActivation().name().toLowerCase()
                + " | color " + colorHex(value.getVeinminerOutlineColor())
                + " | glow " + value.getVeinminerOutlineBrightness() + "%"));
        player.sendSystemMessage(Component.literal(" Entity Insight: " + value.isEntityInsightEnabled()
                + " | health " + value.isEntityInsightShowHealth()
                + " | range " + value.getEntityInsightRange() + " blocks"
                + " | max entities " + value.getEntityInsightMaxEntities()));
        player.sendSystemMessage(Component.literal(" Auto-delete claimed private attachment mail: "
                + value.isMailAutoDeletePlayerAttachments()));
        player.sendSystemMessage(Component.literal(" Auto-delete claimed server attachment mail: "
                + value.isMailAutoDeleteSystemAttachments()));
        player.sendSystemMessage(Component.literal(" Auto-delete claimed auction attachment mail: "
                + value.isMailAutoDeleteAuctionAttachments()));
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

    private static int setWorldMapClaimOverlay(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setWorldMapShowClaims(enabled), "World-map claim overlay: " + enabled);
    }

    private static int setWorldMapRegionOverlay(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setWorldMapShowRegions(enabled), "World-map region overlay: " + enabled);
    }

    private static int setTreecapitatorEnabled(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = player(source);
        if (enabled && !Config.ENABLE_TREECAPITATOR.get()) {
            player.sendSystemMessage(Component.literal("Treecapitator is disabled by the server."));
            return 0;
        }
        if (enabled && !PermissionService.getBoolean(player, PermissionKeys.TREECAPITATOR_USE, true)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use Treecapitator."));
            return 0;
        }
        return update(source, value -> value.setTreecapitatorEnabled(enabled), "Treecapitator enabled: " + enabled);
    }

    private static int setTreecapitatorActivation(CommandSourceStack source, String raw) {
        MiningActivationMode mode = parseActivation(source, raw);
        return mode == null ? 0 : update(source, value -> value.setTreecapitatorActivation(mode),
                "Treecapitator activation: " + mode.name().toLowerCase());
    }

    private static int setTreecapitatorColor(CommandSourceStack source, String raw) {
        Integer color = parseColor(source, raw);
        return color == null ? 0 : update(source, value -> value.setTreecapitatorOutlineColor(color),
                "Treecapitator outline color: " + colorHex(color));
    }

    private static int setTreecapitatorBrightness(CommandSourceStack source, int percent) {
        return update(source, value -> value.setTreecapitatorOutlineBrightness(percent),
                "Treecapitator outline brightness: " + percent + "%");
    }

    private static int setVeinminerEnabled(CommandSourceStack source, boolean enabled) {
        ServerPlayer player = player(source);
        if (enabled && !Config.ENABLE_VEINMINER.get()) {
            player.sendSystemMessage(Component.literal("Veinminer is disabled by the server."));
            return 0;
        }
        if (enabled && !PermissionService.getBoolean(player, PermissionKeys.VEINMINER_USE, true)) {
            player.sendSystemMessage(Component.literal("You do not have permission to use Veinminer."));
            return 0;
        }
        return update(source, value -> value.setVeinminerEnabled(enabled), "Veinminer enabled: " + enabled);
    }

    private static int setVeinminerActivation(CommandSourceStack source, String raw) {
        MiningActivationMode mode = parseActivation(source, raw);
        return mode == null ? 0 : update(source, value -> value.setVeinminerActivation(mode),
                "Veinminer activation: " + mode.name().toLowerCase());
    }

    private static int setVeinminerColor(CommandSourceStack source, String raw) {
        Integer color = parseColor(source, raw);
        return color == null ? 0 : update(source, value -> value.setVeinminerOutlineColor(color),
                "Veinminer outline color: " + colorHex(color));
    }

    private static int setVeinminerBrightness(CommandSourceStack source, int percent) {
        return update(source, value -> value.setVeinminerOutlineBrightness(percent),
                "Veinminer outline brightness: " + percent + "%");
    }

    private static MiningActivationMode parseActivation(CommandSourceStack source, String raw) {
        try {
            return MiningActivationMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Activation must be sneak or keybind."));
            return null;
        }
    }

    private static Integer parseColor(CommandSourceStack source, String raw) {
        try {
            String value = raw.trim();
            if (value.startsWith("#")) value = value.substring(1);
            if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
            if (value.length() != 6) throw new IllegalArgumentException();
            return (int) (0xFF000000L | Long.parseUnsignedLong(value, 16));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Color must be a six-digit RGB value, for example 55FF77."));
            return null;
        }
    }

    private static String colorHex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    private static int setMailAutoDeletePlayer(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setMailAutoDeletePlayerAttachments(enabled),
                "Auto-delete claimed private attachment mail: " + enabled);
    }

    private static int setMailAutoDeleteSystem(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setMailAutoDeleteSystemAttachments(enabled),
                "Auto-delete claimed server attachment mail: " + enabled);
    }

    private static int setMailAutoDeleteAuction(CommandSourceStack source, boolean enabled) {
        return update(source, value -> value.setMailAutoDeleteAuctionAttachments(enabled),
                "Auto-delete claimed auction attachment mail: " + enabled);
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
        be.winnetrie.mod.simpleserverutilities.identity.EntityInsightService.sync(player);
        return 1;
    }

    private static ServerPlayer player(CommandSourceStack source) {
        return (ServerPlayer) source.getEntity();
    }
}
