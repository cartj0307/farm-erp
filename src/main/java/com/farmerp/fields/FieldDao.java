package com.farmerp.fields;

import com.farmerp.app.Database;
import com.farmerp.common.Dao;
import com.farmerp.common.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FieldDao implements Dao<Field> {

    private static final String COLUMNS = "field_id, farm_id, name, acres, notes";

    private final Database db;

    public FieldDao(Database db) {
        this.db = db;
    }

    @Override
    public Field insert(Field field) {
        String sql = "INSERT INTO field (farm_id, name, acres, notes) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, field.farmId());
            ps.setString(2, field.name());
            ps.setDouble(3, field.acres());
            ps.setString(4, blankToNull(field.notes()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return field.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the field", e);
        }
    }

    @Override
    public void update(Field field) {
        String sql = "UPDATE field SET name = ?, acres = ?, notes = ? WHERE field_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, field.name());
            ps.setDouble(2, field.acres());
            ps.setString(3, blankToNull(field.notes()));
            ps.setLong(4, field.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the field", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM field WHERE field_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the field", e);
        }
    }

    @Override
    public Optional<Field> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM field WHERE field_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the field", e);
        }
    }

    public List<Field> findByFarm(long farmId) {
        String sql = "SELECT " + COLUMNS + " FROM field WHERE farm_id = ? ORDER BY name COLLATE NOCASE";
        List<Field> fields = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, farmId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fields.add(map(rs));
                }
            }
            return fields;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load fields", e);
        }
    }

    // Case-insensitive name check within one farm, skipping the field being edited
    public boolean nameExists(long farmId, String name, long excludeFieldId) {
        String sql = "SELECT 1 FROM field WHERE farm_id = ? AND name = ? COLLATE NOCASE AND field_id <> ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, farmId);
            ps.setString(2, name);
            ps.setLong(3, excludeFieldId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check field names", e);
        }
    }

    private Field map(ResultSet rs) throws SQLException {
        String notes = rs.getString("notes");
        return new Field(rs.getLong("field_id"), rs.getLong("farm_id"), rs.getString("name"),
                rs.getDouble("acres"), notes == null ? "" : notes);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
