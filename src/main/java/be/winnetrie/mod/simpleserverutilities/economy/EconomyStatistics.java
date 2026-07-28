package be.winnetrie.mod.simpleserverutilities.economy;

public record EconomyStatistics(
        int accounts,
        long totalSupplyMinor,
        int loadedTransactions,
        int preparedTransactions,
        int committedTransactions
) {
}
