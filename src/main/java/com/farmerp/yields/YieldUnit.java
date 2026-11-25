package com.farmerp.yields;

// Matches the CHECK list on yield_record.yield_unit
public enum YieldUnit {
    BUSHELS("bushels", "bu/acre"),
    TONS("tons", "tons/acre");

    private final String label;
    private final String perAcre;

    YieldUnit(String label, String perAcre) {
        this.label = label;
        this.perAcre = perAcre;
    }

    public String perAcreLabel() {
        return perAcre;
    }

    @Override
    public String toString() {
        return label;
    }
}
