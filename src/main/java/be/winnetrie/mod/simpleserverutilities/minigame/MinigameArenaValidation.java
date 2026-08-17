package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server-side arena validation used by the setup wizard and diagnostics. */
public final class MinigameArenaValidation {
    public enum Severity { OK, WARNING, ERROR }

    public record Issue(Severity severity, String message, MinigameLocation location) {
        public Issue {
            severity = severity == null ? Severity.WARNING : severity;
            message = message == null ? "" : message.trim();
            location = location == null ? null : location.copy();
        }
    }

    public record Report(List<Issue> issues) {
        public Report { issues = issues == null ? List.of() : List.copyOf(issues); }
        public long errors() { return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count(); }
        public long warnings() { return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count(); }
        public boolean valid() { return errors() == 0L; }
    }

    private MinigameArenaValidation() {
    }

    public static Report validate(MinecraftServer server, MinigameDefinition definition,
                                  MinigameArenaDefinition arena) {
        ArrayList<Issue> issues = new ArrayList<>();
        if (server == null || definition == null || arena == null) {
            issues.add(new Issue(Severity.ERROR, "The minigame definition or arena is unavailable.", null));
            return new Report(issues);
        }
        Region region = SimpleServerUtilities.REGIONS.get(arena.regionId);
        if (arena.regionId == null || arena.regionId.isBlank() || region == null) {
            issues.add(new Issue(Severity.ERROR, "No valid arena region is linked.", arena.lobby));
        } else if (server.getLevel(region.getDimension()) == null) {
            issues.add(new Issue(Severity.ERROR, "The arena dimension is unavailable.", arena.lobby));
        }
        if (!arena.resetRegionAfterMatch) {
            issues.add(new Issue(Severity.WARNING, "Arena reset after a match is disabled.", arena.lobby));
        } else if (region != null && !SimpleServerUtilities.REGION_SNAPSHOTS.hasSnapshot(region.getName())) {
            issues.add(new Issue(Severity.ERROR, "The arena reset snapshot has not been saved.", arena.lobby));
        }

        checkLocation(server, issues, "Lobby", arena.lobby, region, false);
        checkLocation(server, issues, "Spectator spawn", arena.spectator, region, false);
        if (arena.spectatorBounds == null || !arena.spectatorBounds.configured()) {
            issues.add(new Issue(Severity.WARNING, "Spectator bounds are not configured.", arena.spectator));
        } else if (resolve(server, arena.spectatorBounds.dimension) == null) {
            issues.add(new Issue(Severity.ERROR, "Spectator bounds use an unavailable dimension.", arena.spectator));
        }

        int expectedTeams = Math.max(1, definition.teamCount);
        for (int team = 1; team <= expectedTeams; team++) {
            int count = 0;
            for (MinigameSpawnPoint spawn : arena.teamSpawns) {
                if (spawn.team != team) continue;
                count++;
                checkLocation(server, issues, "Team " + team + " spawn " + count, spawn.location, region, true);
            }
            if (count == 0) issues.add(new Issue(Severity.ERROR, "Team " + team + " has no spawn location.", arena.lobby));
        }

        MinigameGameType type = MinigameGameType.parse(definition.gameType);
        if (type == MinigameGameType.SPLEEF) {
            if (arena.playFloor == null || !arena.playFloor.configured()) {
                issues.add(new Issue(Severity.ERROR, "The Spleef floor is not configured.", arena.lobby));
            } else if (region != null && !boundsNearRegion(arena.playFloor, region, 0)) {
                issues.add(new Issue(Severity.ERROR, "The Spleef floor extends outside the arena region.", arena.lobby));
            }
        } else if (type == MinigameGameType.CAPTURE_THE_FLAG) {
            for (int team = 1; team <= 2; team++) {
                MinigameFlagPoint flag = arena.flagForTeam(team);
                if (flag == null) issues.add(new Issue(Severity.ERROR, "Team " + team + " has no flag point.", arena.lobby));
                else checkLocation(server, issues, "Team " + team + " flag", flag.location, region, true);
            }
            if (arena.flagPoints.size() >= 2) {
                double distance = distance(arena.flagPoints.get(0).location, arena.flagPoints.get(1).location);
                if (distance < 8.0D) issues.add(new Issue(Severity.WARNING,
                        "The two flags are only " + Math.round(distance) + " blocks apart.", arena.flagPoints.get(0).location));
            }
        } else if (type == MinigameGameType.DOMINATION) {
            if (arena.controlPoints.size() < 3) {
                issues.add(new Issue(Severity.ERROR, "Domination requires at least three control points.", arena.lobby));
            }
            for (MinigameControlPoint point : arena.controlPoints) {
                checkLocation(server, issues, "Node " + point.displayName, point.location, region, true);
                checkLocation(server, issues, "Node respawn " + point.displayName, point.respawn, region, true);
            }
        }

        MinigameBoostRules boosts = type == MinigameGameType.CAPTURE_THE_FLAG
                ? definition.captureTheFlag.boosts
                : type == MinigameGameType.DOMINATION ? definition.domination.boosts : null;
        if (boosts != null && boosts.enabled && !boosts.automatic()) {
            if (arena.boostSpawns.isEmpty()) {
                issues.add(new Issue(Severity.ERROR, "Manual boost mode is enabled but no boost locations exist.", arena.lobby));
            }
            for (int index = 0; index < arena.boostSpawns.size(); index++) {
                MinigameLocation location = arena.boostSpawns.get(index);
                checkBoostLocation(server, issues, "Boost spawn " + (index + 1), location, region);
            }
        }

        MinigameRoleRules roles = type == MinigameGameType.CAPTURE_THE_FLAG
                ? definition.captureTheFlag.roles
                : type == MinigameGameType.DOMINATION ? definition.domination.roles : null;
        if (roles != null && roles.enabled) {
            int smallestTeam = Math.max(1, definition.minPlayers / Math.max(1, definition.teamCount));
            int largestTeam = (definition.maxPlayers + definition.teamCount - 1) / definition.teamCount;
            if (roles.minimumTotalPerTeam() > smallestTeam) {
                issues.add(new Issue(Severity.ERROR, "Role minimums require more players than the smallest possible team.", arena.lobby));
            }
            if (roles.maximumTotalPerTeam() < largestTeam) {
                issues.add(new Issue(Severity.ERROR, "Role maximums cannot hold the largest possible team.", arena.lobby));
            }
        }

        if (issues.stream().noneMatch(issue -> issue.severity() != Severity.OK)) {
            issues.add(new Issue(Severity.OK, "Arena configuration is valid and ready for a test match.", arena.lobby));
        }
        return new Report(issues);
    }

    private static void checkLocation(MinecraftServer server, List<Issue> issues, String label,
                                      MinigameLocation location, Region region, boolean inside) {
        if (location == null || location.dimension == null || location.dimension.isBlank()) {
            issues.add(new Issue(Severity.ERROR, label + " is not configured.", location));
            return;
        }
        ServerLevel level = resolve(server, location.dimension);
        if (level == null) {
            issues.add(new Issue(Severity.ERROR, label + " uses an unavailable dimension.", location));
            return;
        }
        if (inside && region != null && !region.contains(level.dimension(), BlockPos.containing(location.x, location.y, location.z))) {
            issues.add(new Issue(Severity.ERROR, label + " lies outside the arena region.", location));
        }
        if (!safeStandingLocation(server, location)) {
            issues.add(new Issue(Severity.WARNING, label + " may place players inside a block or above an unsafe floor.", location));
        }
    }

    private static void checkBoostLocation(MinecraftServer server, List<Issue> issues, String label,
                                           MinigameLocation location, Region region) {
        if (location == null || location.dimension == null || location.dimension.isBlank()) {
            issues.add(new Issue(Severity.ERROR, label + " is not configured.", location));
            return;
        }
        ServerLevel level = resolve(server, location.dimension);
        if (level == null) {
            issues.add(new Issue(Severity.ERROR, label + " uses an unavailable dimension.", location));
            return;
        }
        BlockPos pos = BlockPos.containing(location.x, location.y, location.z);
        if (region != null && !region.contains(level.dimension(), pos)) {
            issues.add(new Issue(Severity.ERROR, label + " lies outside the arena region.", location));
        }
        if (!safeBoostLocation(level, pos)) {
            issues.add(new Issue(Severity.ERROR, label
                    + " needs free item space and a solid floor below it.", location));
        }
    }

    /** Validates the real boost position while treating the temporary setup End Rod as air. */
    private static boolean safeBoostLocation(ServerLevel level, BlockPos feet) {
        if (level == null || feet == null) return false;
        var feetState = level.getBlockState(feet);
        boolean freeFeet = feetState.is(net.minecraft.world.level.block.Blocks.END_ROD)
                || feetState.getCollisionShape(level, feet).isEmpty();
        boolean freeAbove = level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        boolean solidBelow = !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
        return freeFeet && freeAbove && solidBelow;
    }

    private static boolean safeStandingLocation(MinecraftServer server, MinigameLocation location) {
        if (location == null) return false;
        ServerLevel level = resolve(server, location.dimension);
        if (level == null) return false;
        BlockPos feet = BlockPos.containing(location.x, location.y, location.z);
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
    }

    private static ServerLevel resolve(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }

    private static boolean boundsNearRegion(MinigameAreaBounds bounds, Region region, int padding) {
        if (bounds == null || region == null || !bounds.configured()) return false;
        return bounds.dimension.equals(region.getDimension().location().toString())
                && bounds.minX >= region.getMinX() - padding && bounds.maxX <= region.getMaxX() + padding
                && bounds.minY >= region.getMinY() - padding && bounds.maxY <= region.getMaxY() + padding
                && bounds.minZ >= region.getMinZ() - padding && bounds.maxZ <= region.getMaxZ() + padding;
    }

    private static double distance(MinigameLocation first, MinigameLocation second) {
        if (first == null || second == null || !first.dimension.equals(second.dimension)) return Double.POSITIVE_INFINITY;
        double x = first.x - second.x, y = first.y - second.y, z = first.z - second.z;
        return Math.sqrt(x * x + y * y + z * z);
    }
}
