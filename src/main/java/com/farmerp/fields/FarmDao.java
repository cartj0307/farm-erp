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

public class FarmDao implements Dao<Farm> {

    private final Database db;

    public FarmDao(Database db) {
        this.db = db;
    }

    @Override
    public Farm insert(Farm farm) {
        String sql = "INSERT INTO farm (name, location, total_acres) VALUES (?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, farm.name());
            ps.setString(2, blankToNull(farm.location()));
            ps.setDouble(3, farm.totalAcres());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return farm.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the farm", e);
        }
    }

    @Override
    public void update(Farm farm) {
        String sql = "UPDATE farm SET name = ?, location = ?, total_acres = ? WHERE farm_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, farm.name());
            ps.setString(2, blankToNull(farm.location()));
            ps.setDouble(3, farm.totalAcres());
            ps.setLong(4, farm.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the farm", e);
        }
    }

    // Fields on the farm are removed by ON DELETE CASCADE
    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM farm WHERE farm_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the farm", e);
        }
    }

    @Override
    public Optional<Farm> findById(long id) {
        String sql = "SELECT farm_id, name, location, total_acres FROM farm WHERE farm_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the farm", e);
        }
    }

    public List<Farm> findAll() {
        String sql = "SELECT farm_id, name, location, total_acres FROM farm ORDER BY name COLLATE NOCASE";
        List<Farm> farms = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                farms.add(map(rs));
            }
            return farms;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load farms", e);
        }
    }

    private Farm map(ResultSet rs) throws SQLException {
        String location = rs.getString("location");
        return new Farm(rs.getLong("farm_id"), rs.getString("name"),
                location == null ? "" : location, rs.getDouble("total_acres"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
