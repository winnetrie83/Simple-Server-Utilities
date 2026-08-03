package be.winnetrie.mod.simpleserverutilities.command;

import java.util.Locale;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.JobSnapshot;
import be.winnetrie.mod.simpleserverutilities.core.performance.RegionSpatialIndex;
import be.winnetrie.mod.simpleserverutilities.core.performance.SsuPerformanceMonitor;
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
                .then(Commands.literal("performance")
                        .executes(context -> performance(context.getSource()))
                        .then(Commands.literal("reset")
                                .executes(context -> resetPerformance(context.getSource()))))
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
                        + ", immutableTasks=" + storage.activeTasks()
        ));
        source.sendSystemMessage(Component.literal(" - Active jobs: " + SimpleServerUtilities.JOBS.size()));
        return 1;
    }


    private static int performance(CommandSourceStack source) {
        if (!canManage(source)) {
            source.sendFailure(Component.literal("You do not have permission to view SSU performance data."));
            return 0;
        }

        SsuPerformanceMonitor.Snapshot performance = SimpleServerUtilities.PERFORMANCE.snapshot();
        RegionSpatialIndex.Statistics index = SimpleServerUtilities.REGIONS.spatialIndexStatistics();
        StorageStatistics storage = SimpleServerUtilities.STORAGE.statistics();

        source.sendSystemMessage(Component.literal("SSU performance:"));
        source.sendSystemMessage(Component.literal(String.format(
                Locale.ROOT,
                " - Regions: %d lookup(s), %.2f candidate(s)/lookup, %d fallback(s)",
                performance.regionLookups(),
                performance.averageRegionCandidates(),
                performance.regionIndexFallbacks()
        )));
        source.sendSystemMessage(Component.literal(
                " - Region index: " + index.regions() + " region(s), "
                        + index.cells() + " cell(s), " + index.references() + " reference(s), "
                        + index.largeRegions() + " overflow region(s), max bucket " + index.maxBucketSize()
        ));
        source.sendSystemMessage(Component.literal(String.format(
                Locale.ROOT,
                " - Permissions: %d check(s), %.1f%% cache hit rate (%d hit / %d miss), %d cached result(s)",
                performance.permissionChecks(),
                performance.permissionCacheHitRate() * 100.0D,
                performance.permissionCacheHits(),
                performance.permissionCacheMisses(),
                SimpleServerUtilities.PERMISSIONS.cachedResolutionCount()
        )));
        source.sendSystemMessage(Component.literal(String.format(
                Locale.ROOT,
                " - Jobs: %d completed, %d cancelled, %d failed, %.2f ms average runtime",
                performance.jobsCompleted(),
                performance.jobsCancelled(),
                performance.jobsFailed(),
                performance.averageJobRuntimeMillis()
        )));
        source.sendSystemMessage(Component.literal(
                " - Storage: pending=" + storage.pending()
                        + ", completed=" + storage.completed()
                        + ", coalesced=" + storage.coalesced()
                        + ", failed=" + storage.failed()
        ));
        var npcRuntime = SimpleServerUtilities.NPCS.runtimeStatistics();
        source.sendSystemMessage(Component.literal(
                " - NPCs: " + npcRuntime.placements() + " placement(s), "
                        + npcRuntime.staticPhysicsPlacements() + " gravity, "
                        + npcRuntime.scheduledPlacements() + " scheduled, "
                        + npcRuntime.relationPlacements() + " relation/combat"
        ));
        var hologramIndex = SimpleServerUtilities.HOLOGRAMS.spatialStatistics();
        source.sendSystemMessage(Component.literal(
                " - Holograms: " + hologramIndex.holograms() + " definition(s), "
                        + hologramIndex.cells() + " indexed cell(s), max bucket "
                        + hologramIndex.maximumBucketSize()
        ));
        var moduleTimings = SimpleServerUtilities.PERFORMANCE.moduleTimingSnapshot();
        if (!moduleTimings.isEmpty()) {
            source.sendSystemMessage(Component.literal(" - Timed subsystems (rolling 256 samples):"));
            for (var timing : moduleTimings) {
                source.sendSystemMessage(Component.literal(String.format(
                        Locale.ROOT,
                        "   %s: avg %.3f ms, p95 %.3f ms, max %.3f ms, last %.3f ms (%d total)",
                        timing.module(),
                        timing.rollingAverageMillis(),
                        timing.p95Millis(),
                        timing.maximumMillis(),
                        timing.lastMillis(),
                        timing.totalSamples()
                )));
            }
        }
        return 1;
    }

    private static int resetPerformance(CommandSourceStack source) {
        if (!canManage(source)) {
            source.sendFailure(Component.literal("You do not have permission to reset SSU performance counters."));
            return 0;
        }
        SimpleServerUtilities.PERFORMANCE.reset();
        source.sendSystemMessage(Component.literal("SSU performance counters were reset."));
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
