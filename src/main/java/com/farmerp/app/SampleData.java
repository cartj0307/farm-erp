package com.farmerp.app;

import com.farmerp.common.DataAccessException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Loads made-up sample data from sample-data.sql into an empty database
public final class SampleData {

    private SampleData() {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        // Registers the SQLite driver when run through exec:java
        Class.forName("org.sqlite.JDBC");
        Database db = Database.openDefault();
        db.initSchema();
        Path file = Path.of(System.getProperty("user.home"), "FarmERP", "farm_erp.db");
        if (load(db)) {
            System.out.println("Sample data loaded into " + file);
        } else {
            System.out.println("Nothing loaded. The database already has farms in it: " + file);
            System.out.println("Close the app, delete that file, and run this again to start fresh.");
        }
    }

    // Returns false without changing anything if the database already has a farm
    public static boolean load(Database db) {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM farm")) {
                rs.next();
                if (rs.getInt(1) > 0) {
                    return false;
                }
            }
            c.setAutoCommit(false);
            try {
                for (String sql : Database.loadStatements("/sample-data.sql")) {
                    s.execute(sql);
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the sample data", e);
        }
    }
}
