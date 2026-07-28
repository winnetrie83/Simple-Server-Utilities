package be.winnetrie.mod.simpleserverutilities.economy;

import java.util.UUID;

public record EconomyResult(
        boolean successful,
        String code,
        String message,
        UUID transactionId,
        long sourceBalanceMinor,
        long destinationBalanceMinor
) {
    public static EconomyResult success(
            UUID transactionId,
            String message,
            long sourceBalance,
            long destinationBalance
    ) {
        return new EconomyResult(true, "success", message, transactionId, sourceBalance, destinationBalance);
    }

    public static EconomyResult failure(String code, String message) {
        return new EconomyResult(false, code, message, null, 0L, 0L);
    }
}
