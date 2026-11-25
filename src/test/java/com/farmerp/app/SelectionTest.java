package com.farmerp.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectionTest {

    @Test
    void startsEmpty() {
        Selection s = new Selection();
        assertEquals(0, s.farmId());
        assertEquals(0, s.fieldId());
        assertEquals(0, s.version());
    }

    @Test
    void pickingAFarmClearsTheFieldAndBumpsBothVersions() {
        Selection s = new Selection();
        s.selectField(1, 10);
        s.selectFarm(2);
        assertEquals(2, s.farmId());
        assertEquals(0, s.fieldId());
        assertEquals(3, s.version());
        assertEquals(2, s.farmVersion());
    }

    @Test
    void pickingTheSameFarmAgainChangesNothing() {
        Selection s = new Selection();
        s.selectField(1, 10);
        long version = s.version();
        s.selectFarm(1);
        assertEquals(10, s.fieldId());
        assertEquals(version, s.version());
    }

    @Test
    void pickingAnotherFieldOnTheSameFarmLeavesFarmVersionAlone() {
        Selection s = new Selection();
        s.selectField(1, 10);
        long farmVersion = s.farmVersion();
        s.selectField(1, 11);
        assertEquals(11, s.fieldId());
        assertEquals(farmVersion, s.farmVersion());
    }

    @Test
    void forgettingTheHomeFarmOrFieldClearsIt() {
        Selection s = new Selection();
        s.selectField(1, 10);
        s.forgetField(99);
        assertEquals(10, s.fieldId());
        s.forgetField(10);
        assertEquals(0, s.fieldId());
        assertEquals(1, s.farmId());
        s.forgetFarm(1);
        assertEquals(0, s.farmId());
    }
}
