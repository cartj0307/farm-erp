package com.farmerp.common;

public final class Validator {

    private Validator() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value.trim();
    }

    public static double requirePositive(double value, String fieldName) {
        if (Double.isNaN(value) || value <= 0) {
            throw new ValidationException(fieldName + " must be more than 0.");
        }
        return value;
    }

    public static long requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new ValidationException(fieldName + " must be more than 0.");
        }
        return value;
    }

    public static double requireNonNegative(double value, String fieldName) {
        if (Double.isNaN(value) || value < 0) {
            throw new ValidationException(fieldName + " can't be negative.");
        }
        return value;
    }

    public static double requireRange(double value, double min, double max, String fieldName) {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new ValidationException(fieldName + " must be between " + trim(min) + " and " + trim(max) + ".");
        }
        return value;
    }

    // Parses a number typed in a text box
    public static double parseNumber(String text, String fieldName) {
        String value = requireText(text, fieldName).replace(",", "");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(fieldName + " must be a number.");
        }
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
