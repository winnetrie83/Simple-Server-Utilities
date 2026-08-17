package be.winnetrie.mod.simpleserverutilities.command;

import java.net.URI;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramDefinition;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramScoreboardMode;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class HologramCommands {
    private HologramCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("hologram")
                .requires(HologramCommands::canAdmin)
                .executes(context -> list(context.getSource()))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("refresh").executes(context -> refresh(context.getSource())))
                .then(Commands.literal("create")
                        .then(Commands.literal("text")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> createText(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "text")
                                                )))))
                        .then(Commands.literal("link")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("url", StringArgumentType.string())
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(context -> createLink(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "id"),
                                                                StringArgumentType.getString(context, "url"),
                                                                StringArgumentType.getString(context, "text")
                                                        ))))))
                        .then(Commands.literal("scoreboard")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("objective", StringArgumentType.word())
                                                .then(Commands.literal("top")
                                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                                .executes(context -> createScoreboard(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "objective"),
                                                                        HologramScoreboardMode.TOP,
                                                                        StringArgumentType.getString(context, "title")
                                                                ))))
                                                .then(Commands.literal("self")
                                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                                .executes(context -> createScoreboard(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "id"),
                                                                        StringArgumentType.getString(context, "objective"),
                                                                        HologramScoreboardMode.SELF,
                                                                        StringArgumentType.getString(context, "title")
                                                                )))))))
                        .then(Commands.literal("image")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("source", StringArgumentType.greedyString())
                                                .executes(context -> createImage(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "source")
                                                ))))))
                .then(Commands.literal("edit")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.literal("text")
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(context -> editText(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "text")))))
                                .then(Commands.literal("link")
                                        .then(Commands.argument("url", StringArgumentType.string())
                                                .executes(context -> editLink(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "url")))))
                                .then(Commands.literal("image_source")
                                        .then(Commands.argument("source", StringArgumentType.greedyString())
                                                .executes(context -> editImageSource(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "source")))))
                                .then(Commands.literal("image_size")
                                        .then(Commands.argument("width", DoubleArgumentType.doubleArg(0.1D, 32.0D))
                                                .then(Commands.argument("height", DoubleArgumentType.doubleArg(0.1D, 32.0D))
                                                        .executes(context -> editImageSize(context.getSource(),
                                                                StringArgumentType.getString(context, "id"),
                                                                DoubleArgumentType.getDouble(context, "width"),
                                                                DoubleArgumentType.getDouble(context, "height"))))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> delete(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("movehere")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> moveHere(context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("style")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.literal("color")
                                        .then(Commands.argument("hex", StringArgumentType.word())
                                                .executes(context -> setColor(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "hex")))))
                                .then(booleanStyle("bold"))
                                .then(booleanStyle("italic"))
                                .then(booleanStyle("underlined"))
                                .then(booleanStyle("strikethrough"))
                                .then(booleanStyle("see_through"))
                                .then(booleanStyle("enabled"))
                                .then(Commands.literal("scale")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0D, 8.0D))
                                                .executes(context -> setScale(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        DoubleArgumentType.getDouble(context, "value")))))
                                .then(Commands.literal("view_distance")
                                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(4.0D, 512.0D))
                                                .executes(context -> setViewDistance(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        DoubleArgumentType.getDouble(context, "blocks")))))))
                .then(Commands.literal("scoreboard")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.literal("max_lines")
                                        .then(Commands.argument("lines", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> setMaxLines(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "lines")))))
                                .then(Commands.literal("interval")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(10, 72000))
                                                .executes(context -> setInterval(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "ticks")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> booleanStyle(String property) {
        return Commands.literal(property)
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> setBooleanStyle(
                                context.getSource(),
                                StringArgumentType.getString(context, "id"),
                                property,
                                BoolArgumentType.getBool(context, "value")
                        )));
    }

    private static boolean canAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player)
                || PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false);
    }

    private static int list(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        source.sendSystemMessage(Component.literal("SSU holograms (" + SimpleServerUtilities.HOLOGRAMS.all().size() + "):"));
        for (HologramDefinition definition : SimpleServerUtilities.HOLOGRAMS.all()) {
            source.sendSystemMessage(Component.literal(" - " + definition.id + " [" + definition.type.name().toLowerCase()
                    + "] " + definition.dimension + " @ " + one(definition.x) + ", " + one(definition.y) + ", " + one(definition.z)));
        }
        return 1;
    }

    private static int refresh(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        SimpleServerUtilities.HOLOGRAMS.syncAll();
        source.sendSystemMessage(Component.literal("Holograms refreshed for all players."));
        return 1;
    }

    private static int createText(CommandSourceStack source, String id, String text) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = atSource(source, id, HologramType.TEXT);
        if (value == null) return 0;
        value.text = text;
        return save(source, value, "Text hologram created");
    }

    private static int createLink(CommandSourceStack source, String id, String url, String text) {
        if (!requireModule(source)) return 0;
        if (!validWebsite(url)) {
            source.sendFailure(Component.literal("Only valid http/https website links are accepted."));
            return 0;
        }
        HologramDefinition value = atSource(source, id, HologramType.LINK);
        if (value == null) return 0;
        value.url = url;
        value.text = "§n" + text + "§r";
        value.color = 0xFF55AAFF;
        return save(source, value, "Clickable link hologram created");
    }

    private static int createScoreboard(CommandSourceStack source, String id, String objective,
                                        HologramScoreboardMode mode, String title) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = atSource(source, id, HologramType.SCOREBOARD);
        if (value == null) return 0;
        value.objective = objective;
        value.scoreboardMode = mode;
        value.text = title;
        return save(source, value, "Scoreboard hologram created");
    }

    private static int createImage(CommandSourceStack source, String id, String imageSource) {
        if (!requireModule(source)) return 0;
        if (!validateImageSource(source, imageSource)) {
            return 0;
        }
        HologramDefinition value = atSource(source, id, HologramType.IMAGE);
        if (value == null) return 0;
        value.imageSource = imageSource;
        return save(source, value, "Image hologram definition created");
    }

    private static int editText(CommandSourceStack source, String id, String text) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = require(source, id);
        if (value == null) return 0;
        value.text = text;
        return save(source, value, "Hologram text updated");
    }

    private static int editLink(CommandSourceStack source, String id, String url) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = requireType(source, id, HologramType.LINK);
        if (value == null) return 0;
        if (!validWebsite(url)) {
            source.sendFailure(Component.literal("Only valid http/https website links are accepted."));
            return 0;
        }
        value.url = url;
        return save(source, value, "Hologram link updated");
    }

    private static int editImageSource(CommandSourceStack source, String id, String imageSource) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = requireType(source, id, HologramType.IMAGE);
        if (value == null || !validateImageSource(source, imageSource)) return 0;
        value.imageSource = imageSource;
        return save(source, value, "Hologram image source updated");
    }

    private static int editImageSize(CommandSourceStack source, String id, double width, double height) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = requireType(source, id, HologramType.IMAGE);
        if (value == null) return 0;
        value.imageWidth = (float) width;
        value.imageHeight = (float) height;
        return save(source, value, "Hologram image size updated");
    }

    private static int delete(CommandSourceStack source, String id) {
        if (!requireModule(source)) return 0;
        if (!SimpleServerUtilities.HOLOGRAMS.delete(id)) {
            source.sendFailure(Component.literal("Unknown hologram: " + id));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Deleted hologram " + id + "."));
        return 1;
    }

    private static int moveHere(CommandSourceStack source, String id) {
        if (!requireModule(source)) return 0;
        ServerPlayer player = requirePlayer(source);
        if (player == null) return 0;
        HologramDefinition value = require(source, id);
        if (value == null) return 0;
        value.dimension = player.level().dimension().identifier().toString();
        value.x = player.getX();
        value.y = player.getY() + 1.8D;
        value.z = player.getZ();
        return save(source, value, "Hologram moved");
    }

    private static int setColor(CommandSourceStack source, String id, String raw) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = require(source, id);
        if (value == null) return 0;
        try {
            String hex = raw.trim().replace("#", "").replace("0x", "");
            if (hex.length() != 6 && hex.length() != 8) throw new NumberFormatException();
            long parsed = Long.parseUnsignedLong(hex, 16);
            value.color = hex.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
            return save(source, value, "Hologram color updated");
        } catch (NumberFormatException exception) {
            source.sendFailure(Component.literal("Use a six-digit RGB or eight-digit ARGB hex color."));
            return 0;
        }
    }

    private static int setBooleanStyle(CommandSourceStack source, String id, String property, boolean enabled) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = require(source, id);
        if (value == null) return 0;
        switch (property) {
            case "bold" -> value.text = setWholeTextFormat(value.text, 'l', enabled);
            case "italic" -> value.text = setWholeTextFormat(value.text, 'o', enabled);
            case "underlined" -> value.text = setWholeTextFormat(value.text, 'n', enabled);
            case "strikethrough" -> value.text = setWholeTextFormat(value.text, 'm', enabled);
            case "see_through" -> value.seeThrough = enabled;
            case "enabled" -> value.enabled = enabled;
            default -> { return 0; }
        }
        return save(source, value, "Hologram style updated");
    }

    private static String setWholeTextFormat(String text, char code, boolean enabled) {
        String safe = text == null ? "" : text;
        String marker = "§" + code;
        if (enabled) return marker + safe + "§r";
        return safe.replace(marker, "").replace("§" + Character.toUpperCase(code), "");
    }

    private static int setScale(CommandSourceStack source, String id, double scale) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = require(source, id);
        if (value == null) return 0;
        value.scale = (float) scale;
        return save(source, value, "Hologram scale updated");
    }

    private static int setViewDistance(CommandSourceStack source, String id, double distance) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = require(source, id);
        if (value == null) return 0;
        value.viewDistance = distance;
        return save(source, value, "Hologram view distance updated");
    }

    private static int setMaxLines(CommandSourceStack source, String id, int lines) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = requireScoreboard(source, id);
        if (value == null) return 0;
        value.maxLines = lines;
        return save(source, value, "Scoreboard maximum lines updated");
    }

    private static int setInterval(CommandSourceStack source, String id, int ticks) {
        if (!requireModule(source)) return 0;
        HologramDefinition value = requireScoreboard(source, id);
        if (value == null) return 0;
        value.updateIntervalTicks = ticks;
        return save(source, value, "Scoreboard update interval updated");
    }

    private static HologramDefinition atSource(CommandSourceStack source, String id, HologramType type) {
        ServerPlayer player = requirePlayer(source);
        if (player == null) return null;
        HologramDefinition value = new HologramDefinition();
        value.id = id;
        value.type = type;
        value.dimension = player.level().dimension().identifier().toString();
        value.x = player.getX();
        value.y = player.getY() + 1.8D;
        value.z = player.getZ();
        return value;
    }

    private static HologramDefinition require(CommandSourceStack source, String id) {
        HologramDefinition value = SimpleServerUtilities.HOLOGRAMS.get(id);
        if (value == null) source.sendFailure(Component.literal("Unknown hologram: " + id));
        return value;
    }

    private static HologramDefinition requireType(CommandSourceStack source, String id, HologramType type) {
        HologramDefinition value = require(source, id);
        if (value != null && value.type != type) {
            source.sendFailure(Component.literal("Hologram " + id + " is not a " + type.name().toLowerCase() + "."));
            return null;
        }
        return value;
    }

    private static HologramDefinition requireScoreboard(CommandSourceStack source, String id) {
        HologramDefinition value = require(source, id);
        if (value != null && value.type != HologramType.SCOREBOARD) {
            source.sendFailure(Component.literal("Hologram " + id + " is not a scoreboard."));
            return null;
        }
        return value;
    }

    private static int save(CommandSourceStack source, HologramDefinition value, String message) {
        if (!requireModule(source)) return 0;
        if (!SimpleServerUtilities.HOLOGRAMS.put(value)) {
            source.sendFailure(Component.literal("Hologram could not be saved; the server limit of "
                    + be.winnetrie.mod.simpleserverutilities.hologram.HologramManager.MAX_HOLOGRAMS + " was reached."));
            return 0;
        }
        source.sendSystemMessage(Component.literal(message + ": " + value.id + "."));
        return 1;
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) return player;
        source.sendFailure(Component.literal("This hologram command must be run by a player."));
        return null;
    }

    private static boolean validateImageSource(CommandSourceStack source, String raw) {
        if (isRemote(raw)) {
            if (!validWebsite(raw)) {
                source.sendFailure(Component.literal("Remote images require a valid http/https URL."));
                return false;
            }
            if (!Config.ALLOW_REMOTE_HOLOGRAM_IMAGES.get()) {
                source.sendFailure(Component.literal("Remote hologram images are disabled in the server config."));
                return false;
            }
            return true;
        }
        try {
            net.minecraft.resources.Identifier identifier = net.minecraft.resources.Identifier.parse(raw);
            String path = identifier.getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.endsWith(".png") || path.endsWith(".gif")
                    || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                return true;
            }
            source.sendFailure(Component.literal("Internal hologram images must be PNG, GIF or JPG resources."));
            return false;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Internal images require a resource identifier such as simpleserverutilities:textures/holograms/example.png."));
            return false;
        }
    }

    private static boolean validWebsite(String raw) {
        try {
            URI uri = URI.create(raw);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean isRemote(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        return value.startsWith("https://") || value.startsWith("http://");
    }

    private static String one(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static boolean requireModule(CommandSourceStack source) {
        if (be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("holograms")) return true;
        source.sendFailure(Component.literal("Holograms is disabled or blocked by a required dependency."));
        return false;
    }
}
