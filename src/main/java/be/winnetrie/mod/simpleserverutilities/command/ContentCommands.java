package be.winnetrie.mod.simpleserverutilities.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.content.ContentAccessPolicy;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Read-only diagnostics for the first Content & Progression Core phase. */
public final class ContentCommands {
    private ContentCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("content")
                .then(Commands.literal("status").executes(context -> status(context.getSource())));
    }

    private static int status(CommandSourceStack source) {
        if (!canManage(source)) {
            source.sendFailure(Component.literal("You do not have permission to view SSU content status."));
            return 0;
        }
        var progress = SimpleServerUtilities.CONTENT_PROGRESS.snapshot();
        var events = SimpleServerUtilities.CONTENT_EVENTS.snapshot();
        source.sendSystemMessage(Component.literal("SSU Content & Progression Core:"));
        source.sendSystemMessage(Component.literal(
                " - Player records=" + progress.players()
                        + ", flags=" + progress.playerFlags()
                        + ", counters=" + progress.playerCounters()
                        + ", unlocks=" + progress.playerUnlocks()
                        + ", reputation=" + progress.reputationEntries()
                        + ", pending writes=" + progress.pendingWrites()));
        source.sendSystemMessage(Component.literal(
                " - Server flags=" + progress.serverFlags()
                        + ", counters=" + progress.serverCounters()
                        + ", unlocks=" + progress.serverUnlocks()));
        source.sendSystemMessage(Component.literal(
                " - Registered conditions=" + SimpleServerUtilities.CONTENT_CONDITIONS.registeredTypeCount()
                        + ", actions=" + SimpleServerUtilities.CONTENT_ACTIONS.registeredTypeCount()));
        source.sendSystemMessage(Component.literal(
                " - Events=" + events.publishedEvents()
                        + ", listener calls=" + events.listenerInvocations()
                        + ", failures=" + events.listenerFailures()
                        + ", subscriptions=" + events.subscriptions()));
        source.sendSystemMessage(Component.literal(
                " - Quest entry preference=" + ContentAccessPolicy.configuredQuestAccessMode().serializedName()
                        + ", effective=" + ContentAccessPolicy.effectiveQuestAccessMode().serializedName()));
        return 1;
    }

    private static boolean canManage(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        return PermissionService.getBoolean(player, PermissionKeys.CONTENT_ADMIN, false);
    }
}
