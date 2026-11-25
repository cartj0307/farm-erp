package com.farmerp.finance;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import com.farmerp.fields.FarmDao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class FinanceService {

    static final int MAX_TERM_MONTHS = 600;

    private final CashEntryDao cashDao;
    private final LoanDao loanDao;
    private final BillDao billDao;
    private final FarmDao farmDao;

    public FinanceService(Database db) {
        this(new CashEntryDao(db), new LoanDao(db), new BillDao(db), new FarmDao(db));
    }

    public FinanceService(CashEntryDao cashDao, LoanDao loanDao, BillDao billDao, FarmDao farmDao) {
        this.cashDao = cashDao;
        this.loanDao = loanDao;
        this.billDao = billDao;
        this.farmDao = farmDao;
    }

    // ---------- cash on hand (FR-10) ----------

    public List<CashEntry> listCash(long farmId) {
        return cashDao.findByFarm(farmId);
    }

    public Optional<CashEntry> latestCash(long farmId) {
        return cashDao.latestFor(farmId);
    }

    // Balance can be negative for an overdrawn account
    public CashEntry saveCash(CashEntry e) {
        requireFarm(e.farmId());
        if (e.date() == null) {
            throw new ValidationException("Date is required.");
        }
        if (e.id() == 0) {
            return cashDao.insert(e);
        }
        cashDao.update(e);
        return e;
    }

    public void deleteCash(long cashId) {
        cashDao.delete(cashId);
    }

    // ---------- loans (FR-11) ----------

    public List<Loan> listLoans(long farmId) {
        return loanDao.findByFarm(farmId);
    }

    public Loan saveLoan(Loan l) {
        requireFarm(l.farmId());
        String lender = Validator.requireText(l.lender(), "Lender");
        Validator.requirePositive(l.principalCents(), "Principal");
        Validator.requireRange(l.annualRatePct(), 0, 100, "Interest rate");
        if (l.termMonths() <= 0 || l.termMonths() > MAX_TERM_MONTHS) {
            throw new ValidationException("Term must be between 1 and " + MAX_TERM_MONTHS + " months.");
        }
        if (l.monthlyPaymentCents() < 0) {
            throw new ValidationException("Monthly payment can't be negative.");
        }
        if (l.startDate() == null) {
            throw new ValidationException("Start date is required.");
        }
        if (l.firstPaymentDate() == null) {
            throw new ValidationException("First payment date is required.");
        }
        if (l.firstPaymentDate().isBefore(l.startDate())) {
            throw new ValidationException("First payment date can't be before the start date.");
        }

        Loan clean = new Loan(l.id(), l.farmId(), lender, l.principalCents(), l.annualRatePct(), l.startDate(),
                l.termMonths(), l.monthlyPaymentCents(), l.firstPaymentDate());
        if (clean.id() == 0) {
            return loanDao.insert(clean);
        }
        loanDao.update(clean);
        return clean;
    }

    public void deleteLoan(long loanId) {
        loanDao.delete(loanId);
    }

    // Simple interest I = P x r x t, rounded half up to the cent
    public static long totalInterest(Loan l) {
        return BigDecimal.valueOf(l.principalCents())
                .multiply(BigDecimal.valueOf(l.annualRatePct()))
                .multiply(BigDecimal.valueOf(l.termMonths()))
                .divide(BigDecimal.valueOf(1200), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    // Monthly payments due after "after" and on or before "through"
    public static long paymentsDue(Loan l, LocalDate after, LocalDate through) {
        int count = 0;
        for (int i = 0; i < l.termMonths(); i++) {
            LocalDate due = l.firstPaymentDate().plusMonths(i);
            if (due.isAfter(through)) {
                break;
            }
            if (due.isAfter(after)) {
                count++;
            }
        }
        return count * l.monthlyPaymentCents();
    }

    // ---------- bills (FR-12) ----------

    public List<Bill> listBills(long farmId, boolean includePaid) {
        return billDao.findByFarm(farmId, includePaid);
    }

    public List<Bill> unpaidBills(long farmId) {
        return billDao.findByFarm(farmId, false);
    }

    public Bill saveBill(Bill b) {
        requireFarm(b.farmId());
        String payee = Validator.requireText(b.payee(), "Payee");
        Validator.requirePositive(b.amountCents(), "Amount");
        if (b.dueDate() == null) {
            throw new ValidationException("Due date is required.");
        }
        Bill clean = new Bill(b.id(), b.farmId(), payee, b.amountCents(), b.dueDate(), b.paid());
        if (clean.id() == 0) {
            return billDao.insert(clean);
        }
        billDao.update(clean);
        return clean;
    }

    public void setPaid(long billId, boolean paid) {
        Bill bill = billDao.findById(billId)
                .orElseThrow(() -> new ValidationException("That bill no longer exists."));
        billDao.update(new Bill(bill.id(), bill.farmId(), bill.payee(), bill.amountCents(), bill.dueDate(), paid));
    }

    public void deleteBill(long billId) {
        billDao.delete(billId);
    }

    // ---------- working capital (FR-13) ----------

    public WorkingCapital workingCapital(long farmId, LocalDate harvestDate) {
        return workingCapital(farmId, harvestDate, LocalDate.now());
    }

    // Loan payments count from the latest cash entry date (or today if there's none) through harvest
    WorkingCapital workingCapital(long farmId, LocalDate harvestDate, LocalDate today) {
        requireFarm(farmId);
        if (harvestDate == null) {
            throw new ValidationException("Pick a harvest date.");
        }
        Optional<CashEntry> cash = cashDao.latestFor(farmId);
        LocalDate asOf = cash.map(CashEntry::date).orElse(today);
        long cashCents = cash.map(CashEntry::balanceCents).orElse(0L);
        long bills = billDao.unpaidDueBy(farmId, harvestDate);
        long loans = 0;
        for (Loan l : loanDao.findByFarm(farmId)) {
            loans += paymentsDue(l, asOf, harvestDate);
        }
        return new WorkingCapital(harvestDate, cash.isPresent() ? asOf : null, cashCents, bills, loans);
    }

    private void requireFarm(long farmId) {
        if (farmDao.findById(farmId).isEmpty()) {
            throw new ValidationException("Pick a farm first.");
        }
    }
}
