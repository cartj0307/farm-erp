package com.farmerp.records;

// Matches the CHECK list on application.application_type
public enum ApplicationType {
    WATER("Water"),
    FERTILIZER("Fertilizer"),
    LIME("Lime"),
    CHEMICAL("Chemical");

    private final String label;

    ApplicationType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
