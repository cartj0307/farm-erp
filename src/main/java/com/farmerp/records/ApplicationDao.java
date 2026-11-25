package com.farmerp.records;

import com.farmerp.app.Database;
import com.farmerp.common.Dao;
import com.farmerp.common.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ApplicationDao implements Dao<Application> {

    private static final String COLUMNS = "application_id, field_id, application_date, application_type, "
            + "product, amount, unit";

    private final Database db;

    public ApplicationDao(Database db) {
        this.db = db;
    }

    @Override
    public Application insert(Application a) {
        String sql = "INSERT INTO application (field_id, application_date, application_type, product, amount, unit) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, a.fieldId());
            ps.setString(2, a.date().toString());
            ps.setString(3, a.type().name());
            ps.setString(4, blankToNull(a.product()));
            ps.setDouble(5, a.amount());
            ps.setString(6, a.unit());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return a.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the application", e);
        }
    }

    @Override
    public void update(Application a) {
        String sql = "UPDATE application SET application_date = ?, application_type = ?, product = ?, "
                + "amount = ?, unit = ? WHERE application_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.date().toString());
            ps.setString(2, a.type().name());
            ps.setString(3, blankToNull(a.product()));
            ps.setDouble(4, a.amount());
            ps.setString(5, a.unit());
            ps.setLong(6, a.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the application", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM application WHERE application_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the application", e);
        }
    }

    @Override
    public Optional<Application> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM application WHERE application_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the application", e);
        }
    }

    // Newest first
    public List<Application> findByField(long fieldId) {
        String sql = "SELECT " + COLUMNS + " FROM application WHERE field_id = ? "
                + "ORDER BY application_date DESC, application_id DESC";
        List<Application> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fieldId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load applications", e);
        }
    }

    private Application map(ResultSet rs) throws SQLException {
        String product = rs.getString("product");
        return new Application(rs.getLong("application_id"), rs.getLong("field_id"),
                LocalDate.parse(rs.getString("application_date")),
                ApplicationType.valueOf(rs.getString("application_type")),
                product == null ? "" : product, rs.getDouble("amount"), rs.getString("unit"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
