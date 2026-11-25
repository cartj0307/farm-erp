package com.farmerp.yields;

import java.time.LocalDate;

// priceCentsPerUnit: $4.35 per bushel is stored as 435
public record YieldRecord(long id, long fieldId, int cropYear, String crop, LocalDate harvestDate,
                          double totalYield, YieldUnit unit, long priceCentsPerUnit) {

    public YieldRecord withId(long newId) {
        return new YieldRecord(newId, fieldId, cropYear, crop, harvestDate, totalYield, unit, priceCentsPerUnit);
    }
}
