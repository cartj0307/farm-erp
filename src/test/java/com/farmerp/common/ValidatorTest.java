package com.farmerp.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    @Test
    void requireTextTrims() {
        assertEquals("North", Validator.requireText("  North ", "Name"));
        assertThrows(ValidationException.class, () -> Validator.requireText(" ", "Name"));
    }

    @Test
    void requirePositiveBlocksZeroAndNegative() {
        assertThrows(ValidationException.class, () -> Validator.requirePositive(0.0, "Acres"));
        assertThrows(ValidationException.class, () -> Validator.requirePositive(-5L, "Amount"));
        assertEquals(12.5, Validator.requirePositive(12.5, "Acres"));
    }

    @Test
    void requireRangeChecksPh() {
        assertEquals(6.5, Validator.requireRange(6.5, 0, 14, "pH"));
        ValidationException e = assertThrows(ValidationException.class,
                () -> Validator.requireRange(15, 0, 14, "pH"));
        assertEquals("pH must be between 0 and 14.", e.getMessage());
    }

    @Test
    void parseNumberHandlesCommas() {
        assertEquals(1250.5, Validator.parseNumber("1,250.5", "Acres"));
        assertThrows(ValidationException.class, () -> Validator.parseNumber("ten", "Acres"));
    }
}
