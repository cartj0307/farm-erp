package com.farmerp.finance;

import com.farmerp.app.Selection;
import com.farmerp.common.DataAccessException;
import com.farmerp.common.Money;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import com.farmerp.fields.Farm;
import com.farmerp.fields.FieldService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class FinanceController {

    private static final DecimalFormat RATE = new DecimalFormat("#,##0.###");

    private final FinanceService finance;
    private final FieldService fields;
    private final Selection selection;
    private long seenFarmVersion;

    @FXML private ComboBox<Farm> farmCombo;
    @FXML private DatePicker harvestPicker;
    @FXML private Label workingCapitalLabel;
    @FXML private Label workingCapitalDetail;
    @FXML private TabPane financeTabs;

    @FXML private TableView<CashEntry> cashTable;
    @FXML private TableColumn<CashEntry, String> cashDateCol;
    @FXML private TableColumn<CashEntry, String> cashBalanceCol;
    @FXML private Label currentCashLabel;
    @FXML private DatePicker cashDatePicker;
    @FXML private TextField cashBalanceField;
    @FXML private Button deleteCashButton;

    @FXML private TableView<Loan> loanTable;
    @FXML private TableColumn<Loan, String> lenderCol;
    @FXML private TableColumn<Loan, String> principalCol;
    @FXML private TableColumn<Loan, String> rateCol;
    @FXML private TableColumn<Loan, String> startCol;
    @FXML private TableColumn<Loan, String> termCol;
    @FXML private TableColumn<Loan, String> paymentCol;
    @FXML private TableColumn<Loan, String> firstPaymentCol;
    @FXML private TableColumn<Loan, String> interestCol;
    @FXML private TextField lenderField;
    @FXML private TextField principalField;
    @FXML private TextField rateField;
    @FXML private DatePicker startPicker;
    @FXML private TextField termField;
    @FXML private TextField paymentField;
    @FXML private DatePicker firstPaymentPicker;
    @FXML private Button deleteLoanButton;

    @FXML private CheckBox showPaidCheck;
    @FXML private TableView<Bill> billTable;
    @FXML private TableColumn<Bill, String> dueCol;
    @FXML private TableColumn<Bill, String> payeeCol;
    @FXML private TableColumn<Bill, String> billAmountCol;
    @FXML private TableColumn<Bill, String> statusCol;
    @FXML private Label unpaidTotalLabel;
    @FXML private TextField payeeField;
    @FXML private TextField billAmountField;
    @FXML private DatePicker duePicker;
    @FXML private Button paidButton;
    @FXML private Button deleteBillButton;

    // 0 means the form is for a new record
    private long editingCashId;
    private long editingLoanId;
    private long editingBillId;

    public FinanceController(FinanceService finance, FieldService fields, Selection selection) {
        this.finance = finance;
        this.fields = fields;
        this.selection = selection;
    }

    @FXML
    private void initialize() {
        farmCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Farm f) {
                return f == null ? "" : f.name();
            }

            @Override
            public Farm fromString(String s) {
                return null;
            }
        });

        text(cashDateCol, c -> c.date().toString());
        text(cashBalanceCol, c -> Money.format(c.balanceCents()));
        cashBalanceCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        text(lenderCol, Loan::lender);
        text(principalCol, l -> Money.format(l.principalCents()));
        text(rateCol, l -> RATE.format(l.annualRatePct()) + "%");
        text(startCol, l -> l.startDate().toString());
        text(termCol, l -> l.termMonths() + " mo");
        text(paymentCol, l -> Money.format(l.monthlyPaymentCents()));
        text(firstPaymentCol, l -> l.firstPaymentDate().toString());
        text(interestCol, l -> Money.format(FinanceService.totalInterest(l)));
        principalCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        paymentCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        interestCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        text(dueCol, b -> b.dueDate().toString());
        text(payeeCol, Bill::payee);
        text(billAmountCol, b -> Money.format(b.amountCents()));
        text(statusCol, b -> b.paid() ? "Paid" : (b.dueDate().isBefore(LocalDate.now()) ? "Overdue" : "Unpaid"));
        billAmountCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        harvestPicker.setValue(defaultHarvestDate());

        financeTabs.disableProperty().bind(farmCombo.getSelectionModel().selectedItemProperty().isNull());
        harvestPicker.disableProperty().bind(farmCombo.getSelectionModel().selectedItemProperty().isNull());
        deleteCashButton.disableProperty().bind(cashTable.getSelectionModel().selectedItemProperty().isNull());
        deleteLoanButton.disableProperty().bind(loanTable.getSelectionModel().selectedItemProperty().isNull());
        deleteBillButton.disableProperty().bind(billTable.getSelectionModel().selectedItemProperty().isNull());
        paidButton.disableProperty().bind(billTable.getSelectionModel().selectedItemProperty().isNull());

        farmCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, farm) -> loadAll());
        harvestPicker.valueProperty().addListener((obs, old, d) -> showWorkingCapital());
        showPaidCheck.selectedProperty().addListener((obs, old, v) -> loadBills());
        cashTable.getSelectionModel().selectedItemProperty().addListener((obs, old, c) -> showCash(c));
        loanTable.getSelectionModel().selectedItemProperty().addListener((obs, old, l) -> showLoan(l));
        billTable.getSelectionModel().selectedItemProperty().addListener((obs, old, b) -> showBill(b));

        refresh();
    }

    // Called when the Finance tab is opened so new farms show up
    public void refresh() {
        long farmId = farmCombo.getValue() == null ? 0 : farmCombo.getValue().id();

        // Follow the Fields tab only when its farm has changed since this tab last looked
        if (selection.farmVersion() != seenFarmVersion) {
            seenFarmVersion = selection.farmVersion();
            farmId = selection.farmId();
        }

        long pickFarm = farmId;
        farmCombo.getItems().setAll(fields.listFarms());
        Farm match = farmCombo.getItems().stream()
                .filter(f -> f.id() == pickFarm).findFirst().orElse(null);
        farmCombo.setValue(match);
        loadAll();
    }

    private void loadAll() {
        loadCash();
        loadLoans();
        loadBills();
    }

    // October 15 this year, or next year once that's passed
    private static LocalDate defaultHarvestDate() {
        LocalDate today = LocalDate.now();
        LocalDate harvest = LocalDate.of(today.getYear(), 10, 15);
        return today.isAfter(harvest) ? harvest.plusYears(1) : harvest;
    }

    // ---------- working capital (FR-13) ----------

    private void showWorkingCapital() {
        Farm farm = farmCombo.getValue();
        if (farm == null || harvestPicker.getValue() == null) {
            workingCapitalLabel.setText("");
            workingCapitalDetail.setText("");
            return;
        }
        WorkingCapital w = finance.workingCapital(farm.id(), harvestPicker.getValue());
        workingCapitalLabel.setText(Money.format(w.totalCents()));
        workingCapitalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: "
                + (w.totalCents() < 0 ? "#B00020;" : "#2C5F2D;"));
        String cashText = w.cashAsOf() == null ? "no cash entered" : "cash " + Money.format(w.cashCents())
                + " as of " + w.cashAsOf();
        workingCapitalDetail.setText(cashText + "  -  unpaid bills " + Money.format(w.billsCents())
                + "  -  loan payments " + Money.format(w.loanPaymentsCents()));
    }

    // ---------- cash on hand ----------

    private void loadCash() {
        Farm farm = farmCombo.getValue();
        if (farm == null) {
            cashTable.getItems().clear();
            currentCashLabel.setText("");
        } else {
            cashTable.getItems().setAll(finance.listCash(farm.id()));
            currentCashLabel.setText(finance.latestCash(farm.id())
                    .map(c -> "Current cash on hand: " + Money.format(c.balanceCents()) + " as of " + c.date())
                    .orElse("No cash on hand entered yet."));
        }
        clearCashForm();
        showWorkingCapital();
    }

    @FXML
    private void onNewCash() {
        cashTable.getSelectionModel().clearSelection();
        clearCashForm();
    }

    @FXML
    private void onSaveCash() {
        Farm farm = farmCombo.getValue();
        if (farm == null) {
            return;
        }
        try {
            long cents = Money.parseCents(cashBalanceField.getText(), "Balance");
            CashEntry saved = finance.saveCash(new CashEntry(editingCashId, farm.id(), cashDatePicker.getValue(), cents));
            loadCash();
            select(cashTable, saved.id(), CashEntry::id);
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteCash() {
        CashEntry c = cashTable.getSelectionModel().getSelectedItem();
        if (c != null && confirm("Delete the cash entry from " + c.date() + "?")) {
            try {
                finance.deleteCash(c.id());
                loadCash();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showCash(CashEntry c) {
        if (c == null) {
            return;
        }
        editingCashId = c.id();
        cashDatePicker.setValue(c.date());
        cashBalanceField.setText(dollars(c.balanceCents()));
    }

    private void clearCashForm() {
        editingCashId = 0;
        cashDatePicker.setValue(LocalDate.now());
        cashBalanceField.clear();
    }

    // ---------- loans ----------

    private void loadLoans() {
        Farm farm = farmCombo.getValue();
        loanTable.getItems().setAll(farm == null ? List.of() : finance.listLoans(farm.id()));
        clearLoanForm();
        showWorkingCapital();
    }

    @FXML
    private void onNewLoan() {
        loanTable.getSelectionModel().clearSelection();
        clearLoanForm();
    }

    @FXML
    private void onSaveLoan() {
        Farm farm = farmCombo.getValue();
        if (farm == null) {
            return;
        }
        try {
            long principal = Money.parseCents(principalField.getText(), "Principal");
            double rate = Validator.parseNumber(rateField.getText(), "Interest rate");
            int term = wholeNumber(termField.getText(), "Term");
            long payment = Money.parseCents(paymentField.getText(), "Monthly payment");
            Loan saved = finance.saveLoan(new Loan(editingLoanId, farm.id(), lenderField.getText(), principal, rate,
                    startPicker.getValue(), term, payment, firstPaymentPicker.getValue()));
            loadLoans();
            select(loanTable, saved.id(), Loan::id);
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteLoan() {
        Loan l = loanTable.getSelectionModel().getSelectedItem();
        if (l != null && confirm("Delete the loan from " + l.lender() + "?")) {
            try {
                finance.deleteLoan(l.id());
                loadLoans();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showLoan(Loan l) {
        if (l == null) {
            return;
        }
        editingLoanId = l.id();
        lenderField.setText(l.lender());
        principalField.setText(dollars(l.principalCents()));
        rateField.setText(RATE.format(l.annualRatePct()));
        startPicker.setValue(l.startDate());
        termField.setText(String.valueOf(l.termMonths()));
        paymentField.setText(dollars(l.monthlyPaymentCents()));
        firstPaymentPicker.setValue(l.firstPaymentDate());
    }

    private void clearLoanForm() {
        editingLoanId = 0;
        lenderField.clear();
        principalField.clear();
        rateField.clear();
        startPicker.setValue(LocalDate.now());
        termField.clear();
        paymentField.clear();
        firstPaymentPicker.setValue(LocalDate.now().plusMonths(1));
    }

    // ---------- bills ----------

    private void loadBills() {
        Farm farm = farmCombo.getValue();
        if (farm == null) {
            billTable.getItems().clear();
            unpaidTotalLabel.setText("");
        } else {
            billTable.getItems().setAll(finance.listBills(farm.id(), showPaidCheck.isSelected()));
            long unpaid = finance.unpaidBills(farm.id()).stream().mapToLong(Bill::amountCents).sum();
            unpaidTotalLabel.setText("Total unpaid: " + Money.format(unpaid));
        }
        clearBillForm();
        showWorkingCapital();
    }

    @FXML
    private void onNewBill() {
        billTable.getSelectionModel().clearSelection();
        clearBillForm();
    }

    @FXML
    private void onSaveBill() {
        Farm farm = farmCombo.getValue();
        if (farm == null) {
            return;
        }
        try {
            long cents = Money.parseCents(billAmountField.getText(), "Amount");
            boolean paid = billTable.getItems().stream()
                    .filter(b -> b.id() == editingBillId).findFirst().map(Bill::paid).orElse(false);
            Bill saved = finance.saveBill(new Bill(editingBillId, farm.id(), payeeField.getText(), cents,
                    duePicker.getValue(), paid));
            loadBills();
            select(billTable, saved.id(), Bill::id);
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onTogglePaid() {
        Bill b = billTable.getSelectionModel().getSelectedItem();
        if (b == null) {
            return;
        }
        try {
            finance.setPaid(b.id(), !b.paid());
            loadBills();
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteBill() {
        Bill b = billTable.getSelectionModel().getSelectedItem();
        if (b != null && confirm("Delete the " + Money.format(b.amountCents()) + " bill to " + b.payee() + "?")) {
            try {
                finance.deleteBill(b.id());
                loadBills();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showBill(Bill b) {
        paidButton.setText(b != null && b.paid() ? "Mark unpaid" : "Mark paid");
        if (b == null) {
            return;
        }
        editingBillId = b.id();
        payeeField.setText(b.payee());
        billAmountField.setText(dollars(b.amountCents()));
        duePicker.setValue(b.dueDate());
    }

    private void clearBillForm() {
        editingBillId = 0;
        payeeField.clear();
        billAmountField.clear();
        duePicker.setValue(LocalDate.now().plusDays(30));
    }

    // ---------- helpers ----------

    private static String dollars(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static int wholeNumber(String text, String name) {
        double value = Validator.parseNumber(text, name);
        if (value != Math.rint(value)) {
            throw new ValidationException(name + " must be a whole number of months.");
        }
        return (int) value;
    }

    private static <T> void select(TableView<T> table, long id, Function<T, Long> getId) {
        table.getItems().stream().filter(t -> getId.apply(t) == id).findFirst()
                .ifPresent(t -> table.getSelectionModel().select(t));
    }

    private static <T> void text(TableColumn<T, String> col, Function<T, String> getter) {
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(getter.apply(c.getValue())));
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.YES;
    }
}
