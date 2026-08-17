package be.winnetrie.mod.simpleserverutilities.economy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.core.storage.DirtyJsonRecordStore;
import be.winnetrie.mod.simpleserverutilities.core.transaction.SsuTransactionManager;
import be.winnetrie.mod.simpleserverutilities.storage.JsonStorage;
import be.winnetrie.mod.simpleserverutilities.storage.StoragePaths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative wallet and economy storage.
 *
 * <p>Balances are stored as exact minor units (cents for the default euro
 * currency). Every balance mutation is written to a small atomic transaction
 * journal before in-memory balances are changed. Prepared or committed records
 * are replayed idempotently on startup by comparing account revisions.</p>
 */
public final class EconomyManager implements EconomyProvider {

    @Override
    public String providerId() { return "ssu_digital"; }

    @Override
    public String displayName() { return "SSU Digital Wallet"; }

    /** Removed in dev2.1; retained only to discard the experimental dev2 system account safely. */
    private static final UUID LEGACY_TREASURY_ID = UUID.nameUUIDFromBytes(
            (SimpleServerUtilities.MODID + ":server_treasury").getBytes(StandardCharsets.UTF_8));

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, EconomyAccount> accounts = new HashMap<>();
    private final Map<String, UUID> accountNameIndex = new HashMap<>();
    private final Map<UUID, EconomyTransactionRecord> transactions = new HashMap<>();
    private final Map<String, UUID> idempotencyIndex = new HashMap<>();
    private final Deque<UUID> recentTransactionIds = new ArrayDeque<>();
    private final DirtyJsonRecordStore accountStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore transactionStore = new DirtyJsonRecordStore();
    private final Map<UUID, Deque<UUID>> retainedByAccount = new HashMap<>();
    private final Map<UUID, Integer> retentionReferences = new HashMap<>();
    private final Set<UUID> retentionTracked = new HashSet<>();
    private final Deque<UUID> retainedUnscoped = new ArrayDeque<>();
    private final Map<String, Long> committedKeyTimestamps = new LinkedHashMap<>();
    private final Deque<String> committedKeyOrder = new ArrayDeque<>();
    private final DirtyJsonRecordStore committedKeyStore = new DirtyJsonRecordStore();
    private boolean retentionReady;

    private EconomySettings settings = new EconomySettings();
    private Path rootFolder;
    private Path accountsFolder;
    private Path transactionsFolder;
    private Path settingsFile;
    private Path committedKeyFile;

    public synchronized void load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        rootFolder = StoragePaths.economy(StoragePaths.root(server));
        accountsFolder = StoragePaths.economyAccounts(StoragePaths.root(server));
        transactionsFolder = StoragePaths.economyTransactions(StoragePaths.root(server));
        settingsFile = rootFolder.resolve("settings.json");
        committedKeyFile = rootFolder.resolve("committed_keys.json");

        accounts.clear();
        accountNameIndex.clear();
        transactions.clear();
        idempotencyIndex.clear();
        recentTransactionIds.clear();
        accountStore.reset();
        transactionStore.reset();
        retainedByAccount.clear();
        retentionReferences.clear();
        retentionTracked.clear();
        retainedUnscoped.clear();
        committedKeyTimestamps.clear();
        committedKeyOrder.clear();
        committedKeyStore.reset();
        retentionReady = false;

        loadSettings();
        loadCommittedKeys();
        loadAccounts();
        removeLegacyTreasuryAccount();
        loadTransactions();
        recoverJournal();
        rebuildRetentionIndexAndPrune(true);
        retentionReady = true;
        save();

        SimpleServerUtilities.LOGGER.info(
                "Loaded SSU economy: {} account(s), {} retained transaction record(s), {} compact committed key(s).",
                accounts.size(),
                transactions.size(),
                committedKeyTimestamps.size()
        );
    }

    public synchronized void save() {
        if (rootFolder == null) {
            return;
        }

        settings.normalize();
        accountStore.queueJson(gson, settingsFile, settings);
        committedKeyStore.queueJson(gson, committedKeyFile, committedKeySnapshot());
        for (EconomyAccount account : accounts.values()) {
            accountStore.queueJson(gson, accountFile(account.getPlayerId()), account);
        }
        Set<Path> transactionFiles = new HashSet<>();
        for (EconomyTransactionRecord record : transactions.values()) {
            Path file = transactionFile(record.getTransactionId());
            transactionFiles.add(file.toAbsolutePath().normalize());
            transactionStore.queueJson(gson, file, record);
        }
        transactionStore.queueDeleteMissing(transactionFiles);
    }

    public synchronized EconomySettings settings() {
        return settings;
    }

    /**
     * Changes the number of recent transaction records retained for each
     * participating account. Prepared journal entries are always preserved
     * until recovery has completed.
     */
    public synchronized int setRecentHistoryLimit(int requestedLimit) {
        settings.setRecentHistoryLimit(requestedLimit);
        settings.normalize();
        rebuildRetentionIndexAndPrune(true);
        save();
        return settings.getRecentHistoryLimit();
    }

    public synchronized boolean isEnabled() {
        return settings.isEnabled() && SsuModuleAccess.active("economy");
    }

    public synchronized EconomyAccount ensureAccount(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        EconomyAccount account = ensureAccount(player.getUUID(), player.getName().getString());
        String previousName = account.getLastKnownName();
        account.updateName(player.getName().getString());
        if (!account.getLastKnownName().equals(previousName)) {
            rebuildNameIndex();
            queueAccount(account);
        }
        return account;
    }

    public synchronized EconomyAccount ensureAccount(UUID playerId, String name) {
        return ensureAccount(playerId, name, settings.getStartingBalanceMinor());
    }

    /** Creates or upgrades a non-player account with an explicit initial balance. */
    public synchronized EconomyAccount ensureSystemAccount(UUID accountId, String name) {
        EconomyAccount account = ensureAccount(accountId, name, 0L);
        if (!account.isSystemAccount()) {
            account.markSystemAccount();
            rebuildNameIndex();
            queueAccount(account);
        }
        return account;
    }

    private EconomyAccount ensureAccount(UUID playerId, String name, long initialBalanceMinor) {
        Objects.requireNonNull(playerId, "playerId");
        EconomyAccount account = accounts.get(playerId);
        if (account != null) {
            if (name != null && !name.isBlank()) {
                account.updateName(name);
            }
            index(account);
            return account;
        }

        account = new EconomyAccount(playerId, name, Math.max(0L, initialBalanceMinor));
        accounts.put(playerId, account);
        index(account);
        queueAccount(account);
        return account;
    }

    public synchronized Optional<EconomyAccount> findAccount(UUID playerId) {
        EconomyAccount account = accounts.get(playerId);
        return account == null ? Optional.empty() : Optional.of(account);
    }

    public synchronized Optional<EconomyAccount> findAccountByName(MinecraftServer server, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }

        String name = rawName.trim();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(ensureAccount(online));
        }

        UUID playerId = accountNameIndex.get(name.toLowerCase(Locale.ROOT));
        if (playerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accounts.get(playerId));
    }

    /** Resolves only real player accounts; internal SSU clearing/escrow accounts are excluded. */
    public synchronized Optional<EconomyAccount> findPlayerAccountByName(MinecraftServer server, String rawName) {
        return findAccountByName(server, rawName).filter(account -> !account.isSystemAccount());
    }

    public synchronized Optional<EconomyAccount> findPlayerAccount(UUID playerId) {
        EconomyAccount account = accounts.get(playerId);
        return account == null || account.isSystemAccount() ? Optional.empty() : Optional.of(account);
    }

    public synchronized boolean isSystemAccount(UUID accountId) {
        if (EconomySystemAccounts.isKnown(accountId)) return true;
        EconomyAccount account = accountId == null ? null : accounts.get(accountId);
        return account != null && account.isSystemAccount();
    }

    public synchronized long balance(ServerPlayer player) {
        return ensureAccount(player).getBalanceMinor();
    }

    public synchronized String formattedBalance(ServerPlayer player) {
        return MoneyFormat.format(balance(player), settings);
    }

    @Override
    public synchronized long balance(UUID playerId) {
        return ensureAccount(playerId, "").getBalanceMinor();
    }

    @Override
    public synchronized String format(long amountMinor) {
        return MoneyFormat.format(amountMinor, settings);
    }

    @Override
    public synchronized EconomyResult transfer(
            UUID actorId,
            String actorName,
            UUID sourceId,
            UUID destinationId,
            long amountMinor,
            String module,
            String reason,
            String idempotencyKey
    ) {
        return transferTyped(
                actorId,
                actorName,
                sourceId,
                destinationId,
                amountMinor,
                EconomyTransactionType.TRANSFER,
                module,
                reason,
                idempotencyKey
        );
    }

    /** Module-facing account transfer with an explicit journal transaction type. */
    public synchronized EconomyResult transferTyped(
            UUID actorId,
            String actorName,
            UUID sourceId,
            UUID destinationId,
            long amountMinor,
            EconomyTransactionType type,
            String module,
            String reason,
            String idempotencyKey
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (sourceId == null || destinationId == null) {
            return EconomyResult.failure("account_missing", "Source and destination account ids are required.");
        }
        EconomyAccount source = ensureAccount(sourceId, "");
        EconomyAccount destination = ensureAccount(destinationId, "");
        if (sourceId.equals(destinationId)) {
            return EconomyResult.failure("same_account", "Source and destination accounts must be different.");
        }
        String amountError = validateTransferAmount(amountMinor);
        if (amountError != null) {
            return EconomyResult.failure("invalid_amount", amountError);
        }
        if (source.getBalanceMinor() < amountMinor) {
            return EconomyResult.failure("insufficient_funds", "Insufficient funds.");
        }
        long sourceAfter;
        long destinationAfter;
        try {
            sourceAfter = Math.subtractExact(source.getBalanceMinor(), amountMinor);
            destinationAfter = Math.addExact(destination.getBalanceMinor(), amountMinor);
        } catch (ArithmeticException e) {
            return EconomyResult.failure("overflow", "The balance change is too large.");
        }
        if (destinationAfter > settings.getMaximumBalanceMinor()) {
            return EconomyResult.failure("maximum_balance", "The destination account would exceed the maximum balance.");
        }
        return executeMutation(
                actorId,
                actorName,
                type == null ? EconomyTransactionType.TRANSFER : type,
                source,
                destination,
                amountMinor,
                sourceAfter,
                destinationAfter,
                module,
                reason,
                idempotencyKey
        );
    }

    @Override
    public synchronized EconomyResult credit(
            UUID actorId,
            String actorName,
            UUID destinationId,
            long amountMinor,
            String module,
            String reason,
            String idempotencyKey
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (destinationId == null) {
            return EconomyResult.failure("account_missing", "Destination account id is required.");
        }
        EconomyAccount destination = ensureAccount(destinationId, "");
        if (amountMinor <= 0L) {
            return EconomyResult.failure("invalid_amount", "Amount must be greater than zero.");
        }
        long destinationAfter;
        try {
            destinationAfter = Math.addExact(destination.getBalanceMinor(), amountMinor);
        } catch (ArithmeticException e) {
            return EconomyResult.failure("overflow", "The balance change is too large.");
        }
        if (destinationAfter > settings.getMaximumBalanceMinor()) {
            return EconomyResult.failure("maximum_balance", "The account would exceed the maximum balance.");
        }
        return executeMutation(
                actorId,
                actorName,
                EconomyTransactionType.ADMIN_GIVE,
                null,
                destination,
                amountMinor,
                0L,
                destinationAfter,
                module,
                reason,
                idempotencyKey
        );
    }

    @Override
    public synchronized EconomyResult debit(
            UUID actorId,
            String actorName,
            UUID sourceId,
            long amountMinor,
            String module,
            String reason,
            String idempotencyKey
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (sourceId == null) {
            return EconomyResult.failure("account_missing", "Source account id is required.");
        }
        EconomyAccount source = ensureAccount(sourceId, "");
        if (amountMinor <= 0L) {
            return EconomyResult.failure("invalid_amount", "Amount must be greater than zero.");
        }
        if (source.getBalanceMinor() < amountMinor) {
            return EconomyResult.failure("insufficient_funds", "The account does not contain that amount.");
        }
        return executeMutation(
                actorId,
                actorName,
                EconomyTransactionType.ADMIN_TAKE,
                source,
                null,
                amountMinor,
                source.getBalanceMinor() - amountMinor,
                0L,
                module,
                reason,
                idempotencyKey
        );
    }

    public synchronized EconomyResult transfer(
            ServerPlayer actor,
            EconomyAccount source,
            EconomyAccount destination,
            long amountMinor,
            String reason,
            String idempotencyKey
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (source == null || destination == null) {
            return EconomyResult.failure("account_missing", "One of the economy accounts does not exist.");
        }
        if (source.getPlayerId().equals(destination.getPlayerId())) {
            return EconomyResult.failure("same_account", "You cannot pay yourself.");
        }
        String amountError = validateTransferAmount(amountMinor);
        if (amountError != null) {
            return EconomyResult.failure("invalid_amount", amountError);
        }
        if (source.getBalanceMinor() < amountMinor) {
            return EconomyResult.failure("insufficient_funds", "Insufficient funds.");
        }

        long sourceAfter;
        long destinationAfter;
        try {
            sourceAfter = Math.subtractExact(source.getBalanceMinor(), amountMinor);
            destinationAfter = Math.addExact(destination.getBalanceMinor(), amountMinor);
        } catch (ArithmeticException e) {
            return EconomyResult.failure("overflow", "The balance change is too large.");
        }
        if (destinationAfter > settings.getMaximumBalanceMinor()) {
            return EconomyResult.failure("maximum_balance", "The recipient would exceed the maximum balance.");
        }

        return executeMutation(
                actor,
                EconomyTransactionType.TRANSFER,
                source,
                destination,
                amountMinor,
                sourceAfter,
                destinationAfter,
                "economy",
                reason,
                idempotencyKey
        );
    }

    public synchronized EconomyResult give(
            UUID actorId,
            String actorName,
            EconomyAccount destination,
            long amountMinor,
            String reason
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (destination == null) {
            return EconomyResult.failure("account_missing", "Economy account not found.");
        }
        if (amountMinor <= 0L) {
            return EconomyResult.failure("invalid_amount", "Amount must be greater than zero.");
        }

        long destinationAfter;
        try {
            destinationAfter = Math.addExact(destination.getBalanceMinor(), amountMinor);
        } catch (ArithmeticException e) {
            return EconomyResult.failure("overflow", "The balance change is too large.");
        }
        if (destinationAfter > settings.getMaximumBalanceMinor()) {
            return EconomyResult.failure("maximum_balance", "The account would exceed the maximum balance.");
        }

        return executeMutation(
                actorId,
                actorName,
                EconomyTransactionType.ADMIN_GIVE,
                null,
                destination,
                amountMinor,
                0L,
                destinationAfter,
                "economy_admin",
                reason,
                ""
        );
    }

    public synchronized EconomyResult take(
            UUID actorId,
            String actorName,
            EconomyAccount source,
            long amountMinor,
            String reason
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (source == null) {
            return EconomyResult.failure("account_missing", "Economy account not found.");
        }
        if (amountMinor <= 0L) {
            return EconomyResult.failure("invalid_amount", "Amount must be greater than zero.");
        }
        if (source.getBalanceMinor() < amountMinor) {
            return EconomyResult.failure("insufficient_funds", "The account does not contain that amount.");
        }

        return executeMutation(
                actorId,
                actorName,
                EconomyTransactionType.ADMIN_TAKE,
                source,
                null,
                amountMinor,
                source.getBalanceMinor() - amountMinor,
                0L,
                "economy_admin",
                reason,
                ""
        );
    }

    public synchronized EconomyResult setBalance(
            UUID actorId,
            String actorName,
            EconomyAccount account,
            long newBalanceMinor,
            String reason
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (account == null) {
            return EconomyResult.failure("account_missing", "Economy account not found.");
        }
        if (newBalanceMinor < 0L || newBalanceMinor > settings.getMaximumBalanceMinor()) {
            return EconomyResult.failure("invalid_amount", "Balance is outside the allowed range.");
        }

        long before = account.getBalanceMinor();
        if (newBalanceMinor == before) {
            return EconomyResult.success(null, "Balance was already set to that amount.", before, before);
        }
        long amount = Math.abs(newBalanceMinor - before);
        EconomyAccount source = newBalanceMinor < before ? account : null;
        EconomyAccount destination = newBalanceMinor >= before ? account : null;

        return executeMutation(
                actorId,
                actorName,
                EconomyTransactionType.ADMIN_SET,
                source,
                destination,
                amount,
                source == null ? 0L : newBalanceMinor,
                destination == null ? 0L : newBalanceMinor,
                "economy_admin",
                reason,
                ""
        );
    }

    /**
     * Module-facing exact credit with an explicit transaction type.
     */
    public synchronized EconomyResult creditTyped(
            UUID actorId,
            String actorName,
            UUID destinationId,
            long amountMinor,
            EconomyTransactionType type,
            String module,
            String reason,
            String idempotencyKey
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (destinationId == null) {
            return EconomyResult.failure("account_missing", "Destination account id is required.");
        }
        if (amountMinor <= 0L) {
            return EconomyResult.failure("invalid_amount", "Amount must be greater than zero.");
        }
        EconomyAccount destination = ensureAccount(destinationId, "");
        long destinationAfter;
        try {
            destinationAfter = Math.addExact(destination.getBalanceMinor(), amountMinor);
        } catch (ArithmeticException e) {
            return EconomyResult.failure("overflow", "The balance change is too large.");
        }
        if (destinationAfter > settings.getMaximumBalanceMinor()) {
            return EconomyResult.failure("maximum_balance", "The account would exceed the maximum balance.");
        }
        return executeMutation(
                actorId,
                actorName,
                type == null ? EconomyTransactionType.ADMIN_GIVE : type,
                null,
                destination,
                amountMinor,
                0L,
                destinationAfter,
                module,
                reason,
                idempotencyKey
        );
    }

    /**
     * Module-facing exact debit with an explicit transaction type.
     */
    public synchronized EconomyResult debitTyped(
            UUID actorId,
            String actorName,
            UUID sourceId,
            long amountMinor,
            EconomyTransactionType type,
            String module,
            String reason,
            String idempotencyKey
    ) {
        if (!settings.isEnabled()) {
            return EconomyResult.failure("disabled", "The economy module is disabled.");
        }
        if (sourceId == null) {
            return EconomyResult.failure("account_missing", "Source account id is required.");
        }
        if (amountMinor <= 0L) {
            return EconomyResult.failure("invalid_amount", "Amount must be greater than zero.");
        }
        EconomyAccount source = ensureAccount(sourceId, "");
        if (source.getBalanceMinor() < amountMinor) {
            return EconomyResult.failure("insufficient_funds", "The account does not contain that amount.");
        }
        return executeMutation(
                actorId,
                actorName,
                type == null ? EconomyTransactionType.ADMIN_TAKE : type,
                source,
                null,
                amountMinor,
                source.getBalanceMinor() - amountMinor,
                0L,
                module,
                reason,
                idempotencyKey
        );
    }

    public synchronized Optional<EconomyTransactionRecord> transactionByIdempotencyKey(String rawKey) {
        String key = normalizeIdempotencyKey(rawKey);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        UUID transactionId = idempotencyIndex.get(key);
        return transactionId == null ? Optional.empty() : Optional.ofNullable(transactions.get(transactionId));
    }

    public synchronized boolean isCommittedIdempotencyKey(String rawKey) {
        String key = normalizeIdempotencyKey(rawKey);
        if (key.isEmpty()) return false;
        if (committedKeyTimestamps.containsKey(key)) return true;
        return transactionByIdempotencyKey(key)
                .map(record -> record.getStatus() == EconomyTransactionStatus.COMMITTED)
                .orElse(false);
    }

    public synchronized List<EconomyTransactionRecord> history(UUID playerId, int limit) {
        int bounded = playerId == null
                ? Math.max(1, Math.min(100_000, limit))
                : Math.max(1, Math.min(settings.getRecentHistoryLimit(), limit));
        List<EconomyTransactionRecord> result = new ArrayList<>(bounded);
        Deque<UUID> source = playerId == null ? recentTransactionIds : retainedByAccount.get(playerId);
        if (source == null) return List.of();
        var iterator = source.descendingIterator();
        while (iterator.hasNext() && result.size() < bounded) {
            EconomyTransactionRecord record = transactions.get(iterator.next());
            if (record != null) result.add(record);
        }
        return List.copyOf(result);
    }

    public synchronized EconomyStatistics statistics() {
        long supply = 0L;
        for (EconomyAccount account : accounts.values()) {
            try {
                supply = Math.addExact(supply, account.getBalanceMinor());
            } catch (ArithmeticException e) {
                supply = Long.MAX_VALUE;
                break;
            }
        }

        int prepared = 0;
        int committed = 0;
        for (EconomyTransactionRecord record : transactions.values()) {
            if (record.getStatus() == EconomyTransactionStatus.PREPARED) {
                prepared++;
            } else if (record.getStatus() == EconomyTransactionStatus.COMMITTED) {
                committed++;
            }
        }
        return new EconomyStatistics(accounts.size(), supply, transactions.size(), prepared, committed);
    }

    public synchronized Collection<EconomyAccount> accounts() {
        return accounts.values().stream()
                .map(EconomyAccount::copy)
                .sorted(Comparator.comparing(EconomyAccount::getLastKnownName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Returns only accounts that belong to real players, for player pickers and suggestions. */
    public synchronized Collection<EconomyAccount> playerAccounts() {
        return accounts.values().stream()
                .filter(account -> !account.isSystemAccount())
                .map(EconomyAccount::copy)
                .sorted(Comparator.comparing(EconomyAccount::getLastKnownName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private EconomyResult executeMutation(
            ServerPlayer actor,
            EconomyTransactionType type,
            EconomyAccount source,
            EconomyAccount destination,
            long amountMinor,
            long sourceAfter,
            long destinationAfter,
            String module,
            String reason,
            String idempotencyKey
    ) {
        return executeMutation(
                actor == null ? null : actor.getUUID(),
                actor == null ? "server" : actor.getName().getString(),
                type,
                source,
                destination,
                amountMinor,
                sourceAfter,
                destinationAfter,
                module,
                reason,
                idempotencyKey
        );
    }

    private EconomyResult executeMutation(
            UUID actorId,
            String actorName,
            EconomyTransactionType type,
            EconomyAccount source,
            EconomyAccount destination,
            long amountMinor,
            long sourceAfter,
            long destinationAfter,
            String module,
            String reason,
            String rawIdempotencyKey
    ) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        if (!idempotencyKey.isEmpty() && isCommittedIdempotencyKey(idempotencyKey)) {
            return EconomyResult.failure("duplicate", "This transaction was already completed.");
        }

        UUID transactionId = UUID.randomUUID();
        EconomyTransactionRecord record = EconomyTransactionRecord.prepared(
                transactionId,
                idempotencyKey,
                type,
                module,
                reason,
                actorId,
                actorName,
                source,
                destination,
                amountMinor,
                sourceAfter,
                destinationAfter
        );

        try {
            writeTransactionSynchronously(record);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Could not prepare economy transaction {}.", transactionId, e);
            return EconomyResult.failure("journal_write_failed", "The transaction journal could not be written.");
        }

        EconomyAccount sourceBefore = source == null ? null : source.copy();
        EconomyAccount destinationBefore = destination == null ? null : destination.copy();
        List<SsuTransactionManager.TransactionStep> steps = new ArrayList<>(2);

        if (source != null) {
            steps.add(new AccountMutationStep(
                    source,
                    sourceAfter,
                    record.getSourceRevisionAfter(),
                    sourceBefore
            ));
        }
        if (destination != null && destination != source) {
            steps.add(new AccountMutationStep(
                    destination,
                    destinationAfter,
                    record.getDestinationRevisionAfter(),
                    destinationBefore
            ));
        } else if (destination == source && destination != null) {
            steps.add(new AccountMutationStep(
                    destination,
                    destinationAfter,
                    Math.max(record.getSourceRevisionAfter(), record.getDestinationRevisionAfter()),
                    destinationBefore
            ));
        }

        var execution = SimpleServerUtilities.TRANSACTIONS.execute(module, type.name(), idempotencyKey, steps);
        if (!execution.successful()) {
            record.markRolledBack(execution.error());
            persistTransactionAfterPreparation(record);
            return EconomyResult.failure("mutation_failed", execution.error());
        }

        if (source != null) {
            queueAccount(source);
        }
        if (destination != null) {
            queueAccount(destination);
        }

        record.markCommitted();
        transactions.put(transactionId, record);
        rememberRecent(record);
        if (!idempotencyKey.isEmpty()) {
            idempotencyIndex.put(idempotencyKey, transactionId);
        }
        persistTransactionAfterPreparation(record);

        long currentSource = source == null ? 0L : source.getBalanceMinor();
        long currentDestination = destination == null ? 0L : destination.getBalanceMinor();
        return EconomyResult.success(
                transactionId,
                "Transaction completed.",
                currentSource,
                currentDestination
        );
    }

    private void loadSettings() {
        settings = new EconomySettings();
        if (Files.exists(settingsFile)) {
            try {
                EconomySettings loaded = JsonStorage.read(gson, settingsFile, EconomySettings.class);
                if (loaded != null) {
                    settings = loaded;
                }
            } catch (Exception e) {
                JsonStorage.archiveBrokenFile(settingsFile);
                SimpleServerUtilities.LOGGER.error("Failed to load economy settings; defaults will be used.", e);
            }
        }
        settings.normalize();
        accountStore.discoverFile(settingsFile);
    }

    private void loadCommittedKeys() {
        if (committedKeyFile == null || !Files.exists(committedKeyFile)) return;
        try {
            committedKeyStore.discoverFile(committedKeyFile);
            EconomyCommittedKeyIndex index = JsonStorage.read(
                    gson, committedKeyFile, EconomyCommittedKeyIndex.class);
            if (index == null) return;
            index.normalize();
            for (EconomyCommittedKeyIndex.Entry entry : index.entries) {
                rememberCommittedKey(entry.key, entry.committedAtEpochMilli);
            }
        } catch (Exception exception) {
            Path archived = JsonStorage.archiveBrokenFile(committedKeyFile);
            SimpleServerUtilities.LOGGER.error(
                    "Failed to load compact economy idempotency index. Archived as {}.", archived, exception);
        }
    }

    private EconomyCommittedKeyIndex committedKeySnapshot() {
        EconomyCommittedKeyIndex index = new EconomyCommittedKeyIndex();
        for (String key : committedKeyOrder) {
            index.entries.add(new EconomyCommittedKeyIndex.Entry(
                    key, committedKeyTimestamps.getOrDefault(key, 0L)));
        }
        index.normalize();
        return index;
    }

    private void rememberCommittedKey(String rawKey, long committedAtEpochMilli) {
        String key = normalizeIdempotencyKey(rawKey);
        if (key.isEmpty()) return;
        committedKeyOrder.remove(key);
        committedKeyOrder.addLast(key);
        committedKeyTimestamps.put(key, Math.max(0L, committedAtEpochMilli));
        while (committedKeyOrder.size() > EconomyCommittedKeyIndex.MAX_KEYS) {
            String expired = committedKeyOrder.removeFirst();
            committedKeyTimestamps.remove(expired);
        }
        SimpleServerUtilities.TRANSACTIONS.rememberCommitted(key);
    }

    private void loadAccounts() {
        accountStore.discover(accountsFolder);
        try {
            for (Path file : JsonStorage.listJsonFiles(accountsFolder)) {
                try {
                    EconomyAccount account = JsonStorage.read(gson, file, EconomyAccount.class);
                    UUID fallbackId = UUID.fromString(StoragePaths.fileBaseName(file));
                    account.normalize(fallbackId);
                    accounts.put(account.getPlayerId(), account);
                    index(account);
                } catch (Exception e) {
                    JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load economy account {}.", file, e);
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to scan economy accounts.", e);
        }
    }

    private void removeLegacyTreasuryAccount() {
        EconomyAccount removed = accounts.remove(LEGACY_TREASURY_ID);
        if (removed == null) return;
        rebuildNameIndex();
        Path file = accountFile(LEGACY_TREASURY_ID);
        Path archived = JsonStorage.archiveLegacyFile(file);
        SimpleServerUtilities.LOGGER.info(
                "Removed the experimental dev2 treasury account; {} minor unit(s) were retired from circulation. Archived account: {}",
                removed.getBalanceMinor(), archived == null ? "none" : archived);
    }

    private void loadTransactions() {
        transactionStore.discover(transactionsFolder);
        List<EconomyTransactionRecord> loaded = new ArrayList<>();
        try {
            for (Path file : JsonStorage.listJsonFiles(transactionsFolder)) {
                try {
                    EconomyTransactionRecord record = JsonStorage.read(gson, file, EconomyTransactionRecord.class);
                    if (record == null || record.getTransactionId() == null) {
                        throw new IllegalArgumentException("Transaction record has no id.");
                    }
                    record.normalize();
                    loaded.add(record);
                } catch (Exception e) {
                    JsonStorage.archiveBrokenFile(file);
                    SimpleServerUtilities.LOGGER.error("Failed to load economy transaction {}.", file, e);
                }
            }
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error("Failed to scan economy transactions.", e);
        }

        loaded.sort(Comparator.comparingLong(EconomyTransactionRecord::getCreatedAtEpochMilli));
        for (EconomyTransactionRecord record : loaded) {
            transactions.put(record.getTransactionId(), record);
            rememberRecent(record);
            if (!record.getIdempotencyKey().isEmpty()
                    && record.getStatus() == EconomyTransactionStatus.COMMITTED) {
                idempotencyIndex.put(record.getIdempotencyKey(), record.getTransactionId());
                rememberCommittedKey(record.getIdempotencyKey(), record.getCompletedAtEpochMilli());
            }
        }
    }

    private void recoverJournal() {
        for (EconomyTransactionRecord record : transactions.values()) {
            if (record.getStatus() != EconomyTransactionStatus.PREPARED
                    && record.getStatus() != EconomyTransactionStatus.COMMITTED) {
                continue;
            }

            boolean changed = false;
            changed |= reconcileAccount(
                    record.getSourceId(),
                    record.getSourceName(),
                    record.getSourceRevisionAfter(),
                    record.getSourceBalanceAfter()
            );
            changed |= reconcileAccount(
                    record.getDestinationId(),
                    record.getDestinationName(),
                    record.getDestinationRevisionAfter(),
                    record.getDestinationBalanceAfter()
            );

            if (record.getStatus() == EconomyTransactionStatus.PREPARED) {
                record.markCommitted();
                changed = true;
            }
            if (changed) {
                persistTransactionAfterPreparation(record);
            }
        }
    }

    private boolean reconcileAccount(UUID playerId, String name, long afterRevision, long afterBalance) {
        if (playerId == null || afterRevision <= 0L) {
            return false;
        }
        EconomyAccount account = ensureAccount(playerId, name);
        if (account.getRevision() >= afterRevision) {
            return false;
        }
        account.apply(afterBalance, afterRevision, name);
        queueAccount(account);
        return true;
    }

    private String validateTransferAmount(long amountMinor) {
        if (amountMinor < settings.getMinimumTransferMinor()) {
            return "Amount is below the minimum transfer amount of "
                    + MoneyFormat.format(settings.getMinimumTransferMinor(), settings) + ".";
        }
        if (amountMinor > settings.getMaximumTransferMinor()) {
            return "Amount exceeds the maximum transfer amount of "
                    + MoneyFormat.format(settings.getMaximumTransferMinor(), settings) + ".";
        }
        return null;
    }

    private void queueAccount(EconomyAccount account) {
        if (account == null || accountsFolder == null) {
            return;
        }
        accounts.put(account.getPlayerId(), account);
        index(account);
        accountStore.queueJson(gson, accountFile(account.getPlayerId()), account);
    }

    private void persistTransactionAfterPreparation(EconomyTransactionRecord record) {
        transactions.put(record.getTransactionId(), record);
        rememberRecent(record);
        if (record.getStatus() == EconomyTransactionStatus.COMMITTED
                && !record.getIdempotencyKey().isEmpty()) {
            idempotencyIndex.put(record.getIdempotencyKey(), record.getTransactionId());
            rememberCommittedKey(record.getIdempotencyKey(), record.getCompletedAtEpochMilli());
        }
        try {
            writeTransactionSynchronously(record);
        } catch (IOException e) {
            SimpleServerUtilities.LOGGER.error(
                    "Failed to update economy transaction journal {}; queuing retry.",
                    record.getTransactionId(),
                    e
            );
            transactionStore.queueJson(gson, transactionFile(record.getTransactionId()), record);
        }
        if (retentionReady && record.getStatus() != EconomyTransactionStatus.PREPARED) {
            retainCompletedTransaction(record);
        }
    }

    private void writeTransactionSynchronously(EconomyTransactionRecord record) throws IOException {
        JsonStorage.write(gson, transactionFile(record.getTransactionId()), record);
        transactionStore.discoverFile(transactionFile(record.getTransactionId()));
    }

    private void rememberRecent(EconomyTransactionRecord record) {
        UUID id = record.getTransactionId();
        recentTransactionIds.remove(id);
        recentTransactionIds.addLast(id);
        int maximum = Math.max(settings.getRecentHistoryLimit(), Math.min(100_000,
                settings.getRecentHistoryLimit() * Math.max(1, accounts.size())));
        while (recentTransactionIds.size() > maximum) {
            recentTransactionIds.removeFirst();
        }
    }

    /**
     * Rebuilds the bounded retention index after startup or an administrator
     * changes the configured history size. Normal transactions are maintained
     * incrementally afterwards and do not rescan the whole journal.
     */
    private void rebuildRetentionIndexAndPrune(boolean logResult) {
        retainedByAccount.clear();
        retentionReferences.clear();
        retentionTracked.clear();
        retainedUnscoped.clear();
        if (transactionsFolder == null || transactions.isEmpty()) return;

        int before = transactions.size();
        List<EconomyTransactionRecord> ordered = new ArrayList<>(transactions.values());
        ordered.sort(Comparator
                .comparingLong(EconomyManager::retentionTimestamp)
                .thenComparing(record -> record.getTransactionId().toString()));
        for (EconomyTransactionRecord record : ordered) {
            if (record.getStatus() != EconomyTransactionStatus.PREPARED) {
                retainCompletedTransaction(record);
            }
        }

        Set<UUID> keep = new HashSet<>(retentionTracked);
        for (EconomyTransactionRecord record : transactions.values()) {
            if (record.getStatus() == EconomyTransactionStatus.PREPARED) {
                keep.add(record.getTransactionId());
            }
        }
        transactions.entrySet().removeIf(entry -> !keep.contains(entry.getKey()));
        rebuildTransactionIndexes();

        Set<Path> retainedFiles = new HashSet<>();
        for (UUID id : transactions.keySet()) {
            retainedFiles.add(transactionFile(id).toAbsolutePath().normalize());
        }
        int queuedDeletes = transactionStore.queueDeleteMissing(retainedFiles);
        if (logResult && (before != transactions.size() || queuedDeletes > 0)) {
            SimpleServerUtilities.LOGGER.info(
                    "Pruned SSU economy history from {} to {} record(s); retention is {} per account.",
                    before, transactions.size(), settings.getRecentHistoryLimit());
        }
    }

    /** Adds one completed transaction to the per-account bounded indexes. */
    private void retainCompletedTransaction(EconomyTransactionRecord record) {
        UUID transactionId = record.getTransactionId();
        if (transactionId == null || !retentionTracked.add(transactionId)) return;
        int limit = settings.getRecentHistoryLimit();
        Set<UUID> participants = transactionParticipants(record);
        if (participants.isEmpty()) {
            retainedUnscoped.addLast(transactionId);
            incrementRetentionReference(transactionId);
            while (retainedUnscoped.size() > limit) {
                decrementRetentionReference(retainedUnscoped.removeFirst());
            }
            return;
        }
        for (UUID participant : participants) {
            Deque<UUID> accountHistory = retainedByAccount.computeIfAbsent(
                    participant, ignored -> new ArrayDeque<>());
            accountHistory.addLast(transactionId);
            incrementRetentionReference(transactionId);
            while (accountHistory.size() > limit) {
                decrementRetentionReference(accountHistory.removeFirst());
            }
        }
    }

    private void incrementRetentionReference(UUID transactionId) {
        retentionReferences.merge(transactionId, 1, Integer::sum);
    }

    private void decrementRetentionReference(UUID transactionId) {
        Integer current = retentionReferences.get(transactionId);
        if (current == null || current <= 1) {
            retentionReferences.remove(transactionId);
            removeExpiredTransaction(transactionId);
        } else {
            retentionReferences.put(transactionId, current - 1);
        }
    }

    private void removeExpiredTransaction(UUID transactionId) {
        EconomyTransactionRecord record = transactions.get(transactionId);
        if (record == null || record.getStatus() == EconomyTransactionStatus.PREPARED) return;
        transactions.remove(transactionId);
        retentionTracked.remove(transactionId);
        recentTransactionIds.remove(transactionId);
        if (!record.getIdempotencyKey().isEmpty()
                && transactionId.equals(idempotencyIndex.get(record.getIdempotencyKey()))) {
            idempotencyIndex.remove(record.getIdempotencyKey());
        }
        Path file = transactionFile(transactionId);
        transactionStore.forget(file);
        SimpleServerUtilities.STORAGE.queueDelete(file);
    }

    private void rebuildTransactionIndexes() {
        idempotencyIndex.clear();
        recentTransactionIds.clear();
        List<EconomyTransactionRecord> ordered = new ArrayList<>(transactions.values());
        ordered.sort(Comparator.comparingLong(EconomyManager::retentionTimestamp));
        for (EconomyTransactionRecord record : ordered) {
            rememberRecent(record);
            if (record.getStatus() == EconomyTransactionStatus.COMMITTED
                    && !record.getIdempotencyKey().isEmpty()) {
                idempotencyIndex.put(record.getIdempotencyKey(), record.getTransactionId());
            }
        }
    }

    private static Set<UUID> transactionParticipants(EconomyTransactionRecord record) {
        Set<UUID> participants = new HashSet<>(3);
        if (record.getActorId() != null) participants.add(record.getActorId());
        if (record.getSourceId() != null) participants.add(record.getSourceId());
        if (record.getDestinationId() != null) participants.add(record.getDestinationId());
        return participants;
    }

    private static long retentionTimestamp(EconomyTransactionRecord record) {
        return Math.max(record.getCreatedAtEpochMilli(), record.getCompletedAtEpochMilli());
    }


    private static String normalizeIdempotencyKey(String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        return key.length() <= 256 ? key : key.substring(0, 256);
    }

    private Path accountFile(UUID playerId) {
        return StoragePaths.jsonFile(accountsFolder, playerId.toString());
    }

    private Path transactionFile(UUID transactionId) {
        return StoragePaths.jsonFile(transactionsFolder, transactionId.toString());
    }

    private void index(EconomyAccount account) {
        if (account.getLastKnownName() != null && !account.getLastKnownName().isBlank()) {
            accountNameIndex.put(account.getLastKnownName().toLowerCase(Locale.ROOT), account.getPlayerId());
        }
    }

    private void rebuildNameIndex() {
        accountNameIndex.clear();
        for (EconomyAccount account : accounts.values()) {
            index(account);
        }
    }

    private static final class AccountMutationStep implements SsuTransactionManager.TransactionStep {
        private final EconomyAccount account;
        private final long afterBalance;
        private final long afterRevision;
        private final EconomyAccount before;

        private AccountMutationStep(
                EconomyAccount account,
                long afterBalance,
                long afterRevision,
                EconomyAccount before
        ) {
            this.account = account;
            this.afterBalance = afterBalance;
            this.afterRevision = afterRevision;
            this.before = before;
        }

        @Override
        public void apply() {
            account.apply(afterBalance, afterRevision, account.getLastKnownName());
        }

        @Override
        public void rollback() {
            account.apply(
                    before.getBalanceMinor(),
                    before.getRevision(),
                    before.getLastKnownName()
            );
        }
    }
}
