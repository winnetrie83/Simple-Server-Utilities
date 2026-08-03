package be.winnetrie.mod.simpleserverutilities.content;

import be.winnetrie.mod.simpleserverutilities.core.transaction.SsuTransactionManager;

/** A validated reversible action ready for a shared SSU transaction. */
public record PreparedContentAction(String description, SsuTransactionManager.TransactionStep step) {
    public PreparedContentAction {
        description = description == null ? "" : description;
        if (step == null) throw new IllegalArgumentException("Prepared content action step is required.");
    }
}
