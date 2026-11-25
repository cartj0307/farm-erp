package com.farmerp.finance;

import java.time.LocalDate;

// annualRatePct: 6.5 means 6.5% a year
public record Loan(long id, long farmId, String lender, long principalCents, double annualRatePct,
                   LocalDate startDate, int termMonths, long monthlyPaymentCents, LocalDate firstPaymentDate) {

    public Loan withId(long newId) {
        return new Loan(newId, farmId, lender, principalCents, annualRatePct, startDate, termMonths,
                monthlyPaymentCents, firstPaymentDate);
    }
}
