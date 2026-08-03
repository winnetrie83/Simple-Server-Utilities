package be.winnetrie.mod.simpleserverutilities.network;

/** Shared, allocation-light payload normalization helpers. */
public final class PayloadBounds {
    private PayloadBounds() {
    }

    public static String string(String value, int maximum) {
        String safe = value == null ? "" : value;
        int boundedMaximum = Math.max(0, maximum);
        return safe.length() <= boundedMaximum ? safe : safe.substring(0, boundedMaximum);
    }

    public static String trimmedString(String value, int maximum) {
        return string(value == null ? "" : value.trim(), maximum);
    }

    public static int size(int value, int maximum) {
        return Math.max(0, Math.min(Math.max(0, maximum), value));
    }

    public static long nonNegative(long value) {
        return Math.max(0L, value);
    }
}
