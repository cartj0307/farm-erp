package com.farmerp.costs;

// One field's total for one category in a crop year
public record CostTotal(long fieldId, CostCategory category, long cents) {
}
