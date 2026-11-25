package com.farmerp.fields;

public record Field(long id, long farmId, String name, double acres, String notes) {

    public Field withId(long newId) {
        return new Field(newId, farmId, name, acres, notes);
    }
}
