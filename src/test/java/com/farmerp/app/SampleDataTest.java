package com.farmerp.app;

import com.farmerp.fields.Farm;
import com.farmerp.fields.Field;
import com.farmerp.fields.FieldService;
import com.farmerp.records.RecordService;
import com.farmerp.summary.FarmSummary;
import com.farmerp.summary.SummaryService;
import com.farmerp.yields.YieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleDataTest {

    @TempDir
    Path folder;

    private Database db;

    @BeforeEach
    void setUp() {
        db = new Database(folder.resolve("test.db"));
        db.initSchema();
    }

    @Test
    void loadsIntoEmptyDatabase() {
        assertTrue(SampleData.load(db));
        FieldService fields = new FieldService(db);
        List<Farm> farms = fields.listFarms();
        assertEquals(2, farms.size());
        assertEquals(4, fields.listFields(farms.get(0).id()).size());
    }

    @Test
    void doesNotLoadTwice() {
        assertTrue(SampleData.load(db));
        assertFalse(SampleData.load(db));
        assertEquals(2, new FieldService(db).listFarms().size());
    }

    @Test
    void doesNotTouchADatabaseThatHasFarms() {
        new FieldService(db).saveFarm(new Farm(0, "My Farm", "", 40));
        assertFalse(SampleData.load(db));
        assertEquals(1, new FieldService(db).listFarms().size());
    }

    @Test
    void sampleDataWorksAcrossEveryModule() {
        SampleData.load(db);
        FieldService fields = new FieldService(db);
        Farm home = fields.listFarms().get(0);
        Field north = fields.listFields(home.id()).stream()
                .filter(f -> f.name().equals("North 40")).findFirst().orElseThrow();

        assertFalse(new RecordService(db).fieldHistory(north.id()).isEmpty());
        assertTrue(new YieldService(db).expectedYieldPerAcre(north.id(), "Corn", 2027).isPresent());

        FarmSummary summary = new SummaryService(db).farmSummary(home.id(), 2026);
        assertEquals(4, summary.fields().size());
        assertTrue(summary.totalCostCents() > 0);
        assertTrue(summary.revenueCents() != null && summary.revenueCents() > 0);
    }
}
