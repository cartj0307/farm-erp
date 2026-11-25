package com.farmerp.summary;

import com.farmerp.costs.CostCategory;

import java.util.Map;

// Revenue, net, and net per acre are null when the field has no yield that year
public record FieldSummary(long fieldId, String fieldName, double acres, String crops,
                           Map<CostCategory, Long> costByCategory, long costCents, long costPerAcreCents,
                           Long revenueCents, Long netCents, Long netPerAcreCents) {

    public boolean hasYield() {
        return revenueCents != null;
    }
}
