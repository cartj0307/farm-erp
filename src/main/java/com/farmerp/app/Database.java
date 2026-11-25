package com.farmerp.app;

import com.farmerp.common.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Database {

    private final String url;

    public Database(Path file) {
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
    }

    // Default location: <home>/FarmERP/farm_erp.db
    public static Database openDefault() {
        Path folder = Path.of(System.getProperty("user.home"), "FarmERP");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new DataAccessException("Could not create the data folder " + folder, e);
        }
        return new Database(folder.resolve("farm_erp.db"));
    }

    // Foreign keys have to be turned on for every SQLite connection
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public void initSchema() {
        List<String> statements = loadStatements("/schema.sql");
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not set up the database", e);
        }
    }

    // Splits schema.sql on semicolons and drops comment lines
    static List<String> loadStatements(String resource) {
        String text;
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new DataAccessException("Missing resource " + resource, null);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Could not read " + resource, e);
        }

        StringBuilder clean = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                clean.append(line).append('\n');
            }
        }

        List<String> statements = new ArrayList<>();
        for (String part : clean.toString().split(";")) {
            String sql = part.trim();
            if (!sql.isEmpty() && !sql.toUpperCase().startsWith("PRAGMA")) {
                statements.add(sql);
            }
        }
        return statements;
    }
}
