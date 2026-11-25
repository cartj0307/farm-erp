package com.farmerp.fields;

public record Farm(long id, String name, String location, double totalAcres) {

    public Farm withId(long newId) {
        return new Farm(newId, name, location, totalAcres);
    }
}
