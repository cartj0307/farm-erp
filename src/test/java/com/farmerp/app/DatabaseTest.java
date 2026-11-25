package com.farmerp.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @TempDir
    Path folder;

    private Database newDatabase() {
        Database db = new Database(folder.resolve("test.db"));
        db.initSchema();
        return db;
    }

    @Test
    void createsAllNineTables() throws SQLException {
        Database db = newDatabase();
        Set<String> tables = new HashSet<>();
        try (Connection c = db.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        assertEquals(Set.of("farm", "field", "soil_sample", "application", "input_cost",
                "yield_record", "cash_entry", "loan", "bill"), tables);
    }

    @Test
    void initSchemaCanRunTwice() {
        Database db = newDatabase();
        assertDoesNotThrow(db::initSchema);
    }

    @Test
    void foreignKeysAreOn() throws SQLException {
        Database db = newDatabase();
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            assertThrows(SQLException.class, () ->
                    s.execute("INSERT INTO field (farm_id, name, acres) VALUES (999, 'North', 10)"));
        }
    }

    @Test
    void deletingFarmDeletesItsFields() throws SQLException {
        Database db = newDatabase();
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO farm (name, location, total_acres) VALUES ('Home', 'Allendale', 80)");
            s.execute("INSERT INTO field (farm_id, name, acres) VALUES (1, 'North', 40)");
            s.execute("DELETE FROM farm WHERE farm_id = 1");
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM field")) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    void checkConstraintBlocksZeroAcres() throws SQLException {
        Database db = newDatabase();
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO farm (name, location, total_acres) VALUES ('Home', 'Allendale', 80)");
            SQLException e = assertThrows(SQLException.class, () ->
                    s.execute("INSERT INTO field (farm_id, name, acres) VALUES (1, 'North', 0)"));
            assertTrue(e.getMessage().contains("CHECK"));
        }
    }
}
