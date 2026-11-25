package com.farmerp.yields;

import com.farmerp.app.Database;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TC-08 enter yield, TC-09 expected yield per acre
class YieldServiceTest {

    @TempDir
    Path folder;

    private YieldService yields;
    private FieldService fields;
    private long farmId;
    private long fieldId;

    @BeforeEach
    void setUp() {
        Database db = new Database(folder.resolve("test.db"));
        db.initSchema();
        yields = new YieldService(db);
        fields = new FieldService(db);
        Farm farm = fields.saveFarm(new Farm(0, "Home Farm", "Allendale", 80));
        farmId = farm.id();
        fieldId = fields.saveField(new Field(0, farm.id(), "North", 40, "")).id();
    }

    private YieldRecord harvest(int year, String crop, double total, YieldUnit unit, long priceCents) {
        return new YieldRecord(0, fieldId, year, crop, LocalDate.of(year, 10, 15), total, unit, priceCents);
    }

    // ---------- TC-08 ----------

    @Test
    void savesYield() {
        YieldRecord saved = yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), yields.yieldsFor(fieldId));
    }

    @Test
    void allowsZeroYieldForCropFailure() {
        assertDoesNotThrow(() -> yields.saveYield(harvest(2026, "Corn", 0, YieldUnit.BUSHELS, 435)));
    }

    @Test
    void rejectsNegativeYieldOrPrice() {
        assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(2026, "Corn", -1, YieldUnit.BUSHELS, 435)));
        assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, -1)));
    }

    @Test
    void rejectsMissingCropDateOrUnit() {
        assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(2026, " ", 7200, YieldUnit.BUSHELS, 435)));
        assertThrows(ValidationException.class,
                () -> yields.saveYield(new YieldRecord(0, fieldId, 2026, "Corn", null, 7200, YieldUnit.BUSHELS, 435)));
        assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(2026, "Corn", 7200, null, 435)));
    }

    @Test
    void rejectsCropYearOutOfRangeOrMissingField() {
        assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(1899, "Corn", 7200, YieldUnit.BUSHELS, 435)));
        assertThrows(ValidationException.class,
                () -> yields.saveYield(new YieldRecord(0, 999, 2026, "Corn", LocalDate.of(2026, 10, 15),
                        7200, YieldUnit.BUSHELS, 435)));
    }

    @Test
    void rejectsSecondYieldForSameCropAndYear() {
        yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        ValidationException e = assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(2026, "corn", 7000, YieldUnit.BUSHELS, 435)));
        assertEquals("This field already has a corn yield for 2026.", e.getMessage());
    }

    @Test
    void allowsDifferentCropsInSameYear() {
        yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        assertDoesNotThrow(() -> yields.saveYield(harvest(2026, "Hay", 120, YieldUnit.TONS, 18000)));
    }

    @Test
    void rejectsSwitchingUnitsForSameCrop() {
        yields.saveYield(harvest(2025, "Corn", 7200, YieldUnit.BUSHELS, 435));
        ValidationException e = assertThrows(ValidationException.class,
                () -> yields.saveYield(harvest(2026, "Corn", 200, YieldUnit.TONS, 5000)));
        assertEquals("Corn on this field is recorded in bushels. Use bushels for every year.", e.getMessage());
    }

    @Test
    void updatesYieldWithoutTrippingDuplicateCheck() {
        YieldRecord saved = yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        yields.saveYield(new YieldRecord(saved.id(), fieldId, 2026, "Corn", LocalDate.of(2026, 10, 20),
                7350, YieldUnit.BUSHELS, 440));
        YieldRecord reloaded = yields.yieldsFor(fieldId).get(0);
        assertEquals(7350.0, reloaded.totalYield());
        assertEquals(440, reloaded.priceCentsPerUnit());
    }

    @Test
    void deletesYield() {
        YieldRecord saved = yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        yields.deleteYield(saved.id());
        assertTrue(yields.yieldsFor(fieldId).isEmpty());
    }

    @Test
    void listsNewestCropYearFirst() {
        yields.saveYield(harvest(2024, "Corn", 6800, YieldUnit.BUSHELS, 420));
        yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        yields.saveYield(harvest(2025, "Soybeans", 2200, YieldUnit.BUSHELS, 1050));
        List<YieldRecord> list = yields.yieldsFor(fieldId);
        assertEquals(2026, list.get(0).cropYear());
        assertEquals(2024, list.get(2).cropYear());
    }

    @Test
    void revenueIsYieldTimesPriceToTheCent() {
        assertEquals(3132000, YieldService.revenueCents(7200, 435));
        assertEquals(3, YieldService.revenueCents(0.5, 5));
    }

    @Test
    void deletingFieldDeletesItsYields() {
        yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        fields.deleteField(fieldId);
        long otherField = fields.saveField(new Field(0, farmId, "South", 40, "")).id();
        assertTrue(yields.yieldsFor(fieldId).isEmpty());
        assertTrue(yields.yieldsFor(otherField).isEmpty());
    }

    // ---------- TC-09 ----------

    @Test
    void expectedYieldAveragesPriorYearsPerAcre() {
        yields.saveYield(harvest(2024, "Corn", 7200, YieldUnit.BUSHELS, 420));
        yields.saveYield(harvest(2025, "Corn", 7600, YieldUnit.BUSHELS, 435));
        Optional<ExpectedYield> expected = yields.expectedYieldPerAcre(fieldId, "Corn", 2026);
        assertTrue(expected.isPresent());
        assertEquals(185.0, expected.get().perAcre());
        assertEquals(2, expected.get().years());
        assertEquals(YieldUnit.BUSHELS, expected.get().unit());
    }

    @Test
    void expectedYieldOnlyUsesYearsBeforeTheTargetYear() {
        yields.saveYield(harvest(2024, "Corn", 7200, YieldUnit.BUSHELS, 420));
        yields.saveYield(harvest(2026, "Corn", 8000, YieldUnit.BUSHELS, 435));
        ExpectedYield expected = yields.expectedYieldPerAcre(fieldId, "Corn", 2026).orElseThrow();
        assertEquals(180.0, expected.perAcre());
        assertEquals(1, expected.years());
    }

    @Test
    void expectedYieldIsEmptyWithNoHistory() {
        assertTrue(yields.expectedYieldPerAcre(fieldId, "Corn", 2026).isEmpty());
        yields.saveYield(harvest(2026, "Corn", 7200, YieldUnit.BUSHELS, 435));
        assertTrue(yields.expectedYieldPerAcre(fieldId, "Corn", 2026).isEmpty());
        assertTrue(yields.expectedYieldPerAcre(fieldId, "Soybeans", 2027).isEmpty());
    }

    @Test
    void expectedYieldIgnoresCropNameCase() {
        yields.saveYield(harvest(2025, "Corn", 7200, YieldUnit.BUSHELS, 435));
        assertTrue(yields.expectedYieldPerAcre(fieldId, "corn", 2026).isPresent());
    }
}
