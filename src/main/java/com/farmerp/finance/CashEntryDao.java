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

public class CashEntryDao implements Dao<CashEntry> {

    private static final String COLUMNS = "cash_id, farm_id, entry_date, balance_cents";

    private final Database db;

    public CashEntryDao(Database db) {
        this.db = db;
    }

    @Override
    public CashEntry insert(CashEntry e) {
        String sql = "INSERT INTO cash_entry (farm_id, entry_date, balance_cents) VALUES (?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, e.farmId());
            ps.setString(2, e.date().toString());
            ps.setLong(3, e.balanceCents());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return e.withId(keys.getLong(1));
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the cash entry", ex);
        }
    }

    @Override
    public void update(CashEntry e) {
        String sql = "UPDATE cash_entry SET entry_date = ?, balance_cents = ? WHERE cash_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.date().toString());
            ps.setLong(2, e.balanceCents());
            ps.setLong(3, e.id());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the cash entry", ex);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM cash_entry WHERE cash_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not delete the cash entry", ex);
        }
    }

    @Override
    public Optional<CashEntry> findById(long id) {
        List<CashEntry> list = query("SELECT " + COLUMNS + " FROM cash_entry WHERE cash_id = ?", id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // Newest first
    public List<CashEntry> findByFarm(long farmId) {
        return query("SELECT " + COLUMNS + " FROM cash_entry WHERE farm_id = ? "
                + "ORDER BY entry_date DESC, cash_id DESC", farmId);
    }

    public Optional<CashEntry> latestFor(long farmId) {
        List<CashEntry> list = query("SELECT " + COLUMNS + " FROM cash_entry WHERE farm_id = ? "
                + "ORDER BY entry_date DESC, cash_id DESC LIMIT 1", farmId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<CashEntry> query(String sql, long param) {
        List<CashEntry> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new CashEntry(rs.getLong("cash_id"), rs.getLong("farm_id"),
                            LocalDate.parse(rs.getString("entry_date")), rs.getLong("balance_cents")));
                }
            }
            return list;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load cash entries", ex);
        }
    }
}
