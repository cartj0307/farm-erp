package com.farmerp.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void parsesPlainAndFormattedAmounts() {
        assertEquals(125075, Money.parseCents("1250.75", "Amount"));
        assertEquals(125075, Money.parseCents("$1,250.75", "Amount"));
        assertEquals(500, Money.parseCents("5", "Amount"));
    }

    @Test
    void rejectsBadAmounts() {
        assertThrows(ValidationException.class, () -> Money.parseCents("", "Amount"));
        assertThrows(ValidationException.class, () -> Money.parseCents("abc", "Amount"));
        assertThrows(ValidationException.class, () -> Money.parseCents("1.005", "Amount"));
    }

    @Test
    void formatsCents() {
        assertEquals("$1,250.75", Money.format(125075));
        assertEquals("$0.05", Money.format(5));
    }

    @Test
    void dividesAndRoundsToNearestCent() {
        assertEquals(3333, Money.divide(10000, 3));
        assertEquals(2500, Money.divide(100000, 40));
    }
}
