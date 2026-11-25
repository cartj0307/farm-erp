package com.farmerp.records;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TC-03 soil samples, TC-04 applications, TC-05 history
class RecordServiceTest {

    @TempDir
    Path folder;

    private RecordService records;
    private FieldService fields;
    private long fieldId;

    @BeforeEach
    void setUp() {
        Database db = new Database(folder.resolve("test.db"));
        db.initSchema();
        records = new RecordService(db);
        fields = new FieldService(db);
        Farm farm = fields.saveFarm(new Farm(0, "Home Farm", "Allendale", 80));
        fieldId = fields.saveField(new Field(0, farm.id(), "North", 40, "")).id();
    }

    private static LocalDate day(String iso) {
        return LocalDate.parse(iso);
    }

    private SoilSample sample(String date, Double n, Double p, Double k, Double ph, Double om) {
        return new SoilSample(0, fieldId, date == null ? null : day(date), n, p, k, ph, om);
    }

    private Application app(String date, ApplicationType type, String product, double amount, String unit) {
        return new Application(0, fieldId, date == null ? null : day(date), type, product, amount, unit);
    }

    // ---------- TC-03 soil samples ----------

    @Test
    void savesSoilSampleWithAllValues() {
        SoilSample saved = records.saveSoilSample(sample("2026-04-15", 22.0, 35.0, 140.0, 6.4, 3.1));
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), records.listSoilSamples(fieldId));
    }

    @Test
    void keepsBlankValuesAsBlank() {
        records.saveSoilSample(sample("2026-04-15", null, null, null, 6.4, null));
        SoilSample reloaded = records.listSoilSamples(fieldId).get(0);
        assertNull(reloaded.nitrogenPpm());
        assertEquals(Double.valueOf(6.4), reloaded.ph());
    }

    @Test
    void rejectsSampleWithNoValues() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", null, null, null, null, null)));
        assertEquals("Enter at least one soil test value.", e.getMessage());
    }

    @Test
    void rejectsPhOutsideZeroToFourteen() {
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", null, null, null, 14.5, null)));
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", null, null, null, -1.0, null)));
    }

    @Test
    void rejectsNegativeNutrients() {
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", -1.0, null, null, null, null)));
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", null, -1.0, null, null, null)));
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", null, null, -1.0, null, null)));
    }

    @Test
    void rejectsOrganicMatterOverOneHundred() {
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample("2026-04-15", null, null, null, null, 101.0)));
    }

    @Test
    void rejectsSampleWithoutDate() {
        assertThrows(ValidationException.class,
                () -> records.saveSoilSample(sample(null, 22.0, null, null, null, null)));
    }

    @Test
    void rejectsSampleForMissingField() {
        SoilSample orphan = new SoilSample(0, 999, day("2026-04-15"), 22.0, null, null, null, null);
        assertThrows(ValidationException.class, () -> records.saveSoilSample(orphan));
    }

    @Test
    void listsSamplesNewestFirst() {
        records.saveSoilSample(sample("2025-04-10", 18.0, null, null, null, null));
        records.saveSoilSample(sample("2026-04-15", 22.0, null, null, null, null));
        records.saveSoilSample(sample("2024-04-20", 15.0, null, null, null, null));
        List<SoilSample> list = records.listSoilSamples(fieldId);
        assertEquals(3, list.size());
        assertEquals(day("2026-04-15"), list.get(0).sampleDate());
        assertEquals(day("2024-04-20"), list.get(2).sampleDate());
    }

    @Test
    void updatesAndDeletesSample() {
        SoilSample saved = records.saveSoilSample(sample("2026-04-15", 22.0, null, null, null, null));
        records.saveSoilSample(new SoilSample(saved.id(), fieldId, day("2026-04-16"), 25.0, null, null, 6.8, null));
        SoilSample reloaded = records.listSoilSamples(fieldId).get(0);
        assertEquals(Double.valueOf(25.0), reloaded.nitrogenPpm());
        assertEquals(Double.valueOf(6.8), reloaded.ph());
        records.deleteSoilSample(saved.id());
        assertTrue(records.listSoilSamples(fieldId).isEmpty());
    }

    // ---------- TC-04 applications ----------

    @Test
    void savesApplication() {
        Application saved = records.saveApplication(app("2026-05-01", ApplicationType.FERTILIZER, "Urea 46-0-0", 150, "lb"));
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), records.listApplications(fieldId));
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        assertThrows(ValidationException.class,
                () -> records.saveApplication(app("2026-05-01", ApplicationType.WATER, "", 0, "acre-inches")));
        assertThrows(ValidationException.class,
                () -> records.saveApplication(app("2026-05-01", ApplicationType.WATER, "", -2, "acre-inches")));
    }

    @Test
    void rejectsMissingTypeUnitOrDate() {
        assertThrows(ValidationException.class,
                () -> records.saveApplication(app("2026-05-01", null, "", 1, "acre-inches")));
        assertThrows(ValidationException.class,
                () -> records.saveApplication(app("2026-05-01", ApplicationType.WATER, "", 1, " ")));
        assertThrows(ValidationException.class,
                () -> records.saveApplication(app(null, ApplicationType.WATER, "", 1, "acre-inches")));
    }

    @Test
    void listsApplicationsNewestFirst() {
        records.saveApplication(app("2026-05-01", ApplicationType.FERTILIZER, "Urea", 150, "lb"));
        records.saveApplication(app("2026-07-10", ApplicationType.WATER, "", 1, "acre-inches"));
        assertEquals(ApplicationType.WATER, records.listApplications(fieldId).get(0).type());
    }

    @Test
    void updatesAndDeletesApplication() {
        Application saved = records.saveApplication(app("2026-05-01", ApplicationType.LIME, "Ag lime", 2, "tons"));
        records.saveApplication(new Application(saved.id(), fieldId, day("2026-05-02"), ApplicationType.LIME, "Ag lime", 2.5, "tons"));
        assertEquals(2.5, records.listApplications(fieldId).get(0).amount());
        records.deleteApplication(saved.id());
        assertTrue(records.listApplications(fieldId).isEmpty());
    }

    // ---------- TC-05 history ----------

    @Test
    void historyMergesSamplesAndApplicationsNewestFirst() {
        records.saveSoilSample(sample("2026-04-15", 22.0, 35.0, null, 6.4, null));
        records.saveApplication(app("2026-05-01", ApplicationType.FERTILIZER, "Urea 46-0-0", 150, "lb"));
        records.saveApplication(app("2026-03-20", ApplicationType.LIME, "", 2, "tons"));

        List<HistoryEntry> history = records.fieldHistory(fieldId);
        assertEquals(3, history.size());
        assertEquals(new HistoryEntry(day("2026-05-01"), "Fertilizer application", "Urea 46-0-0, 150 lb"), history.get(0));
        assertEquals(new HistoryEntry(day("2026-04-15"), "Soil sample", "N 22 ppm, P 35 ppm, pH 6.4"), history.get(1));
        assertEquals(new HistoryEntry(day("2026-03-20"), "Lime application", "2 tons"), history.get(2));
    }

    @Test
    void deletingFieldDeletesItsRecords() {
        records.saveSoilSample(sample("2026-04-15", 22.0, null, null, null, null));
        records.saveApplication(app("2026-05-01", ApplicationType.WATER, "", 1, "acre-inches"));
        fields.deleteField(fieldId);
        assertTrue(records.fieldHistory(fieldId).isEmpty());
    }
}
