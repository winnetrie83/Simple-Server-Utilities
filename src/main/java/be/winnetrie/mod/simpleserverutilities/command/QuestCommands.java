package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import be.winnetrie.mod.simpleserverutilities.quest.QuestEditorService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player and administrator commands for the independent Quest Core. */
public final class QuestCommands {
    private QuestCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("quest")
                .then(Commands.literal("open").executes(context -> open(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("edit")
                        .executes(context -> edit(context.getSource(), ""))
                        .then(Commands.argument("quest", StringArgumentType.word())
                                .executes(context -> edit(context.getSource(), StringArgumentType.getString(context, "quest")))));
    }

    private static int open(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by a player.")); return 0;
        }
        SimpleServerUtilities.QUESTS.openFromMenu(player); return 1;
    }

    private static int status(CommandSourceStack source) {
        if (!requireModule(source)) return 0;
        if (!canAdmin(source)) { source.sendFailure(Component.literal("Quest administrator permission is required.")); return 0; }
        var snapshot=SimpleServerUtilities.QUESTS.snapshot();
        source.sendSystemMessage(Component.literal("SSU Quest Core: definitions="+snapshot.definitions()+", journals="+snapshot.journals()+", active="+snapshot.active()+", ready="+snapshot.readyToTurnIn()+", completions="+snapshot.completions()));
        return 1;
    }

    private static int edit(CommandSourceStack source,String questId) {
        if (!requireModule(source)) return 0;
        if (!(source.getEntity() instanceof ServerPlayer player)) { source.sendFailure(Component.literal("This command can only be used by a player.")); return 0; }
        if (!PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false)) { source.sendFailure(Component.literal("Quest administrator permission is required.")); return 0; }
        QuestEditorService.open(player,questId);return 1;
    }

    private static boolean canAdmin(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player)
                || PermissionService.getBoolean(player, PermissionKeys.QUESTS_ADMIN, false);
    }

    private static boolean requireModule(CommandSourceStack source) {
        if (be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("quests")) return true;
        source.sendFailure(Component.literal("Quests is disabled or blocked by a required dependency."));
        return false;
    }
}
