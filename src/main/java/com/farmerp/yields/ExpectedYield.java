package com.farmerp.yields;

// Average yield per acre from a field's prior years of the same crop
public record ExpectedYield(double perAcre, int years, YieldUnit unit) {
}
