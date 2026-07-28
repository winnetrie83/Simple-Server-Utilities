package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.JobSnapshot;
import be.winnetrie.mod.simpleserverutilities.core.storage.BatchedStorageService.StorageStatistics;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CoreCommands {

    private CoreCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("core")
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("jobs")
                        .then(Commands.literal("list")
                                .executes(context -> listJobs(context.getSource())))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> cancelJob(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id")
                                        )))));
    }

    private static int status(CommandSourceStack source) {
        if (!canManage(source)) {
            source.sendFailure(Component.literal("You do not have permission to view SSU Core status."));
            return 0;
        }

        StorageStatistics storage = SimpleServerUtilities.STORAGE.statistics();
        source.sendSystemMessage(Component.literal("SSU Core status:"));
        source.sendSystemMessage(Component.literal(
                " - Storage: pending=" + storage.pending()
                        + ", queued=" + storage.queued()
                        + ", completed=" + storage.completed()
                        + ", coalesced=" + storage.coalesced()
                        + ", failed=" + storage.failed()
                        + ", retryRequired=" + storage.retryRequired()
        ));
        source.sendSystemMessage(Component.literal(" - Active jobs: " + SimpleServerUtilities.JOBS.size()));
        return 1;
    }

    private static int listJobs(CommandSourceStack source) {
        if (!canManage(source)) {
            source.sendFailure(Component.literal("You do not have permission to view SSU jobs."));
            return 0;
        }

        var jobs = SimpleServerUtilities.JOBS.snapshots();
        if (jobs.isEmpty()) {
            source.sendSystemMessage(Component.literal("No active SSU jobs."));
            return 1;
        }

        source.sendSystemMessage(Component.literal("Active SSU jobs:"));
        for (JobSnapshot job : jobs) {
            String progress = job.progress() < 0.0D
                    ? "unknown"
                    : String.format(Locale.ROOT, "%.1f%%", job.progress() * 100.0D);
            source.sendSystemMessage(Component.literal(
                    " - " + job.id() + " | " + progress + " | " + job.description()
            ));
        }
        return 1;
    }

    private static int cancelJob(CommandSourceStack source, String rawId) {
        if (!canManage(source)) {
            source.sendFailure(Component.literal("You do not have permission to cancel SSU jobs."));
            return 0;
        }

        try {
            UUID id = UUID.fromString(rawId);
            if (!SimpleServerUtilities.JOBS.cancel(id)) {
                source.sendFailure(Component.literal("No active SSU job found with id " + id + "."));
                return 0;
            }
            source.sendSystemMessage(Component.literal("Cancelled SSU job " + id + "."));
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Invalid job UUID: " + rawId));
            return 0;
        }
    }

    private static boolean canManage(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        return PermissionService.getBoolean(player, PermissionKeys.CORE_ADMIN, false);
    }
}
