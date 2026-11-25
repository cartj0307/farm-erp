package com.farmerp.costs;

import com.farmerp.app.Database;
import com.farmerp.common.Money;
import com.farmerp.common.ValidationException;
import com.farmerp.fields.Farm;
import com.farmerp.fields.Field;
import com.farmerp.fields.FieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TC-06 record input cost, TC-07 edit, delete, and filter by crop year
class CostServiceTest {

    @TempDir
    Path folder;

    private CostService costs;
    private FieldService fields;
    private long farmId;
    private long fieldId;

    @BeforeEach
    void setUp() {
        Database db = new Database(folder.resolve("test.db"));
        db.initSchema();
        costs = new CostService(db);
        fields = new FieldService(db);
        Farm farm = fields.saveFarm(new Farm(0, "Home Farm", "Allendale", 80));
        farmId = farm.id();
        fieldId = fields.saveField(new Field(0, farm.id(), "North", 40, "")).id();
    }

    private InputCost cost(int year, String date, CostCategory category, String description, long cents) {
        return new InputCost(0, fieldId, year, date == null ? null : LocalDate.parse(date), category, description, cents);
    }

    // ---------- TC-06 ----------

    @Test
    void savesCost() {
        InputCost saved = costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "Seed corn, 30 bags", 825000));
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), costs.costsFor(fieldId, 2026));
    }

    @Test
    void keepsCentsExact() {
        costs.saveCost(cost(2026, "2026-04-20", CostCategory.FUEL, "", Money.parseCents("0.10", "Amount")));
        costs.saveCost(cost(2026, "2026-04-21", CostCategory.FUEL, "", Money.parseCents("0.20", "Amount")));
        assertEquals(30, costs.totalFor(fieldId, 2026));
        assertEquals("$0.30", Money.format(costs.totalFor(fieldId, 2026)));
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", 0)));
        assertEquals("Amount must be more than 0.", e.getMessage());
        assertThrows(ValidationException.class,
                () -> costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", -5000)));
        assertTrue(costs.costsFor(fieldId, 2026).isEmpty());
    }

    @Test
    void rejectsMissingCategoryOrDate() {
        assertThrows(ValidationException.class,
                () -> costs.saveCost(cost(2026, "2026-04-20", null, "", 1000)));
        assertThrows(ValidationException.class,
                () -> costs.saveCost(cost(2026, null, CostCategory.SEED, "", 1000)));
    }

    @Test
    void rejectsCropYearOutOfRange() {
        assertThrows(ValidationException.class,
                () -> costs.saveCost(cost(1899, "2026-04-20", CostCategory.SEED, "", 1000)));
        assertThrows(ValidationException.class,
                () -> costs.saveCost(cost(2101, "2026-04-20", CostCategory.SEED, "", 1000)));
    }

    @Test
    void rejectsCostForMissingField() {
        InputCost orphan = new InputCost(0, 999, 2026, LocalDate.parse("2026-04-20"), CostCategory.SEED, "", 1000);
        assertThrows(ValidationException.class, () -> costs.saveCost(orphan));
    }

    @Test
    void trimsDescription() {
        InputCost saved = costs.saveCost(cost(2026, "2026-04-20", CostCategory.LABOR, "  Planting crew  ", 40000));
        assertEquals("Planting crew", saved.description());
    }

    // ---------- TC-07 ----------

    @Test
    void filtersByCropYear() {
        costs.saveCost(cost(2025, "2025-04-20", CostCategory.SEED, "", 700000));
        costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", 825000));
        costs.saveCost(cost(2026, "2026-05-02", CostCategory.FERTILIZER, "", 312550));
        assertEquals(1, costs.costsFor(fieldId, 2025).size());
        assertEquals(2, costs.costsFor(fieldId, 2026).size());
        assertEquals(1137550, costs.totalFor(fieldId, 2026));
    }

    @Test
    void listsNewestFirst() {
        costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", 825000));
        costs.saveCost(cost(2026, "2026-06-15", CostCategory.CHEMICAL, "", 98000));
        assertEquals(CostCategory.CHEMICAL, costs.costsFor(fieldId, 2026).get(0).category());
    }

    @Test
    void updatesCost() {
        InputCost saved = costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "Seed corn", 825000));
        costs.saveCost(new InputCost(saved.id(), fieldId, 2026, LocalDate.parse("2026-04-21"),
                CostCategory.SEED, "Seed corn, 32 bags", 880000));
        InputCost reloaded = costs.costsFor(fieldId, 2026).get(0);
        assertEquals(880000, reloaded.amountCents());
        assertEquals("Seed corn, 32 bags", reloaded.description());
    }

    @Test
    void deletesCost() {
        InputCost saved = costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", 825000));
        costs.deleteCost(saved.id());
        assertTrue(costs.costsFor(fieldId, 2026).isEmpty());
        assertEquals(0, costs.totalFor(fieldId, 2026));
    }

    @Test
    void keepsCostsSeparateByField() {
        long southId = fields.saveField(new Field(0, farmId, "South", 40, "")).id();
        costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", 825000));
        assertTrue(costs.costsFor(southId, 2026).isEmpty());
        assertEquals(0, costs.totalFor(southId, 2026));
    }

    @Test
    void deletingFieldDeletesItsCosts() {
        costs.saveCost(cost(2026, "2026-04-20", CostCategory.SEED, "", 825000));
        fields.deleteField(fieldId);
        assertTrue(costs.costsFor(fieldId, 2026).isEmpty());
    }
}
