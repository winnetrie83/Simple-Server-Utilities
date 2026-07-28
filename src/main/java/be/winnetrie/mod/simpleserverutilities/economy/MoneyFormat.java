package be.winnetrie.mod.simpleserverutilities.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class MoneyFormat {

    private static final Locale BELGIAN_DUTCH = Locale.forLanguageTag("nl-BE");

    private MoneyFormat() {
    }

    public static long parseMinor(String raw, EconomySettings settings) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Amount cannot be empty.");
        }

        String normalized = raw.trim()
                .replace(" ", "")
                .replace(settings.getCurrencySymbol(), "");

        int comma = normalized.lastIndexOf(',');
        int dot = normalized.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) {
                normalized = normalized.replace(".", "").replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (comma >= 0) {
            normalized = normalized.replace(',', '.');
        } else if (dot >= 0) {
            int fractionalDigits = normalized.length() - dot - 1;
            if (fractionalDigits > settings.getDecimalPlaces()) {
                normalized = normalized.replace(".", "");
            }
        }

        BigDecimal major;
        try {
            major = new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount: " + raw);
        }

        if (major.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative.");
        }

        BigDecimal scale = BigDecimal.TEN.pow(settings.getDecimalPlaces());
        try {
            return major.multiply(scale)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Amount supports at most " + settings.getDecimalPlaces() + " decimal place(s)."
            );
        }
    }

    public static String format(long minorUnits, EconomySettings settings) {
        int decimals = settings.getDecimalPlaces();
        BigDecimal value = BigDecimal.valueOf(minorUnits, decimals);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(BELGIAN_DUTCH);
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            pattern.append("0".repeat(decimals));
        }

        DecimalFormat format = new DecimalFormat(pattern.toString(), symbols);
        format.setRoundingMode(RoundingMode.UNNECESSARY);
        String number = format.format(value);
        String symbol = settings.getCurrencySymbol();
        return symbol.isBlank() ? number : symbol + " " + number;
    }
}
