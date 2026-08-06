package be.winnetrie.mod.simpleserverutilities.client.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import be.winnetrie.mod.simpleserverutilities.network.PlayerIdentitySyncPayload;

/** Client cache for online players' server-authoritative title and rank presentation. */
public final class PlayerIdentityClientState {
    private static Map<Integer, PlayerIdentitySyncPayload.Entry> entries = Map.of();
    private PlayerIdentityClientState() {
    }
    public static synchronized void apply(PlayerIdentitySyncPayload payload) {
        LinkedHashMap<Integer, PlayerIdentitySyncPayload.Entry> values = new LinkedHashMap<>();
        if (payload != null) for (var entry : payload.entries()) values.put(entry.entityId(), entry);
        entries = Map.copyOf(values);
    }
    public static synchronized PlayerIdentitySyncPayload.Entry get(int entityId) { return entries.get(entityId); }
    public static synchronized List<PlayerIdentitySyncPayload.Entry> snapshot() { return List.copyOf(entries.values()); }
    public static synchronized void clear() { entries = Map.of(); }
}
