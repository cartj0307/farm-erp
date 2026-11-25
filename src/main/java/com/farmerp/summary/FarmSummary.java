package com.farmerp.summary;

import com.farmerp.costs.CostCategory;

import java.util.List;
import java.util.Map;

// Farm totals add up every field; revenue and net are null when no field has a yield that year
public record FarmSummary(long farmId, String farmName, int cropYear, List<FieldSummary> fields,
                          Map<CostCategory, Long> costByCategory, double totalAcres, long totalCostCents,
                          long costPerAcreCents, Long revenueCents, Long netCents, Long netPerAcreCents) {
}
