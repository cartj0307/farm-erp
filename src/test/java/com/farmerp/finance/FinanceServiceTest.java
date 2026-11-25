package com.farmerp.finance;

import com.farmerp.app.Database;
import com.farmerp.common.ValidationException;
import com.farmerp.fields.Farm;
import com.farmerp.fields.FieldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TC-10 cash, TC-11 loans, TC-12 bills, TC-13 working capital
class FinanceServiceTest {

    @TempDir
    Path folder;

    private FinanceService finance;
    private FieldService fields;
    private long farmId;

    @BeforeEach
    void setUp() {
        Database db = new Database(folder.resolve("test.db"));
        db.initSchema();
        finance = new FinanceService(db);
        fields = new FieldService(db);
        farmId = fields.saveFarm(new Farm(0, "Home Farm", "Allendale", 80)).id();
    }

    private static LocalDate day(String iso) {
        return LocalDate.parse(iso);
    }

    private CashEntry cash(String date, long cents) {
        return new CashEntry(0, farmId, date == null ? null : day(date), cents);
    }

    private Loan loan(String lender, long principal, double rate, String start, int term, long payment, String first) {
        return new Loan(0, farmId, lender, principal, rate, start == null ? null : day(start), term, payment,
                first == null ? null : day(first));
    }

    private Bill bill(String payee, long cents, String due) {
        return new Bill(0, farmId, payee, cents, due == null ? null : day(due), false);
    }

    // ---------- TC-10 cash on hand ----------

    @Test
    void savesCashAndShowsLatestByDate() {
        finance.saveCash(cash("2026-11-01", 4850000));
        finance.saveCash(cash("2026-09-01", 6000000));
        assertEquals(4850000, finance.latestCash(farmId).orElseThrow().balanceCents());
        assertEquals(day("2026-11-01"), finance.listCash(farmId).get(0).date());
    }

    @Test
    void allowsNegativeBalance() {
        CashEntry saved = finance.saveCash(cash("2026-11-01", -25000));
        assertEquals(-25000, saved.balanceCents());
    }

    @Test
    void rejectsCashWithoutDateOrFarm() {
        assertThrows(ValidationException.class, () -> finance.saveCash(cash(null, 1000)));
        assertThrows(ValidationException.class,
                () -> finance.saveCash(new CashEntry(0, 999, day("2026-11-01"), 1000)));
    }

    @Test
    void updatesAndDeletesCash() {
        CashEntry saved = finance.saveCash(cash("2026-11-01", 4850000));
        finance.saveCash(new CashEntry(saved.id(), farmId, day("2026-11-02"), 4900000));
        assertEquals(4900000, finance.latestCash(farmId).orElseThrow().balanceCents());
        finance.deleteCash(saved.id());
        assertTrue(finance.latestCash(farmId).isEmpty());
    }

    // ---------- TC-11 loans ----------

    @Test
    void savesLoanAndCalculatesSimpleInterest() {
        Loan saved = finance.saveLoan(loan("Farm Credit", 10000000, 6, "2026-01-01", 60, 193300, "2026-02-01"));
        assertTrue(saved.id() > 0);
        assertEquals(List.of(saved), finance.listLoans(farmId));
        // $100,000 x 6% x 5 years = $30,000
        assertEquals(3000000, FinanceService.totalInterest(saved));
    }

    @Test
    void interestRoundsToTheNearestCent() {
        // $1,000.01 x 5.5% x 1 year = $55.00055
        Loan l = loan("Bank", 100001, 5.5, "2026-01-01", 12, 0, "2026-02-01");
        assertEquals(5500, FinanceService.totalInterest(l));
    }

    @Test
    void rejectsBadLoans() {
        assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan(" ", 1000000, 6, "2026-01-01", 60, 0, "2026-02-01")));
        assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan("Bank", 0, 6, "2026-01-01", 60, 0, "2026-02-01")));
        assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan("Bank", 1000000, 101, "2026-01-01", 60, 0, "2026-02-01")));
        assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan("Bank", 1000000, 6, "2026-01-01", 0, 0, "2026-02-01")));
        assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan("Bank", 1000000, 6, "2026-01-01", 60, -1, "2026-02-01")));
        assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan("Bank", 1000000, 6, null, 60, 0, "2026-02-01")));
    }

    @Test
    void rejectsFirstPaymentBeforeStart() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> finance.saveLoan(loan("Bank", 1000000, 6, "2026-03-01", 60, 0, "2026-02-01")));
        assertEquals("First payment date can't be before the start date.", e.getMessage());
    }

    @Test
    void updatesAndDeletesLoan() {
        Loan saved = finance.saveLoan(loan("Bank", 1000000, 6, "2026-01-01", 60, 19330, "2026-02-01"));
        finance.saveLoan(new Loan(saved.id(), farmId, "Bank", 1000000, 5.25, day("2026-01-01"), 60, 19000, day("2026-02-01")));
        assertEquals(5.25, finance.listLoans(farmId).get(0).annualRatePct());
        finance.deleteLoan(saved.id());
        assertTrue(finance.listLoans(farmId).isEmpty());
    }

    @Test
    void countsLoanPaymentsInAWindow() {
        Loan l = loan("Bank", 2400000, 0, "2026-05-15", 24, 100000, "2026-06-01");
        // after Nov 1 2026 through Oct 15 2027: Dec through Oct = 11 payments
        assertEquals(1100000, FinanceService.paymentsDue(l, day("2026-11-01"), day("2027-10-15")));
        // stops when the loan is paid off: Jun 2026 to May 2028 is 24 payments
        assertEquals(2400000, FinanceService.paymentsDue(l, day("2026-01-01"), day("2030-01-01")));
        assertEquals(0, FinanceService.paymentsDue(l, day("2026-11-01"), day("2026-11-01")));
    }

    // ---------- TC-12 bills ----------

    @Test
    void savesBillsAndListsUnpaidSoonestFirst() {
        finance.saveBill(bill("Co-op fertilizer", 500000, "2026-12-01"));
        finance.saveBill(bill("Seed dealer", 825000, "2026-11-20"));
        List<Bill> unpaid = finance.unpaidBills(farmId);
        assertEquals("Seed dealer", unpaid.get(0).payee());
        assertEquals("Co-op fertilizer", unpaid.get(1).payee());
    }

    @Test
    void markingPaidRemovesFromUnpaidList() {
        Bill saved = finance.saveBill(bill("Seed dealer", 825000, "2026-11-20"));
        finance.setPaid(saved.id(), true);
        assertTrue(finance.unpaidBills(farmId).isEmpty());
        assertEquals(1, finance.listBills(farmId, true).size());
        assertTrue(finance.listBills(farmId, true).get(0).paid());
        finance.setPaid(saved.id(), false);
        assertFalse(finance.unpaidBills(farmId).isEmpty());
    }

    @Test
    void rejectsBadBills() {
        assertThrows(ValidationException.class, () -> finance.saveBill(bill(" ", 1000, "2026-12-01")));
        assertThrows(ValidationException.class, () -> finance.saveBill(bill("Co-op", 0, "2026-12-01")));
        assertThrows(ValidationException.class, () -> finance.saveBill(bill("Co-op", 1000, null)));
    }

    @Test
    void updatesAndDeletesBill() {
        Bill saved = finance.saveBill(bill("Co-op", 500000, "2026-12-01"));
        finance.saveBill(new Bill(saved.id(), farmId, "Co-op", 520000, day("2026-12-05"), false));
        assertEquals(520000, finance.unpaidBills(farmId).get(0).amountCents());
        finance.deleteBill(saved.id());
        assertTrue(finance.listBills(farmId, true).isEmpty());
    }

    // ---------- TC-13 working capital ----------

    @Test
    void workingCapitalIsCashMinusBillsMinusLoanPayments() {
        finance.saveCash(cash("2026-09-01", 6000000));
        finance.saveCash(cash("2026-11-01", 5000000));
        finance.saveBill(bill("Co-op", 500000, "2026-12-01"));
        finance.saveBill(bill("Overdue repair", 50000, "2026-10-01"));
        finance.saveBill(bill("Spring seed", 200000, "2027-03-01"));
        finance.saveBill(bill("After harvest", 100000, "2027-11-01"));
        Bill paid = finance.saveBill(bill("Already paid", 999900, "2026-12-15"));
        finance.setPaid(paid.id(), true);
        finance.saveLoan(loan("Bank", 2400000, 0, "2026-05-15", 24, 100000, "2026-06-01"));

        WorkingCapital w = finance.workingCapital(farmId, day("2027-10-15"), day("2026-11-06"));
        assertEquals(5000000, w.cashCents());
        assertEquals(day("2026-11-01"), w.cashAsOf());
        assertEquals(750000, w.billsCents());
        assertEquals(1100000, w.loanPaymentsCents());
        // $50,000 - $7,500 - $11,000 = $31,500
        assertEquals(3150000, w.totalCents());
    }

    @Test
    void workingCapitalWithNoCashCountsFromToday() {
        finance.saveLoan(loan("Bank", 2400000, 0, "2026-05-15", 24, 100000, "2026-06-01"));
        WorkingCapital w = finance.workingCapital(farmId, day("2027-01-15"), day("2026-11-06"));
        assertNull(w.cashAsOf());
        assertEquals(0, w.cashCents());
        // Dec 1 and Jan 1
        assertEquals(200000, w.loanPaymentsCents());
        assertEquals(-200000, w.totalCents());
    }

    @Test
    void workingCapitalNeedsHarvestDate() {
        assertThrows(ValidationException.class, () -> finance.workingCapital(farmId, null, day("2026-11-06")));
    }

    @Test
    void deletingFarmDeletesFinanceRecords() {
        finance.saveCash(cash("2026-11-01", 5000000));
        finance.saveBill(bill("Co-op", 500000, "2026-12-01"));
        finance.saveLoan(loan("Bank", 2400000, 0, "2026-05-15", 24, 100000, "2026-06-01"));
        fields.deleteFarm(farmId);
        assertTrue(finance.listCash(farmId).isEmpty());
        assertTrue(finance.listBills(farmId, true).isEmpty());
        assertTrue(finance.listLoans(farmId).isEmpty());
    }
}
