package com.farmerp.finance;

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

public class BillDao implements Dao<Bill> {

    private static final String COLUMNS = "bill_id, farm_id, payee, amount_cents, due_date, paid";

    private final Database db;

    public BillDao(Database db) {
        this.db = db;
    }

    @Override
    public Bill insert(Bill b) {
        String sql = "INSERT INTO bill (farm_id, payee, amount_cents, due_date, paid) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, b.farmId());
            ps.setString(2, b.payee());
            ps.setLong(3, b.amountCents());
            ps.setString(4, b.dueDate().toString());
            ps.setInt(5, b.paid() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return b.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the bill", e);
        }
    }

    @Override
    public void update(Bill b) {
        String sql = "UPDATE bill SET payee = ?, amount_cents = ?, due_date = ?, paid = ? WHERE bill_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.payee());
            ps.setLong(2, b.amountCents());
            ps.setString(3, b.dueDate().toString());
            ps.setInt(4, b.paid() ? 1 : 0);
            ps.setLong(5, b.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the bill", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM bill WHERE bill_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the bill", e);
        }
    }

    @Override
    public Optional<Bill> findById(long id) {
        String sql = "SELECT " + COLUMNS + " FROM bill WHERE bill_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            List<Bill> list = read(ps);
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the bill", e);
        }
    }

    // Soonest due first
    public List<Bill> findByFarm(long farmId, boolean includePaid) {
        String sql = "SELECT " + COLUMNS + " FROM bill WHERE farm_id = ?"
                + (includePaid ? "" : " AND paid = 0") + " ORDER BY due_date, bill_id";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, farmId);
            return read(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load bills", e);
        }
    }

    public long unpaidDueBy(long farmId, LocalDate date) {
        String sql = "SELECT COALESCE(SUM(amount_cents), 0) FROM bill WHERE farm_id = ? AND paid = 0 AND due_date <= ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, farmId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not total bills", e);
        }
    }

    private List<Bill> read(PreparedStatement ps) throws SQLException {
        List<Bill> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Bill(rs.getLong("bill_id"), rs.getLong("farm_id"), rs.getString("payee"),
                        rs.getLong("amount_cents"), LocalDate.parse(rs.getString("due_date")), rs.getInt("paid") == 1));
            }
        }
        return list;
    }
}
