package com.farmerp.finance;

import java.time.LocalDate;

public record Bill(long id, long farmId, String payee, long amountCents, LocalDate dueDate, boolean paid) {

    public Bill withId(long newId) {
        return new Bill(newId, farmId, payee, amountCents, dueDate, paid);
    }
}
