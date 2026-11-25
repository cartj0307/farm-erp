package com.farmerp.costs;

import java.time.LocalDate;

// amountCents: $1,250.75 is stored as 125075
public record InputCost(long id, long fieldId, int cropYear, LocalDate date, CostCategory category,
                        String description, long amountCents) {

    public InputCost withId(long newId) {
        return new InputCost(newId, fieldId, cropYear, date, category, description, amountCents);
    }
}
