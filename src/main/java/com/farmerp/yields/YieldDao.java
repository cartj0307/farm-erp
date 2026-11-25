package com.farmerp.yields;

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

public class YieldDao implements Dao<YieldRecord> {

    private static final String COLUMNS = "yield_id, field_id, crop_year, crop, harvest_date, total_yield, "
            + "yield_unit, price_cents_per_unit";

    private final Database db;

    public YieldDao(Database db) {
        this.db = db;
    }

    @Override
    public YieldRecord insert(YieldRecord y) {
        String sql = "INSERT INTO yield_record (field_id, crop_year, crop, harvest_date, total_yield, yield_unit, "
                + "price_cents_per_unit) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, y.fieldId());
            ps.setInt(2, y.cropYear());
            ps.setString(3, y.crop());
            ps.setString(4, y.harvestDate().toString());
            ps.setDouble(5, y.totalYield());
            ps.setString(6, y.unit().name());
            ps.setLong(7, y.priceCentsPerUnit());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return y.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the yield", e);
        }
    }

    @Override
    public void update(YieldRecord y) {
        String sql = "UPDATE yield_record SET crop_year = ?, crop = ?, harvest_date = ?, total_yield = ?, "
                + "yield_unit = ?, price_cents_per_unit = ? WHERE yield_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, y.cropYear());
            ps.setString(2, y.crop());
            ps.setString(3, y.harvestDate().toString());
            ps.setDouble(4, y.totalYield());
            ps.setString(5, y.unit().name());
            ps.setLong(6, y.priceCentsPerUnit());
            ps.setLong(7, y.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the yield", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM yield_record WHERE yield_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the yield", e);
        }
    }

    @Override
    public Optional<YieldRecord> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM yield_record WHERE yield_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the yield", e);
        }
    }

    // Newest crop year first
    public List<YieldRecord> findByField(long fieldId) {
        String sql = "SELECT " + COLUMNS + " FROM yield_record WHERE field_id = ? "
                + "ORDER BY crop_year DESC, crop COLLATE NOCASE";
        return query(sql, ps -> ps.setLong(1, fieldId));
    }

    // Oldest crop year first
    public List<YieldRecord> findByFieldAndCrop(long fieldId, String crop) {
        String sql = "SELECT " + COLUMNS + " FROM yield_record WHERE field_id = ? AND crop = ? COLLATE NOCASE "
                + "ORDER BY crop_year";
        return query(sql, ps -> {
            ps.setLong(1, fieldId);
            ps.setString(2, crop);
        });
    }

    // Every yield on a farm for one crop year (FR-16)
    public List<YieldRecord> findByFarmYear(long farmId, int cropYear) {
        String sql = "SELECT y.yield_id, y.field_id, y.crop_year, y.crop, y.harvest_date, y.total_yield, "
                + "y.yield_unit, y.price_cents_per_unit "
                + "FROM yield_record y JOIN field f ON f.field_id = y.field_id "
                + "WHERE f.farm_id = ? AND y.crop_year = ? ORDER BY y.field_id, y.crop COLLATE NOCASE";
        return query(sql, ps -> {
            ps.setLong(1, farmId);
            ps.setInt(2, cropYear);
        });
    }

    // Case-insensitive check, skipping the record being edited
    public boolean exists(long fieldId, int cropYear, String crop, long excludeYieldId) {
        String sql = "SELECT 1 FROM yield_record WHERE field_id = ? AND crop_year = ? AND crop = ? COLLATE NOCASE "
                + "AND yield_id <> ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fieldId);
            ps.setInt(2, cropYear);
            ps.setString(3, crop);
            ps.setLong(4, excludeYieldId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check yields", e);
        }
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<YieldRecord> query(String sql, Binder binder) {
        List<YieldRecord> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load yields", e);
        }
    }

    private YieldRecord map(ResultSet rs) throws SQLException {
        return new YieldRecord(rs.getLong("yield_id"), rs.getLong("field_id"), rs.getInt("crop_year"),
                rs.getString("crop"), LocalDate.parse(rs.getString("harvest_date")), rs.getDouble("total_yield"),
                YieldUnit.valueOf(rs.getString("yield_unit")), rs.getLong("price_cents_per_unit"));
    }
}
