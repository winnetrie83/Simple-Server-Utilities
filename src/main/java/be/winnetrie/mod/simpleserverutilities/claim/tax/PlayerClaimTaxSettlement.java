package be.winnetrie.mod.simpleserverutilities.claim.tax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;

/**
 * Crash-recovery journal entry for one claim-tax settlement.
 *
 * <p>The entry is persisted synchronously before money, claims, homes or
 * permanent claim capacity are changed. Every destructive step is idempotent.
 * Removal snapshots retain the exact linked home names so those homes can still
 * be cleaned after a crash in which the claim file disappeared first.</p>
 */
public final class PlayerClaimTaxSettlement {
    public static final int SCHEMA_VERSION = 2;

    public enum Kind {
        AUTOMATIC_DUE_BATCH,
        VOLUNTARY_PAY_DELETE,
        VOLUNTARY_FORFEIT_DELETE
    }

    public enum Status {
        PREPARED,
        PAYMENT_COMMITTED,
        FORFEITURE_PENDING,
        CLAIMS_REMOVING,
        CLAIMS_REMOVED,
        PENALTY_APPLIED,
        COMPLETED,
        CANCELLED,
        RETRY_REQUIRED
    }

    /** Compact recovery/audit snapshot of one claim at settlement creation. */
    public static final class ClaimRemovalSnapshot {
        private String claimId = "";
        private String claimName = "";
        private String dimension = "";
        private int currentChunks;
        private int taxablePeakChunks;
        private long taxAmountMinor;
        private boolean dueForPayment;
        private List<String> linkedHomeNames = new ArrayList<>();

        public ClaimRemovalSnapshot() {
            // Required for Gson.
        }

        public static ClaimRemovalSnapshot capture(
                PlayerClaim claim,
                long taxAmountMinor,
                boolean dueForPayment,
                List<String> linkedHomeNames
        ) {
            if (claim == null) throw new IllegalArgumentException("Claim snapshot requires a claim.");
            ClaimRemovalSnapshot value = new ClaimRemovalSnapshot();
            value.claimId = claim.getId().toString();
            value.claimName = claim.getDisplayName();
            value.dimension = claim.getDimension();
            value.currentChunks = claim.getChunkCount();
            value.taxablePeakChunks = claim.getTaxPeakChunks();
            value.taxAmountMinor = Math.max(0L, taxAmountMinor);
            value.dueForPayment = dueForPayment;
            value.linkedHomeNames = linkedHomeNames == null ? new ArrayList<>() : new ArrayList<>(linkedHomeNames);
            value.normalize();
            return value;
        }

        private void normalize() {
            claimId = validUuid(claimId);
            if (claimId.isBlank()) throw new IllegalStateException("Settlement claim snapshot has no valid claim id.");
            claimName = claimName == null || claimName.isBlank() ? claimId : claimName.trim();
            dimension = dimension == null ? "" : dimension.trim();
            if (dimension.isBlank()) throw new IllegalStateException("Settlement claim snapshot has no dimension.");
            currentChunks = Math.max(0, currentChunks);
            taxablePeakChunks = Math.max(currentChunks, taxablePeakChunks);
            taxAmountMinor = Math.max(0L, taxAmountMinor);
            LinkedHashSet<String> normalizedHomes = new LinkedHashSet<>();
            if (linkedHomeNames != null) {
                linkedHomeNames.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(String::trim)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(normalizedHomes::add);
            }
            linkedHomeNames = new ArrayList<>(normalizedHomes);
        }

        public UUID claimUuid() { return UUID.fromString(claimId); }
        public String claimName() { return claimName; }
        public String dimension() { return dimension; }
        public int currentChunks() { return currentChunks; }
        public int taxablePeakChunks() { return taxablePeakChunks; }
        public long taxAmountMinor() { return taxAmountMinor; }
        public boolean dueForPayment() { return dueForPayment; }
        public List<String> linkedHomeNames() { return List.copyOf(linkedHomeNames); }
    }

    private int schemaVersion = SCHEMA_VERSION;
    private String settlementId = "";
    private String ownerId = "";
    private Kind kind = Kind.AUTOMATIC_DUE_BATCH;
    private Status status = Status.PREPARED;
    private long createdAt;
    private long amountMinor;
    private int penaltyChunks;
    private String targetClaimId = "";
    private List<String> dueClaimIds = new ArrayList<>();
    private List<String> claimsToRemove = new ArrayList<>();
    private List<ClaimRemovalSnapshot> claimSnapshots = new ArrayList<>();
    private Set<String> removedClaimIds = new LinkedHashSet<>();
    private String economyIdempotencyKey = "";
    private String lastError = "";
    private boolean forfeiturePath;
    private boolean resultMailSent;

    public static PlayerClaimTaxSettlement create(
            UUID settlementId,
            UUID owner,
            Kind kind,
            long amountMinor,
            int penaltyChunks,
            UUID targetClaimId,
            List<UUID> dueClaimIds,
            List<UUID> claimsToRemove,
            List<ClaimRemovalSnapshot> claimSnapshots
    ) {
        PlayerClaimTaxSettlement value = new PlayerClaimTaxSettlement();
        value.settlementId = settlementId.toString();
        value.ownerId = owner.toString();
        value.kind = kind == null ? Kind.AUTOMATIC_DUE_BATCH : kind;
        value.status = Status.PREPARED;
        value.createdAt = System.currentTimeMillis();
        value.amountMinor = Math.max(0L, amountMinor);
        value.penaltyChunks = Math.max(0, penaltyChunks);
        value.targetClaimId = targetClaimId == null ? "" : targetClaimId.toString();
        value.dueClaimIds = stringify(dueClaimIds);
        value.claimsToRemove = stringify(claimsToRemove);
        value.claimSnapshots = claimSnapshots == null ? new ArrayList<>() : new ArrayList<>(claimSnapshots);
        value.removedClaimIds = new LinkedHashSet<>();
        value.economyIdempotencyKey = "claims:tax:settlement:" + value.settlementId;
        value.forfeiturePath = value.kind == Kind.VOLUNTARY_FORFEIT_DELETE;
        value.normalize();
        return value;
    }

    public void normalize() {
        schemaVersion = SCHEMA_VERSION;
        settlementId = validUuid(settlementId);
        ownerId = validUuid(ownerId);
        if (settlementId.isBlank() || ownerId.isBlank()) {
            throw new IllegalStateException("Settlement is missing a valid identity.");
        }
        if (kind == null) kind = Kind.AUTOMATIC_DUE_BATCH;
        if (status == null || status == Status.RETRY_REQUIRED) status = Status.PREPARED;
        createdAt = Math.max(1L, createdAt);
        amountMinor = Math.max(0L, amountMinor);
        penaltyChunks = Math.max(0, penaltyChunks);
        targetClaimId = targetClaimId == null || targetClaimId.isBlank() ? "" : validUuid(targetClaimId);
        dueClaimIds = normalizeIds(dueClaimIds);
        claimsToRemove = normalizeIds(claimsToRemove);
        if (claimSnapshots == null) claimSnapshots = new ArrayList<>();
        Map<String, ClaimRemovalSnapshot> normalizedSnapshots = new LinkedHashMap<>();
        for (ClaimRemovalSnapshot snapshot : claimSnapshots) {
            if (snapshot == null) continue;
            snapshot.normalize();
            normalizedSnapshots.putIfAbsent(snapshot.claimUuid().toString(), snapshot);
        }
        for (String claimId : claimsToRemove) {
            if (!normalizedSnapshots.containsKey(claimId)) {
                throw new IllegalStateException("Settlement is missing the recovery snapshot for claim " + claimId + ".");
            }
        }
        claimSnapshots = new ArrayList<>(normalizedSnapshots.values());
        validateFinancialSnapshot(normalizedSnapshots);
        if (removedClaimIds == null) removedClaimIds = new LinkedHashSet<>();
        removedClaimIds = new LinkedHashSet<>(normalizeIds(new ArrayList<>(removedClaimIds)));
        if (!claimsToRemove.containsAll(removedClaimIds)) {
            throw new IllegalStateException("Settlement removed-claim progress references an unknown claim.");
        }
        economyIdempotencyKey = economyIdempotencyKey == null || economyIdempotencyKey.isBlank()
                ? "claims:tax:settlement:" + settlementId
                : economyIdempotencyKey.trim();
        lastError = lastError == null ? "" : lastError;
        if (kind == Kind.VOLUNTARY_FORFEIT_DELETE) forfeiturePath = true;
    }

    private void validateFinancialSnapshot(Map<String, ClaimRemovalSnapshot> snapshots) {
        for (String dueId : dueClaimIds) {
            if (!claimsToRemove.contains(dueId) || !snapshots.containsKey(dueId)) {
                throw new IllegalStateException("Settlement due claim is missing from its removal snapshot.");
            }
        }
        for (Map.Entry<String, ClaimRemovalSnapshot> entry : snapshots.entrySet()) {
            if (entry.getValue().dueForPayment() != dueClaimIds.contains(entry.getKey())) {
                throw new IllegalStateException("Settlement payable flags do not match the due-claim list.");
            }
        }
        try {
            if (kind == Kind.AUTOMATIC_DUE_BATCH) {
                long expectedAmount = 0L;
                int expectedPenalty = 0;
                for (String dueId : dueClaimIds) {
                    ClaimRemovalSnapshot snapshot = snapshots.get(dueId);
                    if (!snapshot.dueForPayment()) {
                        throw new IllegalStateException("Automatic settlement due snapshot is not marked payable.");
                    }
                    expectedAmount = Math.addExact(expectedAmount, snapshot.taxAmountMinor());
                    long combinedPenalty = (long) expectedPenalty + snapshot.taxablePeakChunks();
                    expectedPenalty = combinedPenalty > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) combinedPenalty;
                }
                if (amountMinor != expectedAmount || penaltyChunks != expectedPenalty) {
                    throw new IllegalStateException("Automatic settlement financial totals do not match claim snapshots.");
                }
            } else {
                if (claimsToRemove.size() != 1 || targetClaimId.isBlank()
                        || !claimsToRemove.contains(targetClaimId)) {
                    throw new IllegalStateException("Voluntary settlement must target exactly one claim.");
                }
                ClaimRemovalSnapshot snapshot = snapshots.get(targetClaimId);
                if (snapshot == null) throw new IllegalStateException("Voluntary settlement snapshot is missing.");
                if (kind == Kind.VOLUNTARY_PAY_DELETE) {
                    if (amountMinor != snapshot.taxAmountMinor() || penaltyChunks != 0) {
                        throw new IllegalStateException("Voluntary payment totals do not match its claim snapshot.");
                    }
                } else if (amountMinor != 0L || penaltyChunks != snapshot.taxablePeakChunks()) {
                    throw new IllegalStateException("Voluntary forfeiture totals do not match its claim snapshot.");
                }
            }
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Settlement financial totals overflow.", exception);
        }
    }

    public UUID settlementUuid() { return UUID.fromString(settlementId); }
    public UUID ownerUuid() { return UUID.fromString(ownerId); }
    public Kind kind() { return kind; }
    public Status status() { return status; }
    public long createdAt() { return createdAt; }
    public long amountMinor() { return amountMinor; }
    public int penaltyChunks() { return penaltyChunks; }
    public UUID targetClaimUuid() { return targetClaimId.isBlank() ? null : UUID.fromString(targetClaimId); }
    public List<UUID> dueClaimUuids() { return parseIds(dueClaimIds); }
    public List<UUID> claimUuidsToRemove() { return parseIds(claimsToRemove); }
    public List<ClaimRemovalSnapshot> claimSnapshots() { return List.copyOf(claimSnapshots); }
    public ClaimRemovalSnapshot snapshotFor(UUID claimId) {
        if (claimId == null) return null;
        for (ClaimRemovalSnapshot snapshot : claimSnapshots) {
            if (claimId.equals(snapshot.claimUuid())) return snapshot;
        }
        return null;
    }
    public Set<UUID> removedClaimUuids() { return new LinkedHashSet<>(parseIds(new ArrayList<>(removedClaimIds))); }
    public String economyIdempotencyKey() { return economyIdempotencyKey; }
    public String lastError() { return lastError; }
    public boolean forfeiturePath() { return forfeiturePath; }
    public boolean resultMailSent() { return resultMailSent; }

    public void setStatus(Status status) {
        this.status = status == null ? Status.PREPARED : status;
        if (this.status != Status.CANCELLED) lastError = "";
    }

    /** Records a retryable technical failure without losing the exact phase to resume. */
    public void markRetry(String message) {
        lastError = message == null || message.isBlank()
                ? "Unknown settlement failure."
                : message;
    }

    public void cancel(String message) {
        status = Status.CANCELLED;
        lastError = message == null || message.isBlank() ? "The settlement was cancelled." : message;
    }

    public void markForfeiturePath() { forfeiturePath = true; }

    public void markClaimRemoved(UUID claimId) {
        if (claimId != null) removedClaimIds.add(claimId.toString());
    }

    public boolean isClaimRemoved(UUID claimId) {
        return claimId != null && removedClaimIds.contains(claimId.toString());
    }

    public void markResultMailSent() { resultMailSent = true; }

    public boolean isTerminal() { return status == Status.COMPLETED || status == Status.CANCELLED; }

    private static List<String> stringify(List<UUID> ids) {
        List<String> result = new ArrayList<>();
        if (ids != null) {
            for (UUID id : ids) if (id != null) result.add(id.toString());
        }
        return result;
    }

    private static List<String> normalizeIds(List<String> raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw != null) {
            for (String id : raw) {
                String normalized = validUuid(id);
                if (!normalized.isBlank()) result.add(normalized);
            }
        }
        return new ArrayList<>(result);
    }

    private static List<UUID> parseIds(List<String> raw) {
        List<UUID> result = new ArrayList<>();
        for (String id : normalizeIds(raw)) result.add(UUID.fromString(id));
        return List.copyOf(result);
    }

    private static String validUuid(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try { return UUID.fromString(raw.trim()).toString(); }
        catch (IllegalArgumentException ignored) { return ""; }
    }
}
