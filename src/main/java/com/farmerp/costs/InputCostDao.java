package com.farmerp.costs;

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

public class InputCostDao implements Dao<InputCost> {

    private static final String COLUMNS = "cost_id, field_id, crop_year, cost_date, category, description, amount_cents";

    private final Database db;

    public InputCostDao(Database db) {
        this.db = db;
    }

    @Override
    public InputCost insert(InputCost cost) {
        String sql = "INSERT INTO input_cost (field_id, crop_year, cost_date, category, description, amount_cents) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, cost.fieldId());
            ps.setInt(2, cost.cropYear());
            ps.setString(3, cost.date().toString());
            ps.setString(4, cost.category().name());
            ps.setString(5, blankToNull(cost.description()));
            ps.setLong(6, cost.amountCents());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return cost.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the cost", e);
        }
    }

    @Override
    public void update(InputCost cost) {
        String sql = "UPDATE input_cost SET crop_year = ?, cost_date = ?, category = ?, description = ?, "
                + "amount_cents = ? WHERE cost_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, cost.cropYear());
            ps.setString(2, cost.date().toString());
            ps.setString(3, cost.category().name());
            ps.setString(4, blankToNull(cost.description()));
            ps.setLong(5, cost.amountCents());
            ps.setLong(6, cost.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the cost", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM input_cost WHERE cost_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the cost", e);
        }
    }

    @Override
    public Optional<InputCost> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM input_cost WHERE cost_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the cost", e);
        }
    }

    // Newest first
    public List<InputCost> findByField(long fieldId, int cropYear) {
        String sql = "SELECT " + COLUMNS + " FROM input_cost WHERE field_id = ? AND crop_year = ? "
                + "ORDER BY cost_date DESC, cost_id DESC";
        List<InputCost> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fieldId);
            ps.setInt(2, cropYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load costs", e);
        }
    }

    public long totalForField(long fieldId, int cropYear) {
        String sql = "SELECT COALESCE(SUM(amount_cents), 0) FROM input_cost WHERE field_id = ? AND crop_year = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, fieldId);
            ps.setInt(2, cropYear);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not total costs", e);
        }
    }

    // Grouped totals for every field on a farm in one query (FR-14, NFR-6)
    public List<CostTotal> totalsByField(long farmId, int cropYear) {
        String sql = "SELECT c.field_id, c.category, SUM(c.amount_cents) AS total "
                + "FROM input_cost c JOIN field f ON f.field_id = c.field_id "
                + "WHERE f.farm_id = ? AND c.crop_year = ? "
                + "GROUP BY c.field_id, c.category";
        List<CostTotal> totals = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, farmId);
            ps.setInt(2, cropYear);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    totals.add(new CostTotal(rs.getLong("field_id"),
                            CostCategory.valueOf(rs.getString("category")), rs.getLong("total")));
                }
            }
            return totals;
        } catch (SQLException e) {
            throw new DataAccessException("Could not total costs", e);
        }
    }

    private InputCost map(ResultSet rs) throws SQLException {
        String description = rs.getString("description");
        return new InputCost(rs.getLong("cost_id"), rs.getLong("field_id"), rs.getInt("crop_year"),
                LocalDate.parse(rs.getString("cost_date")), CostCategory.valueOf(rs.getString("category")),
                description == null ? "" : description, rs.getLong("amount_cents"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
