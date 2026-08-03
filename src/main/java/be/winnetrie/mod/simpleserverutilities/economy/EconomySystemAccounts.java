package be.winnetrie.mod.simpleserverutilities.economy;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/** Stable IDs for internal SSU economy accounts that must never be treated as players. */
public final class EconomySystemAccounts {
    public static final UUID MAIL_ESCROW = UUID.nameUUIDFromBytes(
            "simpleserverutilities:mail_escrow".getBytes(StandardCharsets.UTF_8));
    public static final UUID AUCTION_HOUSE_TAX = UUID.nameUUIDFromBytes(
            "simpleserverutilities:auction_house_clearing".getBytes(StandardCharsets.UTF_8));

    private static final Set<UUID> KNOWN = Set.of(MAIL_ESCROW, AUCTION_HOUSE_TAX);

    private EconomySystemAccounts() {}

    public static boolean isKnown(UUID accountId) {
        return accountId != null && KNOWN.contains(accountId);
    }
}
