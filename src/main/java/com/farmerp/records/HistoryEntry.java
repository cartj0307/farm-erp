package com.farmerp.records;

import java.time.LocalDate;

// One row in a field's combined history
public record HistoryEntry(LocalDate date, String kind, String details) {
}
