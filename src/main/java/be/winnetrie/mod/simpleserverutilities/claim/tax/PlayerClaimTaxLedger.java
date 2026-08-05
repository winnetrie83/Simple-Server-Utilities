package be.winnetrie.mod.simpleserverutilities.claim.tax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent collection of claim-tax settlements used for crash recovery. */
public final class PlayerClaimTaxLedger {
    public static final int SCHEMA_VERSION = 2;
    private static final int MAX_RETAINED_COMPLETED = 2_000;

    private int schemaVersion = SCHEMA_VERSION;
    private Map<String, PlayerClaimTaxSettlement> settlements = new LinkedHashMap<>();
    private transient boolean damagedRecords;

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        damagedRecords = false;
        if (settlements == null) settlements = new LinkedHashMap<>();
        Map<String, PlayerClaimTaxSettlement> normalized = new LinkedHashMap<>();
        settlements.values().stream()
                .filter(value -> value != null)
                .sorted(java.util.Comparator.comparingLong(PlayerClaimTaxSettlement::createdAt))
                .forEach(value -> {
                    try {
                        value.normalize();
                        normalized.put(value.settlementUuid().toString(), value);
                    } catch (RuntimeException ignored) {
                        // Never continue destructive taxation silently after losing
                        // an individual recovery record. The manager enters a
                        // global safety halt until an administrator intervenes.
                        damagedRecords = true;
                    }
                });
        settlements = normalized;
        java.util.Set<UUID> activeOwners = new java.util.HashSet<>();
        for (PlayerClaimTaxSettlement value : settlements.values()) {
            if (!value.isTerminal() && !activeOwners.add(value.ownerUuid())) {
                damagedRecords = true;
            }
        }
        pruneCompleted();
    }


    public boolean hasDamagedRecords() { return damagedRecords; }

    public void put(PlayerClaimTaxSettlement settlement) {
        if (settlement == null) return;
        settlement.normalize();
        settlements.put(settlement.settlementUuid().toString(), settlement);
        pruneCompleted();
    }

    public PlayerClaimTaxSettlement get(UUID id) {
        return id == null ? null : settlements.get(id.toString());
    }

    public List<PlayerClaimTaxSettlement> all() {
        return List.copyOf(settlements.values());
    }

    public PlayerClaimTaxSettlement activeForOwner(UUID owner) {
        if (owner == null) return null;
        for (PlayerClaimTaxSettlement value : settlements.values()) {
            if (!value.isTerminal() && owner.equals(value.ownerUuid())) return value;
        }
        return null;
    }

    private void pruneCompleted() {
        List<String> completed = new ArrayList<>();
        for (Map.Entry<String, PlayerClaimTaxSettlement> entry : settlements.entrySet()) {
            PlayerClaimTaxSettlement value = entry.getValue();
            // Penalty-bearing settlements are the permanent audit/recovery source
            // for confiscated capacity and are therefore never pruned.
            if (value.isTerminal() && value.penaltyChunks() <= 0) completed.add(entry.getKey());
        }
        int remove = Math.max(0, completed.size() - MAX_RETAINED_COMPLETED);
        for (int i = 0; i < remove; i++) settlements.remove(completed.get(i));
    }
}
