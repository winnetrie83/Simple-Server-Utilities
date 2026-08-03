package be.winnetrie.mod.simpleserverutilities.economy;

public final class EconomySettings {

    private int schemaVersion = 2;
    private boolean enabled = true;
    private String currencyName = "euro";
    private String currencySymbol = "€";
    private int decimalPlaces = 2;
    private long startingBalanceMinor = 0L;
    private long maximumBalanceMinor = 9_000_000_000_000_000L;
    private long minimumTransferMinor = 1L;
    private long maximumTransferMinor = 1_000_000_000_000L;
    private int recentHistoryLimit = 50;

    public void normalize() {
        if (schemaVersion < 2) {
            recentHistoryLimit = 50;
        }
        schemaVersion = Math.max(2, schemaVersion);
        currencyName = currencyName == null || currencyName.isBlank() ? "currency" : currencyName.trim();
        currencySymbol = currencySymbol == null ? "" : currencySymbol.trim();
        decimalPlaces = Math.max(0, Math.min(4, decimalPlaces));
        startingBalanceMinor = Math.max(0L, startingBalanceMinor);
        maximumBalanceMinor = Math.max(startingBalanceMinor, maximumBalanceMinor);
        minimumTransferMinor = Math.max(1L, minimumTransferMinor);
        maximumTransferMinor = Math.max(minimumTransferMinor, maximumTransferMinor);
        recentHistoryLimit = Math.max(1, Math.min(1_000, recentHistoryLimit));
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public long getStartingBalanceMinor() {
        return startingBalanceMinor;
    }

    public long getMaximumBalanceMinor() {
        return maximumBalanceMinor;
    }

    public long getMinimumTransferMinor() {
        return minimumTransferMinor;
    }

    public long getMaximumTransferMinor() {
        return maximumTransferMinor;
    }

    public int getRecentHistoryLimit() {
        return recentHistoryLimit;
    }

    public void setRecentHistoryLimit(int recentHistoryLimit) {
        this.recentHistoryLimit = Math.max(1, Math.min(1_000, recentHistoryLimit));
    }
}
