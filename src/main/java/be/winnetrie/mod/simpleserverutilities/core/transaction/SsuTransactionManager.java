package be.winnetrie.mod.simpleserverutilities.core.transaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;

/**
 * Small reversible transaction coordinator shared by value-moving SSU modules.
 *
 * <p>The coordinator only deals with in-memory steps. Modules remain responsible
 * for writing a durable journal before applying value mutations. This separation
 * lets economy, mail, auction escrow and inventory transactions share the same
 * rollback behavior without forcing one storage format on every module.</p>
 */
public final class SsuTransactionManager {

    private static final int COMPLETED_KEY_LIMIT = 10_000;

    private final Set<String> inFlightKeys = new HashSet<>();
    private final Set<String> completedKeys = new HashSet<>();
    private final Deque<String> completedOrder = new ArrayDeque<>();

    public synchronized TransactionResult execute(
            String module,
            String type,
            String idempotencyKey,
            List<? extends TransactionStep> steps
    ) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(steps, "steps");

        String normalizedKey = normalizeKey(idempotencyKey);
        if (!normalizedKey.isEmpty()) {
            if (completedKeys.contains(normalizedKey)) {
                return TransactionResult.duplicate(normalizedKey);
            }
            if (!inFlightKeys.add(normalizedKey)) {
                return TransactionResult.busy(normalizedKey);
            }
        }

        UUID transactionId = UUID.randomUUID();
        List<TransactionStep> applied = new ArrayList<>(steps.size());

        try {
            for (TransactionStep step : steps) {
                TransactionStep safeStep = Objects.requireNonNull(step, "transaction step");
                safeStep.apply();
                applied.add(safeStep);
            }

            if (!normalizedKey.isEmpty()) {
                rememberCompleted(normalizedKey);
            }
            return TransactionResult.success(transactionId, normalizedKey);
        } catch (Exception failure) {
            boolean rollbackClean = rollback(applied, failure);
            return rollbackClean
                    ? TransactionResult.failed(transactionId, normalizedKey, failure.getMessage())
                    : TransactionResult.rollbackFailed(transactionId, normalizedKey, failure.getMessage());
        } finally {
            if (!normalizedKey.isEmpty()) {
                inFlightKeys.remove(normalizedKey);
            }
        }
    }

    public synchronized void rememberCommitted(String idempotencyKey) {
        String normalizedKey = normalizeKey(idempotencyKey);
        if (!normalizedKey.isEmpty()) {
            rememberCompleted(normalizedKey);
        }
    }

    public synchronized void clear() {
        inFlightKeys.clear();
        completedKeys.clear();
        completedOrder.clear();
    }

    public synchronized int completedKeyCount() {
        return completedKeys.size();
    }

    private boolean rollback(List<TransactionStep> applied, Exception originalFailure) {
        boolean clean = true;
        for (int i = applied.size() - 1; i >= 0; i--) {
            try {
                applied.get(i).rollback();
            } catch (Exception rollbackFailure) {
                clean = false;
                SimpleServerUtilities.LOGGER.error(
                        "SSU transaction rollback step failed after transaction error: {}",
                        originalFailure.getMessage(),
                        rollbackFailure
                );
            }
        }
        return clean;
    }

    private void rememberCompleted(String normalizedKey) {
        if (!completedKeys.add(normalizedKey)) {
            return;
        }
        completedOrder.addLast(normalizedKey);
        while (completedOrder.size() > COMPLETED_KEY_LIMIT) {
            String expired = completedOrder.removeFirst();
            completedKeys.remove(expired);
        }
    }

    private static String normalizeKey(String key) {
        String normalized = key == null ? "" : key.trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    public interface TransactionStep {
        void apply() throws Exception;

        void rollback() throws Exception;
    }

    public record TransactionResult(
            UUID transactionId,
            String idempotencyKey,
            Status status,
            String error
    ) {
        static TransactionResult success(UUID id, String key) {
            return new TransactionResult(id, key, Status.SUCCESS, "");
        }

        static TransactionResult duplicate(String key) {
            return new TransactionResult(null, key, Status.DUPLICATE, "Transaction was already committed.");
        }

        static TransactionResult busy(String key) {
            return new TransactionResult(null, key, Status.BUSY, "Transaction is already being processed.");
        }

        static TransactionResult failed(UUID id, String key, String error) {
            return new TransactionResult(id, key, Status.FAILED, error == null ? "Transaction failed." : error);
        }

        static TransactionResult rollbackFailed(UUID id, String key, String error) {
            String message = error == null ? "Transaction failed and rollback was incomplete." : error;
            return new TransactionResult(id, key, Status.ROLLBACK_FAILED, message);
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }
    }

    public enum Status {
        SUCCESS,
        DUPLICATE,
        BUSY,
        FAILED,
        ROLLBACK_FAILED
    }
}
