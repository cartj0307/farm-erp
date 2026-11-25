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

public class LoanDao implements Dao<Loan> {

    private static final String COLUMNS = "loan_id, farm_id, lender, principal_cents, annual_rate, start_date, "
            + "term_months, monthly_payment_cents, first_payment_date";

    private final Database db;

    public LoanDao(Database db) {
        this.db = db;
    }

    @Override
    public Loan insert(Loan l) {
        String sql = "INSERT INTO loan (farm_id, lender, principal_cents, annual_rate, start_date, term_months, "
                + "monthly_payment_cents, first_payment_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, l.farmId());
            bind(ps, 2, l);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return l.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the loan", e);
        }
    }

    @Override
    public void update(Loan l) {
        String sql = "UPDATE loan SET lender = ?, principal_cents = ?, annual_rate = ?, start_date = ?, "
                + "term_months = ?, monthly_payment_cents = ?, first_payment_date = ? WHERE loan_id = ?";
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, 1, l);
            ps.setLong(8, l.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the loan", e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM loan WHERE loan_id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the loan", e);
        }
    }

    @Override
    public Optional<Loan> findById(long id) {
        List<Loan> list = query("SELECT " + COLUMNS + " FROM loan WHERE loan_id = ?", id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<Loan> findByFarm(long farmId) {
        return query("SELECT " + COLUMNS + " FROM loan WHERE farm_id = ? ORDER BY start_date DESC, loan_id DESC", farmId);
    }

    // Sets the seven loan values starting at parameter index start
    private static void bind(PreparedStatement ps, int start, Loan l) throws SQLException {
        ps.setString(start, l.lender());
        ps.setLong(start + 1, l.principalCents());
        ps.setDouble(start + 2, l.annualRatePct());
        ps.setString(start + 3, l.startDate().toString());
        ps.setInt(start + 4, l.termMonths());
        ps.setLong(start + 5, l.monthlyPaymentCents());
        ps.setString(start + 6, l.firstPaymentDate().toString());
    }

    private List<Loan> query(String sql, long param) {
        List<Loan> list = new ArrayList<>();
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Loan(rs.getLong("loan_id"), rs.getLong("farm_id"), rs.getString("lender"),
                            rs.getLong("principal_cents"), rs.getDouble("annual_rate"),
                            LocalDate.parse(rs.getString("start_date")), rs.getInt("term_months"),
                            rs.getLong("monthly_payment_cents"), LocalDate.parse(rs.getString("first_payment_date"))));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load loans", e);
        }
    }
}
