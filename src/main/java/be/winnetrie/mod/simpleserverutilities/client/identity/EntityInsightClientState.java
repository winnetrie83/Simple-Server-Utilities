package be.winnetrie.mod.simpleserverutilities.client.identity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import be.winnetrie.mod.simpleserverutilities.network.EntityInsightPayload;

/** Latest server-authoritative Entity Insight selection for the local viewer. */
public final class EntityInsightClientState {
    private static final Map<Integer, EntityInsightPayload.Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile boolean enabled;
    private static volatile boolean showHealth = true;
    private static volatile int range = 16;
    private static volatile int maxEntities = 20;

    private EntityInsightClientState() {
    }

    public static void apply(EntityInsightPayload payload) {
        enabled = payload.enabled();
        showHealth = payload.showHealth();
        range = payload.range();
        maxEntities = payload.maxEntities();
        ENTRIES.clear();
        for (EntityInsightPayload.Entry entry : payload.entries()) {
            ENTRIES.put(entry.entityId(), entry);
        }
    }

    public static EntityInsightPayload.Entry entry(int entityId) {
        return enabled ? ENTRIES.get(entityId) : null;
    }

    public static boolean showHealth() {
        return showHealth;
    }

    public static int range() {
        return range;
    }

    public static int maxEntities() {
        return maxEntities;
    }

    public static void clear() {
        enabled = false;
        showHealth = true;
        range = 16;
        maxEntities = 20;
        ENTRIES.clear();
    }
}
