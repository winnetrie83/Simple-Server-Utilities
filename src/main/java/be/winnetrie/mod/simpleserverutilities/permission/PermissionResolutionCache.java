package be.winnetrie.mod.simpleserverutilities.permission;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.region.Region;
import net.minecraft.server.level.ServerPlayer;

/** Bounded LRU cache for raw permission resolution results. */
final class PermissionResolutionCache {

    private static final int MAX_ENTRIES = 50_000;
    private static final String NULL_SENTINEL = "\u0000";

    private final LinkedHashMap<Key, String> values = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    synchronized Lookup get(Key key) {
        if (!values.containsKey(key)) {
            return new Lookup(false, null);
        }
        String stored = values.get(key);
        return new Lookup(true, NULL_SENTINEL.equals(stored) ? null : stored);
    }

    synchronized void put(Key key, String value) {
        values.put(key, value == null ? NULL_SENTINEL : value);
    }

    synchronized int clear() {
        int previousSize = values.size();
        values.clear();
        return previousSize;
    }

    synchronized int size() {
        return values.size();
    }

    static Key key(ServerPlayer player, String permission, PermissionContext context) {
        UUID playerId = player == null ? null : player.getUUID();
        String dimension = context == null || context.getDimension() == null ? "" : context.getDimension();
        PermissionContext.ClaimRole claimRole = context == null || context.getClaimRole() == null
                ? PermissionContext.ClaimRole.NONE
                : context.getClaimRole();

        Region region = context == null ? null : context.getRegion();
        String regionName = region == null ? "" : region.getName().trim().toLowerCase(java.util.Locale.ROOT);
        int regionPermissionHash = region == null ? 0 : region.getPermissionOverrides().hashCode();

        return new Key(
                playerId,
                Objects.requireNonNullElse(permission, ""),
                dimension,
                claimRole,
                regionName,
                regionPermissionHash
        );
    }

    record Key(
            UUID playerId,
            String permission,
            String dimension,
            PermissionContext.ClaimRole claimRole,
            String regionName,
            int regionPermissionHash
    ) {
    }

    record Lookup(boolean found, String value) {
    }
}
