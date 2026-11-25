package com.farmerp.finance;

import java.time.LocalDate;

// W = H - B - L through the harvest date (FR-13)
public record WorkingCapital(LocalDate harvestDate, LocalDate cashAsOf, long cashCents,
                             long billsCents, long loanPaymentsCents) {

    public long totalCents() {
        return cashCents - billsCents - loanPaymentsCents;
    }
}
