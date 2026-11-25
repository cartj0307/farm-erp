package com.farmerp.finance;

import java.time.LocalDate;

// Cash on hand as of a date; the latest date is the current balance
public record CashEntry(long id, long farmId, LocalDate date, long balanceCents) {

    public CashEntry withId(long newId) {
        return new CashEntry(newId, farmId, date, balanceCents);
    }
}
