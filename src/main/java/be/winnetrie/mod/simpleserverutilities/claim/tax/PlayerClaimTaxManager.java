package be.winnetrie.mod.simpleserverutilities.claim.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaim;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyAccount;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyResult;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyTransactionType;
import be.winnetrie.mod.simpleserverutilities.mail.MailSource;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-claim recurring tax engine with monotonic cycle peaks, idempotent economy
 * charges, permanent capacity confiscation and crash-recoverable settlements.
 */
public final class PlayerClaimTaxManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Duration CRITICAL_FLUSH_TIMEOUT = Duration.ofSeconds(10);

    public enum VoluntaryDeleteMode { PAY_AND_DELETE, FORFEIT_AND_DELETE }

    public record ClaimTaxQuote(long amountMinor, int peakChunks, int currentChunks, long dueAt) {}
    public record TaxActionResult(boolean successful, String message) {
        public static TaxActionResult ok(String message) { return new TaxActionResult(true, message); }
        public static TaxActionResult fail(String message) { return new TaxActionResult(false, message); }
    }

    private MinecraftServer server;
    private Path settingsFile;
    private Path ledgerFile;
    private Path safetyHaltFile;
    private PlayerClaimTaxSettings settings = new PlayerClaimTaxSettings();
    private PlayerClaimTaxLedger ledger = new PlayerClaimTaxLedger();
    private boolean safetyHalted;

    public synchronized void load(MinecraftServer server) {
        this.server = server;
        Path claimRoot = StoragePaths.playerClaims(StoragePaths.root(server));
        this.settingsFile = claimRoot.resolve("tax_settings.json");
        this.ledgerFile = claimRoot.resolve("tax_settlements.json");
        this.safetyHaltFile = claimRoot.resolve("tax_safety_halt.json");
        safetyHalted = Files.isRegularFile(safetyHaltFile);
        if (safetyHalted) {
            SimpleServerUtilities.LOGGER.error(
                    "Player Claim tax remains in persistent SAFETY HALT because {} exists. Repair the ledger/storage problem before removing that marker and reloading SSU.",
                    safetyHaltFile);
        }
        settings = loadOrDefault(settingsFile, PlayerClaimTaxSettings.class, new PlayerClaimTaxSettings(), "claim-tax settings");
        ledger = loadLedger(ledgerFile);
        long now = System.currentTimeMillis();
        settings.normalize(now);
        ledger.normalize();
        if (ledger.hasDamagedRecords()) {
            boolean haltPersisted = enterSafetyHalt(
                    "One or more settlement records were damaged or multiple active settlements existed for the same owner.", null);
            Path archived = haltPersisted ? JsonStorage.archiveBrokenFile(ledgerFile) : null;
            SimpleServerUtilities.LOGGER.error(
                    haltPersisted
                            ? "Player Claim tax entered persistent SAFETY HALT because one or more settlement records were damaged. The ledger was archived as {}."
                            : "Player Claim tax entered SAFETY HALT because one or more settlement records were damaged. The halt marker could not be persisted, so the damaged ledger was deliberately left in place for the next startup.",
                    archived);
        }
        if (!safetyHalted) reconcileCompletedConfiscations();

        boolean migrated = false;
        for (PlayerClaim claim : sortedClaims()) {
            boolean initialized = claim.hasInitializedTaxCycle();
            boolean repairedPeak = claim.repairTaxPeakInvariant();
            initializeClaimCycle(claim, now);
            migrated |= !initialized || repairedPeak;
        }
        if (migrated) {
            SimpleServerUtilities.PLAYER_CLAIMS.save();
            SimpleServerUtilities.STORAGE.flush(CRITICAL_FLUSH_TIMEOUT);
        }
        save();
        if (!safetyHalted) {
            // Recovery starts on the first server tick, after Economy, Mail and
            // Homes have completed their own lifecycle loading.
            saveLedgerSync();
        }
    }

    private <T> T loadOrDefault(Path file, Class<T> type, T fallback, String label) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                T loaded = JsonStorage.read(GSON, file, type);
                if (loaded != null) return loaded;
            }
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(file);
            SimpleServerUtilities.LOGGER.error("Failed to load {}. Archived as {}.", label, archived, exception);
        }
        return fallback;
    }

    private PlayerClaimTaxLedger loadLedger(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                PlayerClaimTaxLedger loaded = JsonStorage.read(GSON, file, PlayerClaimTaxLedger.class);
                if (loaded != null) return loaded;
                throw new IllegalStateException("Settlement ledger was empty.");
            }
            return new PlayerClaimTaxLedger();
        } catch (Exception primaryFailure) {
            Path backup = file.resolveSibling(file.getFileName() + ".bak");
            try {
                if (Files.isRegularFile(backup)) {
                    PlayerClaimTaxLedger restored = JsonStorage.read(GSON, backup, PlayerClaimTaxLedger.class);
                    if (restored != null) {
                        restored.normalize();
                        if (!restored.hasDamagedRecords()) {
                            Path archived = JsonStorage.archiveBrokenFile(file);
                            JsonStorage.write(GSON, file, restored);
                            SimpleServerUtilities.LOGGER.error(
                                    "Recovered the claim-tax settlement ledger from {} after archiving the damaged file as {}.",
                                    backup, archived, primaryFailure);
                            return restored;
                        }
                    }
                }
            } catch (Exception backupFailure) {
                primaryFailure.addSuppressed(backupFailure);
            }
            boolean haltPersisted = enterSafetyHalt("The settlement ledger could not be read or recovered from backup.", primaryFailure);
            Path archived = haltPersisted ? JsonStorage.archiveBrokenFile(file) : null;
            SimpleServerUtilities.LOGGER.error(
                    haltPersisted
                            ? "Could not recover the claim-tax settlement ledger. Player Claim tax is in persistent SAFETY HALT; the damaged ledger was archived as {}."
                            : "Could not recover the claim-tax settlement ledger. Player Claim tax is in SAFETY HALT and the damaged ledger was left in place because the halt marker could not be persisted.",
                    archived, primaryFailure);
            return new PlayerClaimTaxLedger();
        }
    }

    /**
     * Reconciles retained completed settlement journals with permanent claim
     * capacity. A missing entry is restored; a conflicting amount fails closed
     * because silently choosing either value could over- or under-penalize.
     */
    private void reconcileCompletedConfiscations() {
        boolean changed = false;
        java.util.Set<UUID> changedOwners = new java.util.LinkedHashSet<>();
        for (PlayerClaimTaxSettlement settlement : ledger.all()) {
            if (settlement.status() != PlayerClaimTaxSettlement.Status.COMPLETED
                    || settlement.penaltyChunks() <= 0) continue;
            String id = settlement.settlementUuid().toString();
            int actual = SimpleServerUtilities.PLAYER_CLAIMS.getClaimChunkConfiscation(
                    settlement.ownerUuid(), id);
            if (actual == settlement.penaltyChunks()) continue;
            if (actual > 0) {
                enterSafetyHalt(
                        "Completed settlement " + id + " conflicts with the stored confiscated-capacity amount.", null);
                SimpleServerUtilities.LOGGER.error(
                        "Player Claim tax entered SAFETY HALT: completed settlement {} expects {} confiscated chunks but the player limit record contains {}.",
                        id, settlement.penaltyChunks(), actual);
                return;
            }
            boolean applied = SimpleServerUtilities.PLAYER_CLAIMS.applyClaimChunkConfiscation(
                    settlement.ownerUuid(), id, settlement.penaltyChunks());
            changed |= applied;
            if (applied) changedOwners.add(settlement.ownerUuid());
        }
        if (changed) {
            SimpleServerUtilities.PLAYER_CLAIMS.save();
            SimpleServerUtilities.STORAGE.flush(CRITICAL_FLUSH_TIMEOUT);
            for (UUID owner : changedOwners) {
                if (!SimpleServerUtilities.PLAYER_CLAIMS.isClaimLimitDurable(owner)) {
                    enterSafetyHalt(
                            "Restored confiscation data for " + owner + " was not durable after a critical flush.", null);
                    SimpleServerUtilities.LOGGER.error(
                            "Player Claim tax entered SAFETY HALT because restored confiscation data for {} is not durable.", owner);
                    return;
                }
            }
        }
    }

    /**
     * Enters a fail-closed state that survives server restarts. A damaged or
     * ambiguous settlement must never become actionable merely because the
     * current process stopped. The marker is deliberately removed only by an
     * administrator after repairing/restoring the underlying data.
     */
    private boolean enterSafetyHalt(String reason, Throwable failure) {
        safetyHalted = true;
        String safeReason = reason == null || reason.isBlank()
                ? "Unknown claim-tax safety failure."
                : reason.trim();
        if (safetyHaltFile == null) {
            SimpleServerUtilities.LOGGER.error("Could not persist the claim-tax SAFETY HALT marker because its path is unavailable. Reason: {}", safeReason, failure);
            return false;
        }
        try {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("schemaVersion", 1);
            marker.put("createdAt", System.currentTimeMillis());
            marker.put("reason", safeReason);
            marker.put("ledger", ledgerFile == null ? "" : ledgerFile.getFileName().toString());
            JsonStorage.write(GSON, safetyHaltFile, marker);
            return true;
        } catch (Exception markerFailure) {
            if (failure != null) markerFailure.addSuppressed(failure);
            SimpleServerUtilities.LOGGER.error(
                    "Could not persist the claim-tax SAFETY HALT marker. The current process remains fail-closed; the damaged source file is left in place so the next startup detects it again. Reason: {}",
                    safeReason, markerFailure);
            return false;
        }
    }

    public synchronized void clear() {
        server = null;
        settingsFile = null;
        ledgerFile = null;
        safetyHaltFile = null;
        settings = new PlayerClaimTaxSettings();
        ledger = new PlayerClaimTaxLedger();
        safetyHalted = false;
    }

    public synchronized void save() {
        if (settingsFile == null) return;
        settings.normalize(System.currentTimeMillis());
        SimpleServerUtilities.STORAGE.queueJson(GSON, settingsFile, settings);
    }

    private boolean saveLedgerSync() {
        if (ledgerFile == null || safetyHalted) return false;
        try {
            ledger.normalize();
            JsonStorage.write(GSON, ledgerFile, ledger);
            return true;
        } catch (Exception exception) {
            enterSafetyHalt("The settlement ledger could not be persisted before the next settlement step.", exception);
            SimpleServerUtilities.LOGGER.error(
                    "Could not persist the claim-tax settlement ledger. Player Claim tax entered SAFETY HALT before any further destructive step.",
                    exception);
            return false;
        }
    }

    public synchronized PlayerClaimTaxSettings settings() { return settings; }
    public synchronized boolean isEnabled() { return SsuModuleAccess.active("claims") && SsuModuleAccess.active("economy") && settings.isEnabled() && settings.getRateMinorPerChunk() > 0L; }

    public synchronized boolean requiresDeleteSettlement(PlayerClaim claim) {
        return claim != null && isEnabled() && claim.getTaxPeakChunks() > 0
                && claimTax(claim) > 0L;
    }

    public synchronized boolean isMutationLocked(UUID owner) {
        return owner != null && ((safetyHalted && isEnabled()) || ledger.activeForOwner(owner) != null);
    }

    public synchronized boolean isSafetyHalted() { return safetyHalted; }

    public synchronized boolean allowsSettlementClaimDeletion(UUID owner, UUID claimId, UUID settlementId) {
        if (owner == null || claimId == null || settlementId == null) return false;
        PlayerClaimTaxSettlement active = ledger.activeForOwner(owner);
        return active != null
                && active.settlementUuid().equals(settlementId)
                && active.status() == PlayerClaimTaxSettlement.Status.CLAIMS_REMOVING
                && active.claimUuidsToRemove().contains(claimId);
    }

    public synchronized void configure(boolean enabled, long rateMinor, long intervalMillis, long reminderMillis) {
        long now = System.currentTimeMillis();
        boolean wasEnabled = isEnabled();
        settings.setEnabled(enabled);
        settings.setRateMinorPerChunk(rateMinor);
        settings.setIntervalMillis(intervalMillis);
        settings.setReminderLeadMillis(reminderMillis);
        settings.normalize(now);
        boolean nowEnabled = isEnabled();
        // Enabling starts a clean, full cycle for all existing claims. Later
        // setting changes apply only to newly created or next paid cycles.
        if (!wasEnabled && nowEnabled) restartAllClaimCycles(now);
        save();
    }

    public synchronized void setDimensionMultiplier(String dimension, double multiplier) {
        settings.setMultiplier(dimension, multiplier);
        settings.normalize(System.currentTimeMillis());
        save();
    }

    public synchronized void removeDimensionMultiplier(String dimension) {
        settings.removeMultiplier(dimension);
        settings.normalize(System.currentTimeMillis());
        save();
    }

    public synchronized void initializeClaimCycle(PlayerClaim claim, long now) {
        if (claim == null) return;
        claim.ensureTaxCycle(now,
                settings.getIntervalMillis(),
                settings.getReminderLeadMillis(),
                settings.getRateMinorPerChunk(),
                settings.multiplierBasisPoints(claim.getDimension()));
    }

    private void restartAllClaimCycles(long now) {
        for (PlayerClaim claim : sortedClaims()) {
            claim.startTaxCycle(now,
                    settings.getIntervalMillis(),
                    settings.getReminderLeadMillis(),
                    settings.getRateMinorPerChunk(),
                    settings.multiplierBasisPoints(claim.getDimension()));
        }
        SimpleServerUtilities.PLAYER_CLAIMS.save();
    }

    public synchronized long taxForOwner(UUID owner) {
        if (owner == null) return 0L;
        long total = 0L;
        for (PlayerClaim claim : sortedClaims()) {
            if (claim.isOwner(owner)) total = safeAdd(total, claimTax(claim));
        }
        return total;
    }

    public synchronized long claimTax(PlayerClaim claim) {
        if (claim == null) return 0L;
        return taxForSnapshot(claim.getTaxRateMinorPerChunkSnapshot(), claim.getTaxPeakChunks(),
                claim.getTaxDimensionMultiplierBasisPointsSnapshot());
    }

    public synchronized ClaimTaxQuote quote(PlayerClaim claim) {
        if (claim == null) return new ClaimTaxQuote(0L, 0, 0, 0L);
        return new ClaimTaxQuote(claimTax(claim), claim.getTaxPeakChunks(), claim.getChunkCount(), claim.getTaxDueAt());
    }

    public synchronized long nextDueAt() {
        return sortedClaims().stream().mapToLong(PlayerClaim::getTaxDueAt).filter(value -> value > 0L).min().orElse(0L);
    }

    private long taxForSnapshot(long rateMinor, int chunks, long multiplierBasisPoints) {
        if (rateMinor <= 0L || chunks <= 0 || multiplierBasisPoints <= 0L) return 0L;
        try {
            return BigDecimal.valueOf(rateMinor)
                    .multiply(BigDecimal.valueOf(chunks))
                    .multiply(BigDecimal.valueOf(multiplierBasisPoints))
                    .divide(BigDecimal.valueOf(PlayerClaimTaxSettings.MULTIPLIER_SCALE), 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    public synchronized void maintenanceTick() {
        if (server == null || !SsuModuleAccess.active("claims") || !SsuModuleAccess.active("economy")) return;
        deliverPendingResultMails();
        if (safetyHalted) return;
        recoverSettlements();
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        boolean claimsChanged = false;
        for (PlayerClaim claim : sortedClaims()) {
            initializeClaimCycle(claim, now);
            claimsChanged |= sendReminderWhenNeeded(claim, now);
        }
        if (claimsChanged) SimpleServerUtilities.PLAYER_CLAIMS.save();

        Map<UUID, List<PlayerClaim>> dueByOwner = new LinkedHashMap<>();
        for (PlayerClaim claim : sortedClaims()) {
            if (claim.getTaxDueAt() <= now
                    && claim.getTaxReminderSentForDueAt() == claim.getTaxDueAt()
                    && ledger.activeForOwner(claim.getOwner()) == null) {
                dueByOwner.computeIfAbsent(claim.getOwner(), ignored -> new ArrayList<>()).add(claim);
            }
        }
        for (Map.Entry<UUID, List<PlayerClaim>> entry : dueByOwner.entrySet()) {
            createAutomaticSettlement(entry.getKey(), entry.getValue());
        }
        recoverSettlements();
        deliverPendingResultMails();
    }

    private boolean sendReminderWhenNeeded(PlayerClaim claim, long now) {
        if (claim == null || claim.getTaxReminderSentForDueAt() == claim.getTaxDueAt()) return false;
        long lead = Math.max(Duration.ofMinutes(1).toMillis(), claim.getTaxReminderLeadMillisSnapshot());
        if (now < claim.getTaxDueAt() - lead) return false;
        if (!SsuModuleAccess.active("mail")) return false;

        // If the server missed the reminder window, create a fresh full warning
        // window rather than enforcing a destructive consequence immediately.
        if (now >= claim.getTaxDueAt()) {
            claim.postponeTaxDue(safeAdd(now, lead));
        }
        ClaimTaxQuote quote = quote(claim);
        long remaining = Math.max(0L, quote.dueAt() - now);
        String body = "Estimated tax for claim '" + claim.getDisplayName() + "': "
                + SimpleServerUtilities.ECONOMY.format(quote.amountMinor()) + " based on a cycle peak of "
                + quote.peakChunks() + " chunk(s). This is an estimate: expanding the claim before payment may increase "
                + "the final tax and penalty. Removing chunks does not reduce the recorded peak for this cycle. "
                + "Payment is scheduled in " + humanDuration(remaining) + ". If the automatic payment fails, all of your "
                + "claims and linked homes will be removed and the final number of taxed chunks will be permanently "
                + "confiscated from your claim capacity.";
        var delivery = SimpleServerUtilities.MAIL.deliverSystemMail(claim.getOwner(), playerName(claim.getOwner()),
                "Estimated tax for claim " + claim.getDisplayName(), body, List.of(), 0L, MailSource.SYSTEM,
                "claim-tax-reminder:" + claim.getId() + ":" + claim.getTaxDueAt());
        if (!delivery.successful()) {
            SimpleServerUtilities.LOGGER.error("Could not deliver claim-tax reminder for claim {}: {}",
                    claim.getId(), delivery.message());
            return false;
        }
        claim.markTaxReminderSent();
        return true;
    }

    private void createAutomaticSettlement(UUID owner, List<PlayerClaim> dueClaims) {
        if (owner == null || dueClaims == null || dueClaims.isEmpty() || ledger.activeForOwner(owner) != null) return;
        long amount = 0L;
        int penalty = 0;
        List<UUID> dueIds = new ArrayList<>();
        List<PlayerClaim> zeroClaims = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (PlayerClaim claim : dueClaims) {
            long claimAmount = claimTax(claim);
            if (claimAmount == 0L) {
                zeroClaims.add(claim);
                continue;
            }
            if (!isSafeChargeAmount(claimAmount)) {
                SimpleServerUtilities.LOGGER.error(
                        "Claim-tax settlement for {} was halted because claim {} produced an unsafe amount of {} minor units. No money, claim or capacity was changed.",
                        owner, claim.getId(), claimAmount);
                return;
            }
            long nextAmount = safeAdd(amount, claimAmount);
            if (!isSafeChargeAmount(nextAmount)) {
                SimpleServerUtilities.LOGGER.error(
                        "Claim-tax settlement for {} was halted because the combined invoice exceeds the Economy maximum balance. No money, claim or capacity was changed.", owner);
                return;
            }
            amount = nextAmount;
            penalty = safeIntAdd(penalty, claim.getTaxPeakChunks());
            dueIds.add(claim.getId());
        }

        if (!zeroClaims.isEmpty()) {
            for (PlayerClaim claim : zeroClaims) {
                completeClaimCycle(claim, "zero-" + UUID.randomUUID(), now);
            }
            SimpleServerUtilities.PLAYER_CLAIMS.save();
            if (!SimpleServerUtilities.STORAGE.flush(CRITICAL_FLUSH_TIMEOUT)) {
                SimpleServerUtilities.LOGGER.error("Zero-value claim-tax cycles for {} could not be flushed; paid settlement creation was postponed.", owner);
                return;
            }
        }
        if (dueIds.isEmpty()) return;
        boolean homesActive = SsuModuleAccess.active("homes");
        if (homesActive && !SimpleServerUtilities.HOMES.ensureStorageReady(server)) {
            SimpleServerUtilities.LOGGER.error("Claim-tax settlement for {} was postponed because active Home storage is unavailable.", owner);
            return;
        }

        List<PlayerClaim> allOwnedClaims = sortedClaims().stream().filter(claim -> claim.isOwner(owner)).toList();
        List<UUID> allClaims = allOwnedClaims.stream().map(PlayerClaim::getId).toList();
        java.util.Set<UUID> dueSet = java.util.Set.copyOf(dueIds);
        List<PlayerClaimTaxSettlement.ClaimRemovalSnapshot> snapshots = allOwnedClaims.stream()
                .map(claim -> PlayerClaimTaxSettlement.ClaimRemovalSnapshot.capture(
                        claim, dueSet.contains(claim.getId()) ? claimTax(claim) : 0L, dueSet.contains(claim.getId()),
                        homesActive ? SimpleServerUtilities.HOMES.homeNamesInClaim(owner, claim) : List.of()))
                .toList();
        PlayerClaimTaxSettlement settlement = PlayerClaimTaxSettlement.create(UUID.randomUUID(), owner,
                PlayerClaimTaxSettlement.Kind.AUTOMATIC_DUE_BATCH, amount, penalty, null, dueIds, allClaims, snapshots);
        ledger.put(settlement);
        if (!saveLedgerSync()) {
            SimpleServerUtilities.LOGGER.error("Claim-tax settlement for {} was not started because its journal could not be written.", owner);
        }
    }

    public synchronized TaxActionResult settleVoluntaryDeletion(ServerPlayer player, String claimName,
            VoluntaryDeleteMode mode) {
        if (player == null || claimName == null || claimName.isBlank()) return TaxActionResult.fail("Claim not found.");
        UUID owner = player.getUUID();
        if (safetyHalted) return TaxActionResult.fail("Claim tax is in safety halt. Contact an administrator; nothing was changed.");
        if (ledger.activeForOwner(owner) != null) return TaxActionResult.fail("A claim-tax settlement is already in progress.");
        boolean homesActive = SsuModuleAccess.active("homes");
        if (homesActive && !SimpleServerUtilities.HOMES.ensureStorageReady(server)) {
            return TaxActionResult.fail("Active Home storage is unavailable; nothing was changed.");
        }
        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimGroup(owner, claimName);
        if (claim == null) return TaxActionResult.fail("Claim not found.");
        if (!requiresDeleteSettlement(claim)) {
            boolean deleted = SimpleServerUtilities.PLAYER_CLAIMS.deleteClaimGroup(owner, claimName, true);
            return deleted ? TaxActionResult.ok("Claim and linked homes deleted.") : TaxActionResult.fail("Claim could not be deleted.");
        }

        ClaimTaxQuote quote = quote(claim);
        if (!isSafeChargeAmount(quote.amountMinor())) {
            return TaxActionResult.fail("This claim tax exceeds the Economy safety limit. Nothing was changed; contact an administrator.");
        }
        PlayerClaimTaxSettlement.Kind kind = mode == VoluntaryDeleteMode.FORFEIT_AND_DELETE
                ? PlayerClaimTaxSettlement.Kind.VOLUNTARY_FORFEIT_DELETE
                : PlayerClaimTaxSettlement.Kind.VOLUNTARY_PAY_DELETE;
        long amount = kind == PlayerClaimTaxSettlement.Kind.VOLUNTARY_PAY_DELETE ? quote.amountMinor() : 0L;
        int penalty = kind == PlayerClaimTaxSettlement.Kind.VOLUNTARY_FORFEIT_DELETE ? quote.peakChunks() : 0;
        PlayerClaimTaxSettlement.ClaimRemovalSnapshot snapshot =
                PlayerClaimTaxSettlement.ClaimRemovalSnapshot.capture(claim, quote.amountMinor(), true,
                        homesActive ? SimpleServerUtilities.HOMES.homeNamesInClaim(owner, claim) : List.of());
        PlayerClaimTaxSettlement settlement = PlayerClaimTaxSettlement.create(UUID.randomUUID(), owner, kind,
                amount, penalty, claim.getId(), List.of(claim.getId()), List.of(claim.getId()), List.of(snapshot));
        ledger.put(settlement);
        if (!saveLedgerSync()) return TaxActionResult.fail("The settlement journal could not be written; nothing was changed.");
        processSettlement(settlement);
        return switch (settlement.status()) {
            case COMPLETED -> TaxActionResult.ok(kind == PlayerClaimTaxSettlement.Kind.VOLUNTARY_PAY_DELETE
                    ? "Tax paid and claim deleted." : "Claim deleted and " + penalty + " claim chunk(s) permanently confiscated.");
            case CANCELLED -> TaxActionResult.fail(settlement.lastError().isBlank() ? "The settlement was cancelled." : settlement.lastError());
            default -> TaxActionResult.fail("The settlement is safely queued and will retry automatically: " + settlement.lastError());
        };
    }

    private void recoverSettlements() {
        if (safetyHalted) return;
        for (PlayerClaimTaxSettlement settlement : ledger.all()) {
            if (!settlement.isTerminal()) processSettlement(settlement);
        }
    }

    private void processSettlement(PlayerClaimTaxSettlement settlement) {
        if (safetyHalted || settlement == null || settlement.isTerminal() || server == null) return;
        UUID owner = settlement.ownerUuid();
        try {
            if (settlement.status() == PlayerClaimTaxSettlement.Status.PREPARED
                    && settlement.kind() == PlayerClaimTaxSettlement.Kind.AUTOMATIC_DUE_BATCH
                    && !isEnabled()) {
                settlement.cancel("Player Claim tax was disabled before payment; no money, claim or capacity was changed.");
                saveLedgerSync();
                return;
            }
            if (settlement.status() == PlayerClaimTaxSettlement.Status.PREPARED) {
                if (settlement.kind() == PlayerClaimTaxSettlement.Kind.VOLUNTARY_FORFEIT_DELETE) {
                    settlement.markForfeiturePath();
                    settlement.setStatus(PlayerClaimTaxSettlement.Status.FORFEITURE_PENDING);
                    if (!saveLedgerSync()) return;
                } else if (SimpleServerUtilities.ECONOMY.isCommittedIdempotencyKey(settlement.economyIdempotencyKey())) {
                    settlement.setStatus(PlayerClaimTaxSettlement.Status.PAYMENT_COMMITTED);
                    if (!saveLedgerSync()) return;
                } else if (!SimpleServerUtilities.ECONOMY.isEnabled()) {
                    settlement.markRetry("Economy is unavailable; no claim or capacity was changed.");
                    saveLedgerSync();
                    return;
                } else {
                    EconomyResult result = SimpleServerUtilities.ECONOMY.debitTyped(null, "server", owner,
                            settlement.amountMinor(), EconomyTransactionType.CLAIM_TAX, "claims",
                            "Player claim tax settlement " + settlement.settlementUuid(),
                            settlement.economyIdempotencyKey());
                    if (result.successful() || "duplicate".equals(result.code())) {
                        settlement.setStatus(PlayerClaimTaxSettlement.Status.PAYMENT_COMMITTED);
                        if (!saveLedgerSync()) return;
                    } else if ("insufficient_funds".equals(result.code())) {
                        if (settlement.kind() == PlayerClaimTaxSettlement.Kind.AUTOMATIC_DUE_BATCH) {
                            settlement.markForfeiturePath();
                            settlement.setStatus(PlayerClaimTaxSettlement.Status.FORFEITURE_PENDING);
                        } else {
                            settlement.cancel("Insufficient balance. The claim was not deleted and no capacity was confiscated.");
                        }
                        if (!saveLedgerSync()) return;
                        if (settlement.isTerminal()) return;
                    } else {
                        settlement.markRetry("Economy error: " + result.message());
                        saveLedgerSync();
                        return;
                    }
                }
            }

            if (settlement.status() == PlayerClaimTaxSettlement.Status.PAYMENT_COMMITTED) {
                if (settlement.kind() == PlayerClaimTaxSettlement.Kind.AUTOMATIC_DUE_BATCH) {
                    long now = System.currentTimeMillis();
                    for (UUID claimId : settlement.dueClaimUuids()) {
                        PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimById(claimId);
                        if (claim != null && claim.isOwner(owner)) {
                            completeClaimCycle(claim, settlement.settlementUuid().toString(), now);
                        }
                    }
                    SimpleServerUtilities.PLAYER_CLAIMS.save();
                    SimpleServerUtilities.STORAGE.flush(CRITICAL_FLUSH_TIMEOUT);
                    boolean cyclesDurable = settlement.dueClaimUuids().stream().allMatch(claimId -> {
                        PlayerClaim dueClaim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimById(claimId);
                        return dueClaim != null && SimpleServerUtilities.PLAYER_CLAIMS.isClaimStorageDurable(claimId);
                    });
                    if (!cyclesDurable) {
                        settlement.markRetry("Paid tax was recorded, but the new claim cycles are not durable yet.");
                        saveLedgerSync();
                        return;
                    }
                    settlement.setStatus(PlayerClaimTaxSettlement.Status.COMPLETED);
                    saveLedgerSync();
                    return;
                }
                settlement.setStatus(PlayerClaimTaxSettlement.Status.CLAIMS_REMOVING);
                if (!saveLedgerSync()) return;
            }

            if (settlement.status() == PlayerClaimTaxSettlement.Status.FORFEITURE_PENDING) {
                settlement.setStatus(PlayerClaimTaxSettlement.Status.CLAIMS_REMOVING);
                if (!saveLedgerSync()) return;
            }

            if (settlement.status() == PlayerClaimTaxSettlement.Status.CLAIMS_REMOVING) {
                boolean needsHomes = settlement.claimSnapshots().stream()
                        .anyMatch(snapshot -> !snapshot.linkedHomeNames().isEmpty());
                boolean homesActive = SsuModuleAccess.active("homes");
                if (needsHomes && (!homesActive || !SimpleServerUtilities.HOMES.ensureStorageReady(server))) {
                    settlement.markRetry("This settlement captured linked homes; Home must be active and available before destructive cleanup can continue.");
                    saveLedgerSync();
                    return;
                }
                for (UUID claimId : settlement.claimUuidsToRemove()) {
                    if (settlement.isClaimRemoved(claimId)) continue;
                    PlayerClaimTaxSettlement.ClaimRemovalSnapshot snapshot = settlement.snapshotFor(claimId);
                    if (snapshot == null) {
                        enterSafetyHalt(
                                "Settlement " + settlement.settlementUuid() + " is missing the recovery snapshot for claim " + claimId + ".", null);
                        settlement.markRetry("Missing claim recovery snapshot; tax entered safety halt.");
                        SimpleServerUtilities.LOGGER.error("Claim-tax settlement {} is missing the recovery snapshot for claim {}. No further destructive step will run.",
                                settlement.settlementUuid(), claimId);
                        return;
                    }

                    // Always repeat snapshot-based home cleanup before verifying
                    // deletion. This remains possible even if the claim file was
                    // already removed immediately before a hard crash.
                    if (homesActive) {
                        for (String homeName : snapshot.linkedHomeNames()) {
                            SimpleServerUtilities.HOMES.deleteHome(owner, homeName);
                        }
                    }

                    PlayerClaim claim = SimpleServerUtilities.PLAYER_CLAIMS.getClaimById(claimId);
                    if (claim != null) {
                        if (!claim.isOwner(owner)) {
                            enterSafetyHalt(
                                    "Claim " + claimId + " changed ownership while settlement " + settlement.settlementUuid() + " was active.", null);
                            settlement.markRetry("Claim ownership changed during settlement; tax entered safety halt.");
                            SimpleServerUtilities.LOGGER.error("Claim {} no longer belongs to {} while settlement {} is active.",
                                    claimId, owner, settlement.settlementUuid());
                            return;
                        }
                        boolean deleted = SimpleServerUtilities.PLAYER_CLAIMS.deleteClaimForTaxSettlement(
                                owner, claimId, settlement.settlementUuid());
                        if (!deleted) {
                            settlement.markRetry("A claim could not be removed safely; the settlement will retry.");
                            saveLedgerSync();
                            return;
                        }
                    }

                    // Flush, then verify only the exact claim/home records used by
                    // this step. An unrelated failed storage write must not cause
                    // a false success or falsely mark this claim as removed.
                    SimpleServerUtilities.STORAGE.flush(CRITICAL_FLUSH_TIMEOUT);
                    if (!SimpleServerUtilities.PLAYER_CLAIMS.isClaimDeletionDurable(claimId)
                            || (homesActive && !SimpleServerUtilities.HOMES.isOwnerStorageDurable(owner))) {
                        settlement.markRetry("Claim or active linked-home deletion is not durable yet; the settlement will retry.");
                        saveLedgerSync();
                        return;
                    }

                    settlement.markClaimRemoved(claimId);
                    if (!saveLedgerSync()) return;
                }
                settlement.setStatus(PlayerClaimTaxSettlement.Status.CLAIMS_REMOVED);
                if (!saveLedgerSync()) return;
                if (server != null && SsuModuleAccess.active("visualization")) {
                    SimpleServerUtilities.BORDER_VISUALIZATIONS.refreshAll(server);
                }
            }

            if (settlement.status() == PlayerClaimTaxSettlement.Status.CLAIMS_REMOVED) {
                if (settlement.penaltyChunks() > 0) {
                    String settlementKey = settlement.settlementUuid().toString();
                    int existingPenalty = SimpleServerUtilities.PLAYER_CLAIMS.getClaimChunkConfiscation(
                            owner, settlementKey);
                    if (existingPenalty != 0 && existingPenalty != settlement.penaltyChunks()) {
                        enterSafetyHalt(
                                "Settlement " + settlement.settlementUuid() + " conflicts with an existing confiscated-capacity record.", null);
                        settlement.markRetry("The stored claim-capacity penalty conflicts with the settlement journal; tax entered safety halt.");
                        saveLedgerSync();
                        SimpleServerUtilities.LOGGER.error(
                                "Claim-tax settlement {} expects {} confiscated chunks but the owner record already contains {}. No further destructive step will run.",
                                settlement.settlementUuid(), settlement.penaltyChunks(), existingPenalty);
                        return;
                    }
                    if (existingPenalty == 0) {
                        SimpleServerUtilities.PLAYER_CLAIMS.applyClaimChunkConfiscation(
                                owner, settlementKey, settlement.penaltyChunks());
                    }
                    SimpleServerUtilities.STORAGE.flush(CRITICAL_FLUSH_TIMEOUT);
                    int persistedPenalty = SimpleServerUtilities.PLAYER_CLAIMS.getClaimChunkConfiscation(
                            owner, settlementKey);
                    if (persistedPenalty != settlement.penaltyChunks()
                            || !SimpleServerUtilities.PLAYER_CLAIMS.isClaimLimitDurable(owner)) {
                        settlement.markRetry("The permanent claim-capacity penalty is not durable yet.");
                        saveLedgerSync();
                        return;
                    }
                }
                settlement.setStatus(PlayerClaimTaxSettlement.Status.PENALTY_APPLIED);
                if (!saveLedgerSync()) return;
            }

            if (settlement.status() == PlayerClaimTaxSettlement.Status.PENALTY_APPLIED) {
                settlement.setStatus(PlayerClaimTaxSettlement.Status.COMPLETED);
                saveLedgerSync();
            }
        } catch (Exception exception) {
            settlement.markRetry("Unexpected settlement failure: " + exception.getMessage());
            saveLedgerSync();
            SimpleServerUtilities.LOGGER.error("Claim-tax settlement {} failed safely.", settlement.settlementUuid(), exception);
        }
    }

    private void completeClaimCycle(PlayerClaim claim, String settlementId, long now) {
        claim.completeTaxCycle(settlementId, now,
                settings.getIntervalMillis(), settings.getReminderLeadMillis(), settings.getRateMinorPerChunk(),
                settings.multiplierBasisPoints(claim.getDimension()));
    }

    private void deliverPendingResultMails() {
        if (!SsuModuleAccess.active("mail") || !SsuModuleAccess.active("economy")) return;
        for (PlayerClaimTaxSettlement settlement : ledger.all()) {
            if (!settlement.isTerminal() || settlement.resultMailSent()) continue;
            String subject;
            String body;
            if (settlement.status() == PlayerClaimTaxSettlement.Status.CANCELLED) {
                subject = "Claim deletion cancelled";
                body = settlement.lastError();
            } else if (settlement.kind() == PlayerClaimTaxSettlement.Kind.AUTOMATIC_DUE_BATCH
                    && settlement.forfeiturePath()) {
                subject = "Claims confiscated: unpaid tax";
                body = "The automatic claim tax of " + SimpleServerUtilities.ECONOMY.format(settlement.amountMinor())
                        + " could not be paid. All claims and linked homes were removed. "
                        + settlement.penaltyChunks() + " claim chunk(s) were permanently confiscated from your capacity.";
            } else if (settlement.kind() == PlayerClaimTaxSettlement.Kind.VOLUNTARY_FORFEIT_DELETE) {
                subject = "Claim deleted with capacity forfeiture";
                body = "The claim was deleted and " + settlement.penaltyChunks()
                        + " claim chunk(s) were permanently confiscated from your capacity.";
            } else if (settlement.kind() == PlayerClaimTaxSettlement.Kind.VOLUNTARY_PAY_DELETE) {
                subject = "Claim tax paid and claim deleted";
                body = "A claim tax payment of " + SimpleServerUtilities.ECONOMY.format(settlement.amountMinor())
                        + " was completed successfully. The selected claim and its linked homes were deleted.";
            } else {
                subject = "Claim tax paid";
                body = "A claim tax payment of " + SimpleServerUtilities.ECONOMY.format(settlement.amountMinor())
                        + " was completed successfully.";
            }
            var result = SimpleServerUtilities.MAIL.deliverSystemMail(settlement.ownerUuid(), playerName(settlement.ownerUuid()),
                    subject, body, List.of(), 0L, MailSource.SYSTEM,
                    "claim-tax-result:" + settlement.settlementUuid());
            if (result.successful()) {
                settlement.markResultMailSent();
                saveLedgerSync();
            }
        }
    }

    private List<PlayerClaim> sortedClaims() {
        return SimpleServerUtilities.PLAYER_CLAIMS.getClaims().stream()
                .sorted(Comparator.comparing(PlayerClaim::getOwner)
                        .thenComparingLong(PlayerClaim::getTaxDueAt)
                        .thenComparing(PlayerClaim::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String playerName(UUID owner) {
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(owner);
            if (online != null) return online.getName().getString();
        }
        if (SsuModuleAccess.active("economy")) {
            return SimpleServerUtilities.ECONOMY.findPlayerAccount(owner)
                    .map(EconomyAccount::getLastKnownName).filter(name -> !name.isBlank()).orElse(owner.toString());
        }
        return owner.toString();
    }

    public synchronized Map<String, Double> dimensionMultipliers() { return settings.getDimensionMultipliers(); }

    private boolean isSafeChargeAmount(long amountMinor) {
        if (amountMinor < 0L || amountMinor == Long.MAX_VALUE) return false;
        long maximum = SimpleServerUtilities.ECONOMY.settings().getMaximumBalanceMinor();
        return amountMinor <= maximum;
    }

    private static String humanDuration(long millis) {
        long minutes = Math.max(1L, Duration.ofMillis(millis).toMinutes());
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        if (days > 0L) return days + " day(s) and " + hours + " hour(s)";
        if (hours > 0L) return hours + " hour(s) and " + mins + " minute(s)";
        return mins + " minute(s)";
    }

    private static long safeAdd(long first, long second) {
        try { return Math.addExact(first, second); }
        catch (ArithmeticException exception) { return Long.MAX_VALUE; }
    }

    private static int safeIntAdd(int first, int second) {
        long value = (long) first + Math.max(0, second);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
