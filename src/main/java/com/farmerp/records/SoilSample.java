package com.farmerp.records;

import java.time.LocalDate;

// Test values are null when the lab didn't report them
public record SoilSample(long id, long fieldId, LocalDate sampleDate,
                         Double nitrogenPpm, Double phosphorusPpm, Double potassiumPpm,
                         Double ph, Double organicMatterPct) {

    public SoilSample withId(long newId) {
        return new SoilSample(newId, fieldId, sampleDate, nitrogenPpm, phosphorusPpm,
                potassiumPpm, ph, organicMatterPct);
    }
}
