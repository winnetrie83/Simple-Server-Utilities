package be.winnetrie.mod.simpleserverutilities.command;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Runtime administration for utility-mining modules and custom block allow/deny lists. */
public final class UtilityMiningCommands {
    private UtilityMiningCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("utilitymining")
                .requires(UtilityMiningCommands::canAdmin)
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("tree")
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setBoolean(context.getSource(), Config.ENABLE_TREECAPITATOR,
                                                BoolArgumentType.getBool(context, "enabled"), "Treecapitator"))))
                        .then(Commands.literal("leaf_range")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 16))
                                        .executes(context -> setInt(context.getSource(), Config.TREECAPITATOR_LEAF_SEARCH_RANGE,
                                                IntegerArgumentType.getInteger(context, "blocks"), "Tree leaf search range"))))
                        .then(Commands.literal("break_leaves")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setBoolean(context.getSource(), Config.TREECAPITATOR_BREAK_NATURAL_LEAVES,
                                                BoolArgumentType.getBool(context, "enabled"), "Instant natural leaves"))))
                        .then(Commands.literal("default_max")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 2048))
                                        .executes(context -> setInt(context.getSource(), Config.TREECAPITATOR_DEFAULT_MAX_BLOCKS,
                                                IntegerArgumentType.getInteger(context, "blocks"), "Tree default maximum"))))
                        .then(blockList("custom_logs", Config.TREECAPITATOR_CUSTOM_LOG_BLOCKS))
                        .then(blockList("disabled_logs", Config.TREECAPITATOR_DISABLED_LOG_BLOCKS)))
                .then(Commands.literal("vein")
                        .then(Commands.literal("enabled")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(context -> setBoolean(context.getSource(), Config.ENABLE_VEINMINER,
                                                BoolArgumentType.getBool(context, "enabled"), "Veinminer"))))
                        .then(Commands.literal("default_max")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 2048))
                                        .executes(context -> setInt(context.getSource(), Config.VEINMINER_DEFAULT_MAX_BLOCKS,
                                                IntegerArgumentType.getInteger(context, "blocks"), "Vein default maximum"))))
                        .then(blockList("custom_ores", Config.VEINMINER_CUSTOM_ORE_BLOCKS))
                        .then(blockList("disabled_ores", Config.VEINMINER_DISABLED_ORE_BLOCKS)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> blockList(
            String literal,
            ModConfigSpec.ConfigValue<String> config
    ) {
        return Commands.literal(literal)
                .executes(context -> showList(context.getSource(), literal, config))
                .then(Commands.literal("add")
                        .then(Commands.argument("block", StringArgumentType.word())
                                .executes(context -> mutateList(context.getSource(), literal, config,
                                        StringArgumentType.getString(context, "block"), true))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("block", StringArgumentType.word())
                                .executes(context -> mutateList(context.getSource(), literal, config,
                                        StringArgumentType.getString(context, "block"), false))))
                .then(Commands.literal("clear")
                        .executes(context -> clearList(context.getSource(), literal, config)));
    }

    private static boolean canAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player)
                || PermissionService.getBoolean(player, PermissionKeys.UTILITY_MINING_ADMIN, false);
    }

    private static int status(CommandSourceStack source) {
        source.sendSystemMessage(Component.literal("SSU utility mining:"));
        source.sendSystemMessage(Component.literal(" Treecapitator: " + Config.ENABLE_TREECAPITATOR.get()
                + " | natural leaves " + Config.TREECAPITATOR_BREAK_NATURAL_LEAVES.get()
                + " | leaf range " + Config.TREECAPITATOR_LEAF_SEARCH_RANGE.get()
                + " | default max " + Config.TREECAPITATOR_DEFAULT_MAX_BLOCKS.get()));
        source.sendSystemMessage(Component.literal(" Veinminer: " + Config.ENABLE_VEINMINER.get()
                + " | default max " + Config.VEINMINER_DEFAULT_MAX_BLOCKS.get()));
        source.sendSystemMessage(Component.literal(" Custom logs: " + display(Config.TREECAPITATOR_CUSTOM_LOG_BLOCKS.get())));
        source.sendSystemMessage(Component.literal(" Disabled logs: " + display(Config.TREECAPITATOR_DISABLED_LOG_BLOCKS.get())));
        source.sendSystemMessage(Component.literal(" Custom ores: " + display(Config.VEINMINER_CUSTOM_ORE_BLOCKS.get())));
        source.sendSystemMessage(Component.literal(" Disabled ores: " + display(Config.VEINMINER_DISABLED_ORE_BLOCKS.get())));
        return 1;
    }

    private static int setBoolean(
            CommandSourceStack source,
            ModConfigSpec.BooleanValue setting,
            boolean value,
            String label
    ) {
        setting.set(value);
        source.sendSystemMessage(Component.literal(label + ": " + value));
        return 1;
    }

    private static int setInt(
            CommandSourceStack source,
            ModConfigSpec.IntValue setting,
            int value,
            String label
    ) {
        setting.set(value);
        source.sendSystemMessage(Component.literal(label + ": " + value));
        return 1;
    }

    private static int showList(
            CommandSourceStack source,
            String label,
            ModConfigSpec.ConfigValue<String> setting
    ) {
        source.sendSystemMessage(Component.literal(label + ": " + display(setting.get())));
        return 1;
    }

    private static int mutateList(
            CommandSourceStack source,
            String label,
            ModConfigSpec.ConfigValue<String> setting,
            String rawBlock,
            boolean add
    ) {
        final String block;
        try {
            block = Identifier.parse(rawBlock).toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            source.sendFailure(Component.literal("Invalid block identifier: " + rawBlock));
            return 0;
        }

        Set<String> values = parse(setting.get());
        boolean changed = add ? values.add(block) : values.remove(block);
        setting.set(String.join(",", values));
        source.sendSystemMessage(Component.literal(label + (changed ? " updated: " : " unchanged: ") + display(setting.get())));
        return 1;
    }

    private static int clearList(
            CommandSourceStack source,
            String label,
            ModConfigSpec.ConfigValue<String> setting
    ) {
        setting.set("");
        source.sendSystemMessage(Component.literal(label + " cleared."));
        return 1;
    }

    private static Set<String> parse(String raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return values;
        for (String value : raw.split(",")) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) values.add(normalized);
        }
        return values;
    }

    private static String display(String raw) {
        return raw == null || raw.isBlank() ? "(none)" : raw;
    }
}
