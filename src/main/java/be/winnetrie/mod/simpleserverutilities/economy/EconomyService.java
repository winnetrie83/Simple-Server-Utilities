package be.winnetrie.mod.simpleserverutilities.economy;

import java.util.UUID;

/**
 * Stable server-side economy contract for SSU modules.
 *
 * <p>Auction House, mail, NPC shops, quests and minigames should depend on
 * this interface rather than the built-in EconomyManager. The active {@link EconomyProvider} supplies this contract, so a future item-backed or
 * external adapter can be introduced without changing portable consumers.</p>
 */
public interface EconomyService {

    boolean isEnabled();

    long balance(UUID playerId);

    String format(long amountMinor);

    EconomyResult transfer(
            UUID actorId,
            String actorName,
            UUID sourceId,
            UUID destinationId,
            long amountMinor,
            String module,
            String reason,
            String idempotencyKey
    );

    EconomyResult credit(
            UUID actorId,
            String actorName,
            UUID destinationId,
            long amountMinor,
            String module,
            String reason,
            String idempotencyKey
    );

    EconomyResult debit(
            UUID actorId,
            String actorName,
            UUID sourceId,
            long amountMinor,
            String module,
            String reason,
            String idempotencyKey
    );
}
