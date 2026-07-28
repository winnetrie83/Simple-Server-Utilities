package be.winnetrie.mod.simpleserverutilities.economy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
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
public final class EconomyManager implements EconomyService {

    private static final int MAX_RECENT_IN_MEMORY = 5_000;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, EconomyAccount> accounts = new HashMap<>();
    private final Map<String, UUID> accountNameIndex = new HashMap<>();
    private final Map<UUID, EconomyTransactionRecord> transactions = new HashMap<>();
    private final Map<String, UUID> idempotencyIndex = new HashMap<>();
    private final Deque<UUID> recentTransactionIds = new ArrayDeque<>();
    private final DirtyJsonRecordStore accountStore = new DirtyJsonRecordStore();
    private final DirtyJsonRecordStore transactionStore = new DirtyJsonRecordStore();

    private EconomySettings settings = new EconomySettings();
    private Path rootFolder;
    private Path accountsFolder;
    private Path transactionsFolder;
    private Path settingsFile;

    public synchronized void load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        rootFolder = StoragePaths.economy(StoragePaths.root(server));
        accountsFolder = StoragePaths.economyAccounts(StoragePaths.root(server));
        transactionsFolder = StoragePaths.economyTransactions(StoragePaths.root(server));
        settingsFile = rootFolder.resolve("settings.json");

        accounts.clear();
        accountNameIndex.clear();
        transactions.clear();
        idempotencyIndex.clear();
        recentTransactionIds.clear();
        accountStore.reset();
        transactionStore.reset();
        SimpleServerUtilities.TRANSACTIONS.clear();

        loadSettings();
        loadAccounts();
        loadTransactions();
        recoverJournal();
        save();

        SimpleServerUtilities.LOGGER.info(
                "Loaded SSU economy: {} account(s), {} transaction record(s).",
                accounts.size(),
                transactions.size()
        );
    }

    public synchronized void save() {
        if (rootFolder == null) {
            return;
        }

        settings.normalize();
        accountStore.queueJson(gson, settingsFile, settings);
        for (EconomyAccount account : accounts.values()) {
            accountStore.queueJson(gson, accountFile(account.getPlayerId()), account);
        }
        for (EconomyTransactionRecord record : transactions.values()) {
            transactionStore.queueJson(gson, transactionFile(record.getTransactionId()), record);
        }
    }

    public synchronized EconomySettings settings() {
        return settings;
    }

    public synchronized boolean isEnabled() {
        return settings.isEnabled();
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
        Objects.requireNonNull(playerId, "playerId");
        EconomyAccount account = accounts.get(playerId);
        if (account != null) {
            if (name != null && !name.isBlank()) {
                account.updateName(name);
            }
            index(account);
            return account;
        }

        account = new EconomyAccount(playerId, name, settings.getStartingBalanceMinor());
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
                EconomyTransactionType.TRANSFER,
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
        String key = rawKey == null ? "" : rawKey.trim();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        UUID transactionId = idempotencyIndex.get(key);
        return transactionId == null ? Optional.empty() : Optional.ofNullable(transactions.get(transactionId));
    }

    public synchronized boolean isCommittedIdempotencyKey(String rawKey) {
        return transactionByIdempotencyKey(rawKey)
                .map(record -> record.getStatus() == EconomyTransactionStatus.COMMITTED)
                .orElse(false);
    }

    public synchronized List<EconomyTransactionRecord> history(UUID playerId, int limit) {
        int bounded = Math.max(1, Math.min(100, limit));
        List<EconomyTransactionRecord> result = new ArrayList<>(bounded);
        var iterator = recentTransactionIds.descendingIterator();
        while (iterator.hasNext() && result.size() < bounded) {
            EconomyTransactionRecord record = transactions.get(iterator.next());
            if (record == null) {
                continue;
            }
            if (playerId == null
                    || playerId.equals(record.getSourceId())
                    || playerId.equals(record.getDestinationId())
                    || playerId.equals(record.getActorId())) {
                result.add(record);
            }
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
        String idempotencyKey = rawIdempotencyKey == null ? "" : rawIdempotencyKey.trim();
        if (!idempotencyKey.isEmpty()) {
            UUID previousId = idempotencyIndex.get(idempotencyKey);
            if (previousId != null) {
                EconomyTransactionRecord previous = transactions.get(previousId);
                if (previous != null && previous.getStatus() == EconomyTransactionStatus.COMMITTED) {
                    return EconomyResult.failure("duplicate", "This transaction was already completed.");
                }
            }
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
                SimpleServerUtilities.TRANSACTIONS.rememberCommitted(record.getIdempotencyKey());
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
            SimpleServerUtilities.TRANSACTIONS.rememberCommitted(record.getIdempotencyKey());
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
    }

    private void writeTransactionSynchronously(EconomyTransactionRecord record) throws IOException {
        JsonStorage.write(gson, transactionFile(record.getTransactionId()), record);
        transactionStore.discoverFile(transactionFile(record.getTransactionId()));
    }

    private void rememberRecent(EconomyTransactionRecord record) {
        UUID id = record.getTransactionId();
        recentTransactionIds.remove(id);
        recentTransactionIds.addLast(id);
        while (recentTransactionIds.size() > MAX_RECENT_IN_MEMORY) {
            recentTransactionIds.removeFirst();
        }
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
