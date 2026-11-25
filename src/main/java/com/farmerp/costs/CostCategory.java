package com.farmerp.costs;

// Matches the CHECK list on input_cost.category
public enum CostCategory {
    SEED("Seed"),
    FERTILIZER("Fertilizer"),
    CHEMICAL("Chemical"),
    FUEL("Fuel"),
    LABOR("Labor"),
    EQUIPMENT("Equipment"),
    WATER("Water"),
    OTHER("Other");

    private final String label;

    CostCategory(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
