package be.winnetrie.mod.simpleserverutilities.minigame;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.content.ContentId;

/** Reusable arena slot owned by one minigame definition. */
public final class MinigameArenaDefinition {
    public static final int MAX_TEAM_SPAWNS = 64;
    public static final int MAX_BOOST_SPAWNS = 64;

    public String id = "arena_1";
    public String displayName = "Arena 1";
    public boolean enabled = true;
    public String regionId = "";
    public boolean resetRegionAfterMatch;
    /** True only for arena regions created and owned by the Minigame Selection wizard. */
    public boolean managedRegion;
    public MinigameLocation lobby = new MinigameLocation();
    public MinigameLocation spectator = new MinigameLocation();
    /** Optional movement cuboid for eliminated spectators. */
    public MinigameAreaBounds spectatorBounds = new MinigameAreaBounds();
    /** Optional Spleef floor volume. Only configured floor blocks are breakable. */
    public MinigameAreaBounds playFloor = new MinigameAreaBounds();
    public List<MinigameSpawnPoint> teamSpawns = new ArrayList<>();
    public List<MinigameFlagPoint> flagPoints = new ArrayList<>();
    public List<MinigameControlPoint> controlPoints = new ArrayList<>();
    /** Administrator-authored possible boost locations used by manual placement mode. */
    public List<MinigameLocation> boostSpawns = new ArrayList<>();

    public MinigameArenaDefinition() {
        teamSpawns.add(new MinigameSpawnPoint(1, new MinigameLocation()));
        teamSpawns.add(new MinigameSpawnPoint(2, new MinigameLocation()));
        flagPoints.add(new MinigameFlagPoint(1, new MinigameLocation()));
        flagPoints.add(new MinigameFlagPoint(2, new MinigameLocation()));
    }

    public void normalize() {
        id = ContentId.require(id, "Minigame arena ID");
        displayName = bound(displayName, 128, id);
        regionId = bound(regionId, 128, "").trim();
        if (lobby == null) lobby = new MinigameLocation();
        if (spectator == null) spectator = lobby.copy();
        lobby.normalize();
        spectator.normalize();
        if (spectatorBounds == null) spectatorBounds = new MinigameAreaBounds();
        if (playFloor == null) playFloor = new MinigameAreaBounds();
        spectatorBounds.normalize();
        playFloor.normalize();
        ArrayList<MinigameSpawnPoint> normalized = new ArrayList<>();
        if (teamSpawns != null) {
            for (MinigameSpawnPoint spawn : teamSpawns) {
                if (spawn == null) continue;
                spawn.normalize();
                normalized.add(spawn);
                if (normalized.size() >= MAX_TEAM_SPAWNS) break;
            }
        }
        teamSpawns = normalized;
        ArrayList<MinigameFlagPoint> normalizedFlags = new ArrayList<>();
        if (flagPoints != null) {
            for (MinigameFlagPoint point : flagPoints) {
                if (point == null) continue;
                point.normalize();
                normalizedFlags.add(point);
                if (normalizedFlags.size() >= 2) break;
            }
        }
        flagPoints = normalizedFlags;
        ArrayList<MinigameControlPoint> normalizedControlPoints = new ArrayList<>();
        if (controlPoints != null) {
            for (MinigameControlPoint point : controlPoints) {
                if (point == null) continue;
                point.normalize();
                normalizedControlPoints.add(point);
                if (normalizedControlPoints.size() >= 9) break;
            }
        }
        controlPoints = normalizedControlPoints;
        ArrayList<MinigameLocation> normalizedBoostSpawns = new ArrayList<>();
        if (boostSpawns != null) {
            for (MinigameLocation location : boostSpawns) {
                if (location == null) continue;
                location.normalize();
                normalizedBoostSpawns.add(location);
                if (normalizedBoostSpawns.size() >= MAX_BOOST_SPAWNS) break;
            }
        }
        boostSpawns = normalizedBoostSpawns;
        normalizeControlPointRespawns();
    }

    private void normalizeControlPointRespawns() {
        if (controlPoints.isEmpty()) return;
        double centerX = 0.0D;
        double centerZ = 0.0D;
        for (MinigameControlPoint point : controlPoints) {
            centerX += point.location.x;
            centerZ += point.location.z;
        }
        centerX /= controlPoints.size();
        centerZ /= controlPoints.size();
        for (MinigameControlPoint point : controlPoints) {
            if (point.respawn == null || sameBlock(point.location, point.respawn)) {
                double dx = centerX - point.location.x;
                double dz = centerZ - point.location.z;
                double length = Math.sqrt(dx * dx + dz * dz);
                double offset = length < 0.001D ? 2.5D : Math.min(2.5D, Math.max(1.5D, length * 0.35D));
                double spawnX = length < 0.001D ? point.location.x + offset
                        : point.location.x + dx / length * offset;
                double spawnZ = length < 0.001D ? point.location.z
                        : point.location.z + dz / length * offset;
                point.respawn = new MinigameLocation(point.location.dimension, spawnX, point.location.y, spawnZ,
                        point.location.yaw, point.location.pitch);
            }
            point.respawn.normalize();
        }
    }

    private static boolean sameBlock(MinigameLocation first, MinigameLocation second) {
        return first != null && second != null && first.dimension.equals(second.dimension)
                && (int) Math.floor(first.x) == (int) Math.floor(second.x)
                && (int) Math.floor(first.y) == (int) Math.floor(second.y)
                && (int) Math.floor(first.z) == (int) Math.floor(second.z);
    }

    public MinigameLocation spawnForTeam(int team) { return spawnForTeam(team, 0); }

    public MinigameLocation spawnForTeam(int team, int ordinal) {
        int requested = Math.max(0, ordinal);
        int found = 0;
        for (MinigameSpawnPoint spawn : teamSpawns) {
            if (spawn.team != team) continue;
            if (found++ == requested) return spawn.location;
        }
        for (MinigameSpawnPoint spawn : teamSpawns) if (spawn.team == team) return spawn.location;
        return lobby;
    }

    public MinigameFlagPoint flagForTeam(int team) {
        for (MinigameFlagPoint point : flagPoints) if (point.team == team) return point;
        return null;
    }

    private static String bound(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
