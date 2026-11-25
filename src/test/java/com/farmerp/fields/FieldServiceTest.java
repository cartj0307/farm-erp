package com.farmerp.fields;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TC-01 farms, TC-02 fields
class FieldServiceTest {

    @TempDir
    Path folder;

    private FieldService service;

    @BeforeEach
    void setUp() {
        Database db = new Database(folder.resolve("test.db"));
        db.initSchema();
        service = new FieldService(db);
    }

    private Farm newFarm(String name, double acres) {
        return service.saveFarm(new Farm(0, name, "Allendale", acres));
    }

    // ---------- TC-01 farms ----------

    @Test
    void addsFarmAndListsIt() {
        Farm saved = newFarm("Home Farm", 80);
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), service.listFarms());
    }

    @Test
    void trimsFarmName() {
        Farm saved = newFarm("  Home Farm  ", 80);
        assertEquals("Home Farm", saved.name());
    }

    @Test
    void updatesFarm() {
        Farm saved = newFarm("Home Farm", 80);
        service.saveFarm(new Farm(saved.id(), "Home Farm", "Hudsonville", 120));
        Farm reloaded = service.listFarms().get(0);
        assertEquals("Hudsonville", reloaded.location());
        assertEquals(120, reloaded.totalAcres());
    }

    @Test
    void rejectsBlankFarmName() {
        ValidationException e = assertThrows(ValidationException.class, () -> newFarm(" ", 80));
        assertEquals("Farm name is required.", e.getMessage());
    }

    @Test
    void rejectsZeroOrNegativeTotalAcres() {
        assertThrows(ValidationException.class, () -> newFarm("Home Farm", 0));
        assertThrows(ValidationException.class, () -> newFarm("Home Farm", -10));
        assertTrue(service.listFarms().isEmpty());
    }

    @Test
    void listsFarmsByName() {
        newFarm("west place", 40);
        newFarm("East Place", 60);
        assertEquals("East Place", service.listFarms().get(0).name());
    }

    @Test
    void deletingFarmDeletesItsFields() {
        Farm farm = newFarm("Home Farm", 80);
        service.saveField(new Field(0, farm.id(), "North", 40, ""));
        service.deleteFarm(farm.id());
        assertTrue(service.listFarms().isEmpty());
        assertTrue(service.listFields(farm.id()).isEmpty());
    }

    // ---------- TC-02 fields ----------

    @Test
    void addsFieldToFarm() {
        Farm farm = newFarm("Home Farm", 80);
        Field saved = service.saveField(new Field(0, farm.id(), "North", 40, "Tiled in 2019"));
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), service.listFields(farm.id()));
    }

    @Test
    void rejectsZeroOrNegativeFieldAcres() {
        Farm farm = newFarm("Home Farm", 80);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.saveField(new Field(0, farm.id(), "North", 0, "")));
        assertEquals("Acres must be more than 0.", e.getMessage());
        assertThrows(ValidationException.class,
                () -> service.saveField(new Field(0, farm.id(), "North", -5, "")));
    }

    @Test
    void rejectsBlankFieldName() {
        Farm farm = newFarm("Home Farm", 80);
        assertThrows(ValidationException.class,
                () -> service.saveField(new Field(0, farm.id(), "", 40, "")));
    }

    @Test
    void rejectsDuplicateFieldNameOnSameFarm() {
        Farm farm = newFarm("Home Farm", 80);
        service.saveField(new Field(0, farm.id(), "North", 40, ""));
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.saveField(new Field(0, farm.id(), "north", 20, "")));
        assertEquals("This farm already has a field named north.", e.getMessage());
    }

    @Test
    void allowsSameFieldNameOnDifferentFarms() {
        Farm home = newFarm("Home Farm", 80);
        Farm west = newFarm("West Place", 40);
        service.saveField(new Field(0, home.id(), "North", 40, ""));
        assertDoesNotThrow(() -> service.saveField(new Field(0, west.id(), "North", 20, "")));
    }

    @Test
    void rejectsFieldWithoutFarm() {
        assertThrows(ValidationException.class,
                () -> service.saveField(new Field(0, 999, "North", 40, "")));
    }

    @Test
    void updatesFieldWithoutTrippingDuplicateCheck() {
        Farm farm = newFarm("Home Farm", 80);
        Field saved = service.saveField(new Field(0, farm.id(), "North", 40, ""));
        service.saveField(new Field(saved.id(), farm.id(), "North", 42.5, "Measured again"));
        Field reloaded = service.listFields(farm.id()).get(0);
        assertEquals(42.5, reloaded.acres());
        assertEquals("Measured again", reloaded.notes());
    }

    @Test
    void deletesField() {
        Farm farm = newFarm("Home Farm", 80);
        Field saved = service.saveField(new Field(0, farm.id(), "North", 40, ""));
        service.deleteField(saved.id());
        assertTrue(service.listFields(farm.id()).isEmpty());
    }

    @Test
    void listsFieldsByName() {
        Farm farm = newFarm("Home Farm", 80);
        service.saveField(new Field(0, farm.id(), "south", 20, ""));
        service.saveField(new Field(0, farm.id(), "East", 20, ""));
        service.saveField(new Field(0, farm.id(), "North", 20, ""));
        List<Field> fields = service.listFields(farm.id());
        assertEquals("East", fields.get(0).name());
        assertEquals("North", fields.get(1).name());
        assertEquals("south", fields.get(2).name());
    }
}
