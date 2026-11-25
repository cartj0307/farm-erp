package com.farmerp.records;

import com.farmerp.app.Database;
import com.farmerp.common.Dao;
import com.farmerp.common.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SoilSampleDao implements Dao<SoilSample> {

    private static final String COLUMNS = "sample_id, field_id, sample_date, nitrogen_ppm, phosphorus_ppm, "
            + "potassium_ppm, ph, organic_matter_pct";

    private final Database db;

    public SoilSampleDao(Database db) {
        this.db = db;
    }

    @Override
    public SoilSample insert(SoilSample s) {
        String sql = "INSERT INTO soil_sample (field_id, sample_date, nitrogen_ppm, phosphorus_ppm, "
                + "potassium_ppm, ph, organic_matter_pct) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, s.fieldId());
            ps.setString(2, s.sampleDate().toString());
            setValues(ps, 3, s);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return s.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the soil sample", e);
        }
    }

    @Override
    public void update(SoilSample s) {
        String sql = "UPDATE soil_sample SET sample_date = ?, nitrogen_ppm = ?, phosphorus_ppm = ?, "
                + "potassium_ppm = ?, ph = ?, organic_matter_pct = ? WHERE sample_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.sampleDate().toString());
            setValues(ps, 2, s);
            ps.setLong(7, s.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the soil sample", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM soil_sample WHERE sample_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the soil sample", e);
        }
    }

    @Override
    public Optional<SoilSample> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM soil_sample WHERE sample_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the soil sample", e);
        }
    }

    // Newest first
    public List<SoilSample> findByField(long fieldId) {
        String sql = "SELECT " + COLUMNS + " FROM soil_sample WHERE field_id = ? "
                + "ORDER BY sample_date DESC, sample_id DESC";
        List<SoilSample> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fieldId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load soil samples", e);
        }
    }

    // Sets the five test values starting at parameter index start
    private static void setValues(PreparedStatement ps, int start, SoilSample s) throws SQLException {
        setNullable(ps, start, s.nitrogenPpm());
        setNullable(ps, start + 1, s.phosphorusPpm());
        setNullable(ps, start + 2, s.potassiumPpm());
        setNullable(ps, start + 3, s.ph());
        setNullable(ps, start + 4, s.organicMatterPct());
    }

    private static void setNullable(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static Double getNullable(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private SoilSample map(ResultSet rs) throws SQLException {
        return new SoilSample(rs.getLong("sample_id"), rs.getLong("field_id"),
                LocalDate.parse(rs.getString("sample_date")),
                getNullable(rs, "nitrogen_ppm"), getNullable(rs, "phosphorus_ppm"),
                getNullable(rs, "potassium_ppm"), getNullable(rs, "ph"),
                getNullable(rs, "organic_matter_pct"));
    }
}
