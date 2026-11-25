package com.farmerp.summary;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.costs.CostCategory;
import com.farmerp.costs.CostService;
import com.farmerp.costs.InputCost;
import com.farmerp.fields.Farm;
import com.farmerp.fields.Field;
import com.farmerp.fields.FieldService;
import com.farmerp.yields.YieldRecord;
import com.farmerp.yields.YieldService;
import com.farmerp.yields.YieldUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TC-14 totals by field and category, TC-15 cost per acre, TC-16 revenue and net per acre, TC-22 speed
class SummaryServiceTest {

    @TempDir
    Path folder;

    private Database db;
    private SummaryService summary;
    private FieldService fields;
    private CostService costs;
    private YieldService yields;
    private long farmId;
    private long northId;
    private long southId;

    @BeforeEach
    void setUp() {
        db = new Database(folder.resolve("test.db"));
        db.initSchema();
        summary = new SummaryService(db);
        fields = new FieldService(db);
        costs = new CostService(db);
        yields = new YieldService(db);
        farmId = fields.saveFarm(new Farm(0, "Home Farm", "Allendale", 80)).id();
        northId = fields.saveField(new Field(0, farmId, "North", 40, "")).id();
        southId = fields.saveField(new Field(0, farmId, "South", 20, "")).id();
        fields.saveField(new Field(0, farmId, "Creek", 10, ""));
    }

    private void cost(long fieldId, int year, CostCategory category, long cents) {
        costs.saveCost(new InputCost(0, fieldId, year, LocalDate.of(year, 4, 20), category, "", cents));
    }

    private void harvest(long fieldId, int year, String crop, double total, YieldUnit unit, long priceCents) {
        yields.saveYield(new YieldRecord(0, fieldId, year, crop, LocalDate.of(year, 10, 15), total, unit, priceCents));
    }

    private void standardData() {
        cost(northId, 2026, CostCategory.SEED, 800000);
        cost(northId, 2026, CostCategory.FERTILIZER, 300000);
        cost(southId, 2026, CostCategory.SEED, 200000);
        cost(northId, 2025, CostCategory.SEED, 100000);
        harvest(northId, 2026, "Corn", 7600, YieldUnit.BUSHELS, 435);
    }

    private FieldSummary row(FarmSummary s, String name) {
        return s.fields().stream().filter(f -> f.fieldName().equals(name)).findFirst().orElseThrow();
    }

    // ---------- TC-14 ----------

    @Test
    void totalsCostsByFieldAndFarm() {
        standardData();
        FarmSummary s = summary.farmSummary(farmId, 2026);
        assertEquals(3, s.fields().size());
        assertEquals(1100000, row(s, "North").costCents());
        assertEquals(200000, row(s, "South").costCents());
        assertEquals(0, row(s, "Creek").costCents());
        assertEquals(1300000, s.totalCostCents());
    }

    @Test
    void breaksCostsDownByCategory() {
        standardData();
        FarmSummary s = summary.farmSummary(farmId, 2026);
        assertEquals(Long.valueOf(1000000), s.costByCategory().get(CostCategory.SEED));
        assertEquals(Long.valueOf(300000), s.costByCategory().get(CostCategory.FERTILIZER));
        assertNull(s.costByCategory().get(CostCategory.FUEL));
        assertEquals(Long.valueOf(800000), row(s, "North").costByCategory().get(CostCategory.SEED));
        assertTrue(row(s, "Creek").costByCategory().isEmpty());
    }

    @Test
    void onlyCountsThePickedCropYear() {
        standardData();
        FarmSummary s = summary.farmSummary(farmId, 2025);
        assertEquals(100000, s.totalCostCents());
        assertNull(s.revenueCents());
    }

    @Test
    void leavesOutOtherFarms() {
        standardData();
        long otherFarm = fields.saveFarm(new Farm(0, "West Place", "", 40)).id();
        long otherField = fields.saveField(new Field(0, otherFarm, "West", 40, "")).id();
        cost(otherField, 2026, CostCategory.SEED, 999900);
        assertEquals(1300000, summary.farmSummary(farmId, 2026).totalCostCents());
        assertEquals(999900, summary.farmSummary(otherFarm, 2026).totalCostCents());
    }

    // ---------- TC-15 ----------

    @Test
    void costPerAcreForEachFieldAndFarm() {
        standardData();
        FarmSummary s = summary.farmSummary(farmId, 2026);
        assertEquals(27500, row(s, "North").costPerAcreCents());
        assertEquals(10000, row(s, "South").costPerAcreCents());
        assertEquals(0, row(s, "Creek").costPerAcreCents());
        assertEquals(70.0, s.totalAcres());
        // $13,000 / 70 acres = $185.714... rounds to $185.71
        assertEquals(18571, s.costPerAcreCents());
    }

    @Test
    void costPerAcreRoundsHalfUp() {
        // $10.00 / 8 acres = $1.25; $0.05 / 2 acres = 2.5 cents rounds to 3
        assertEquals(125, SummaryService.costPerAcre(1000, 8));
        assertEquals(3, SummaryService.costPerAcre(5, 2));
        assertEquals(0, SummaryService.costPerAcre(1000, 0));
    }

    // ---------- TC-16 ----------

    @Test
    void revenueAndNetPerAcreForFieldsWithYields() {
        standardData();
        FarmSummary s = summary.farmSummary(farmId, 2026);
        FieldSummary north = row(s, "North");
        assertTrue(north.hasYield());
        // 7,600 bu x $4.35 = $33,060; minus $11,000 = $22,060; / 40 = $551.50
        assertEquals(Long.valueOf(3306000), north.revenueCents());
        assertEquals(Long.valueOf(2206000), north.netCents());
        assertEquals(Long.valueOf(55150), north.netPerAcreCents());
        assertEquals("Corn", north.crops());
    }

    @Test
    void leavesRevenueBlankForFieldsWithoutYields() {
        standardData();
        FieldSummary south = row(summary.farmSummary(farmId, 2026), "South");
        assertFalse(south.hasYield());
        assertNull(south.revenueCents());
        assertNull(south.netCents());
        assertNull(south.netPerAcreCents());
    }

    @Test
    void farmNetIsAllRevenueMinusAllCost() {
        standardData();
        FarmSummary s = summary.farmSummary(farmId, 2026);
        assertEquals(Long.valueOf(3306000), s.revenueCents());
        // $33,060 - $13,000 = $20,060; / 70 acres = $286.571... rounds to $286.57
        assertEquals(Long.valueOf(2006000), s.netCents());
        assertEquals(Long.valueOf(28657), s.netPerAcreCents());
    }

    @Test
    void addsUpTwoCropsOnOneField() {
        cost(northId, 2026, CostCategory.SEED, 100000);
        harvest(northId, 2026, "Corn", 1000, YieldUnit.BUSHELS, 400);
        harvest(northId, 2026, "Hay", 10, YieldUnit.TONS, 15000);
        FieldSummary north = row(summary.farmSummary(farmId, 2026), "North");
        assertEquals(Long.valueOf(550000), north.revenueCents());
        assertEquals("Corn, Hay", north.crops());
    }

    @Test
    void negativeNetWhenCostsBeatRevenue() {
        cost(southId, 2026, CostCategory.SEED, 500000);
        harvest(southId, 2026, "Soybeans", 200, YieldUnit.BUSHELS, 1000);
        FieldSummary south = row(summary.farmSummary(farmId, 2026), "South");
        assertEquals(Long.valueOf(-300000), south.netCents());
        assertEquals(Long.valueOf(-15000), south.netPerAcreCents());
    }

    @Test
    void emptyFarmHasZeroTotals() {
        long emptyFarm = fields.saveFarm(new Farm(0, "Empty", "", 10)).id();
        FarmSummary s = summary.farmSummary(emptyFarm, 2026);
        assertTrue(s.fields().isEmpty());
        assertEquals(0, s.totalCostCents());
        assertEquals(0, s.costPerAcreCents());
        assertNull(s.revenueCents());
    }

    @Test
    void rejectsMissingFarm() {
        assertThrows(ValidationException.class, () -> summary.farmSummary(999, 2026));
    }

    // ---------- TC-22 (NFR-6) ----------

    @Test
    void summaryForHundredFieldsAndTenYearsLoadsUnderTwoSeconds() throws SQLException {
        long bigFarm = fields.saveFarm(new Farm(0, "Big Farm", "", 5000)).id();
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement field = c.prepareStatement(
                    "INSERT INTO field (farm_id, name, acres) VALUES (?, ?, 50)", PreparedStatement.RETURN_GENERATED_KEYS);
                 PreparedStatement cost = c.prepareStatement(
                         "INSERT INTO input_cost (field_id, crop_year, cost_date, category, amount_cents) VALUES (?, ?, ?, ?, ?)");
                 PreparedStatement yield = c.prepareStatement(
                         "INSERT INTO yield_record (field_id, crop_year, crop, harvest_date, total_yield, yield_unit, "
                                 + "price_cents_per_unit) VALUES (?, ?, 'Corn', ?, 9000, 'BUSHELS', 435)")) {
                CostCategory[] cats = CostCategory.values();
                for (int f = 1; f <= 100; f++) {
                    field.setLong(1, bigFarm);
                    field.setString(2, "Field " + f);
                    field.executeUpdate();
                    long fieldId;
                    try (var keys = field.getGeneratedKeys()) {
                        keys.next();
                        fieldId = keys.getLong(1);
                    }
                    for (int year = 2017; year <= 2026; year++) {
                        for (int i = 0; i < 12; i++) {
                            cost.setLong(1, fieldId);
                            cost.setInt(2, year);
                            cost.setString(3, year + "-04-20");
                            cost.setString(4, cats[i % cats.length].name());
                            cost.setLong(5, 10000 + i);
                            cost.addBatch();
                        }
                        yield.setLong(1, fieldId);
                        yield.setInt(2, year);
                        yield.setString(3, year + "-10-15");
                        yield.addBatch();
                    }
                }
                cost.executeBatch();
                yield.executeBatch();
            }
            c.commit();
        }

        long start = System.nanoTime();
        FarmSummary s = summary.farmSummary(bigFarm, 2026);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(100, s.fields().size());
        assertTrue(millis < 2000, "Summary took " + millis + " ms");
    }
}
