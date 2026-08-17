package be.winnetrie.mod.simpleserverutilities.region;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJob;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobLocks;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Periodic and manual reset executor for server regions. */
public final class RegionResetScheduler {
    private static final long MAX_OPERATION_VOLUME = 1_000_000L;
    private static final Set<String> ACTIVE = new HashSet<>();

    private RegionResetScheduler() {
    }

    public static synchronized void clearRuntime() {
        ACTIVE.clear();
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Region region : SimpleServerUtilities.REGIONS.getAll()) {
            RegionResetSettings settings = region.getResetSettings();
            if (!settings.isEnabled() || settings.getNextResetAt() <= 0L || settings.getNextResetAt() > now) continue;
            if (settings.isOnlyWhenEmpty() && containsPlayer(server, region)) continue;
            trigger(server, region, null, false);
        }
    }

    public static Result triggerNow(ServerPlayer actor, Region region) {
        if (actor == null || region == null) return Result.fail("Region not found.");
        return trigger(actor.level().getServer(), region, actor, true);
    }

    private static synchronized Result trigger(MinecraftServer server, Region region, ServerPlayer actor, boolean manual) {
        String key = region.getName().toLowerCase(Locale.ROOT);
        if (ACTIVE.contains(key)) return Result.fail("A reset is already running for this region.");
        if (SsuModuleAccess.active("minigames") && SimpleServerUtilities.MINIGAMES.isManagedArenaRegion(region.getName())) {
            return Result.fail("Minigame-owned regions must be reset by the minigame system.");
        }
        String lock = SsuJobLocks.region(region.getDimension(), region.getName());
        if (SimpleServerUtilities.JOBS.isResourceLocked(lock)) {
            return Result.fail("The region is currently locked by another snapshot or world-edit job.");
        }
        if (SimpleServerUtilities.REGION_RENT_JOURNAL.hasPendingForRegion(region.getName())) {
            return Result.fail("A rental transaction is still being recovered for this region.");
        }
        RegionResetSettings settings = region.getResetSettings();
        if (!manual && settings.isOnlyWhenEmpty() && containsPlayer(server, region)) {
            return Result.fail("The scheduled reset is waiting until the region is empty.");
        }
        ServerLevel level = server.getLevel(region.getDimension());
        if (level == null) return postpone(region, "The region dimension is not loaded.");

        final SsuJob job;
        try {
            if (settings.getMode() == RegionResetMode.PRESET) {
                if (settings.getWeightedPreset().isBlank()) {
                    return postpone(region, "No block preset has been configured.");
                }
                job = RegionWorldEditManager.createFillJob(level, region, settings.getWeightedPreset(),
                        MAX_OPERATION_VOLUME, true);
            } else {
                if (!SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
                    return postpone(region, "No saved snapshot exists for this region.");
                }
                job = SimpleServerUtilities.REGION_SNAPSHOTS.createResetJob(level, region);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            return postpone(region, exception.getMessage());
        }

        ACTIVE.add(key);
        java.util.UUID actorId = actor == null ? null : actor.getUUID();
        var jobId = SimpleServerUtilities.JOBS.submit(job, completed -> server.execute(() -> {
            synchronized (RegionResetScheduler.class) {
                ACTIVE.remove(key);
            }
            long completedAt = System.currentTimeMillis();
            if (completed.status() == be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler.Status.COMPLETED) {
                settings.setLastResetAt(completedAt);
                settings.scheduleFrom(completedAt);
                SimpleServerUtilities.REGIONS.save();
                ServerPlayer online = actorId == null ? null : server.getPlayerList().getPlayer(actorId);
                if (online != null) online.sendSystemMessage(Component.literal(
                        "Region '" + region.getName() + "' reset completed."));
            } else {
                settings.setNextResetAt(completedAt + Math.min(settings.getIntervalSeconds(), 60L) * 1_000L);
                SimpleServerUtilities.REGIONS.save();
                ServerPlayer online = actorId == null ? null : server.getPlayerList().getPlayer(actorId);
                if (online != null) online.sendSystemMessage(Component.literal("Region reset "
                        + completed.status().name().toLowerCase(Locale.ROOT)
                        + (completed.error() == null || completed.error().isBlank() ? "." : ": " + completed.error())));
            }
        }));
        return Result.ok("Region reset scheduled as job " + jobId + ".");
    }

    private static Result postpone(Region region, String message) {
        RegionResetSettings settings = region.getResetSettings();
        if (settings.isEnabled()) {
            settings.setNextResetAt(System.currentTimeMillis()
                    + Math.min(settings.getIntervalSeconds(), 60L) * 1_000L);
            SimpleServerUtilities.REGIONS.save();
        }
        return Result.fail(message == null || message.isBlank() ? "The reset could not be scheduled." : message);
    }

    private static boolean containsPlayer(MinecraftServer server, Region region) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (region.contains(player.level().dimension(), player.blockPosition())) return true;
        }
        return false;
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }
}
