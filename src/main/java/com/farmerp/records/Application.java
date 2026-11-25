package com.farmerp.records;

import java.time.LocalDate;

public record Application(long id, long fieldId, LocalDate date, ApplicationType type,
                          String product, double amount, String unit) {

    public Application withId(long newId) {
        return new Application(newId, fieldId, date, type, product, amount, unit);
    }
}
