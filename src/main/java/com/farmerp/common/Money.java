package com.farmerp.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

// Money is kept as whole cents (long) everywhere
public final class Money {

    private static final NumberFormat FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private Money() {
    }

    // "$1,250.75" or "1250.75" -> 125075
    public static long parseCents(String text, String fieldName) {
        String value = Validator.requireText(text, fieldName).replace("$", "").replace(",", "").trim();
        BigDecimal dollars;
        try {
            dollars = new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a dollar amount.");
        }
        if (dollars.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(fieldName + " can't have more than 2 decimal places.");
        }
        return dollars.movePointRight(2).longValueExact();
    }

    // 125075 -> "$1,250.75"
    public static String format(long cents) {
        synchronized (FORMAT) {
            return FORMAT.format(BigDecimal.valueOf(cents, 2));
        }
    }

    // Rounds half up to the nearest cent
    public static long divide(long cents, double divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("Divide by zero");
        }
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
