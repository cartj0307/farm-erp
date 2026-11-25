package com.farmerp.summary;

import com.farmerp.app.Selection;
import com.farmerp.common.Money;
import com.farmerp.costs.CostCategory;
import com.farmerp.fields.Farm;
import com.farmerp.fields.FieldService;
import com.farmerp.finance.FinanceService;
import com.farmerp.finance.WorkingCapital;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SummaryController {

    private static final DecimalFormat ACRES = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PCT = new DecimalFormat("0.0'%'");
    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 2100;

    private final SummaryService summaryService;
    private final FinanceService financeService;
    private final FieldService fields;
    private final Selection selection;
    private long seenFarmVersion;

    @FXML private ComboBox<Farm> farmCombo;
    @FXML private Spinner<Integer> yearSpinner;
    @FXML private VBox summaryPane;

    @FXML private Label farmTotalsLabel;
    @FXML private Label farmNetLabel;

    @FXML private TableView<FieldSummary> fieldTable;
    @FXML private TableColumn<FieldSummary, String> fieldCol;
    @FXML private TableColumn<FieldSummary, String> acresCol;
    @FXML private TableColumn<FieldSummary, String> cropCol;
    @FXML private TableColumn<FieldSummary, String> costCol;
    @FXML private TableColumn<FieldSummary, String> costPerAcreCol;
    @FXML private TableColumn<FieldSummary, String> revenueCol;
    @FXML private TableColumn<FieldSummary, String> netCol;
    @FXML private TableColumn<FieldSummary, String> netPerAcreCol;

    @FXML private Label categoryHeader;
    @FXML private TableView<CategoryRow> categoryTable;
    @FXML private TableColumn<CategoryRow, String> categoryCol;
    @FXML private TableColumn<CategoryRow, String> categoryAmountCol;
    @FXML private TableColumn<CategoryRow, String> categoryShareCol;

    @FXML private DatePicker harvestPicker;
    @FXML private Label workingCapitalLabel;
    @FXML private Label workingCapitalDetail;

    private FarmSummary current;

    // One row in the costs by category table
    public record CategoryRow(String category, long cents, long totalCents) {
    }

    public SummaryController(SummaryService summaryService, FinanceService financeService, FieldService fields,
                             Selection selection) {
        this.summaryService = summaryService;
        this.financeService = financeService;
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

        yearSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                MIN_YEAR, MAX_YEAR, LocalDate.now().getYear()));
        yearSpinner.getEditor().setOnAction(e -> commitYear());
        yearSpinner.getEditor().focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commitYear();
            }
        });

        text(fieldCol, FieldSummary::fieldName);
        text(acresCol, f -> ACRES.format(f.acres()));
        text(cropCol, f -> f.hasYield() ? f.crops() : "No yield yet");
        text(costCol, f -> Money.format(f.costCents()));
        text(costPerAcreCol, f -> Money.format(f.costPerAcreCents()));
        text(revenueCol, f -> money(f.revenueCents()));
        text(netCol, f -> money(f.netCents()));
        text(netPerAcreCol, f -> money(f.netPerAcreCents()));
        for (TableColumn<FieldSummary, String> c : List.of(acresCol, costCol, costPerAcreCol, revenueCol, netCol, netPerAcreCol)) {
            c.setStyle("-fx-alignment: CENTER-RIGHT;");
        }

        text(categoryCol, CategoryRow::category);
        text(categoryAmountCol, r -> Money.format(r.cents()));
        text(categoryShareCol, r -> r.totalCents() == 0 ? "" : PCT.format(100.0 * r.cents() / r.totalCents()));
        categoryAmountCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        categoryShareCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        harvestPicker.setValue(defaultHarvestDate());

        summaryPane.disableProperty().bind(farmCombo.getSelectionModel().selectedItemProperty().isNull());

        farmCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, farm) -> onShowSummary());
        yearSpinner.valueProperty().addListener((obs, old, year) -> onShowSummary());
        harvestPicker.valueProperty().addListener((obs, old, d) -> onHarvestDateChanged());
        fieldTable.getSelectionModel().selectedItemProperty().addListener((obs, old, f) -> showCategories(f));

        refresh();
    }

    // Called when the Summary tab is opened so the numbers are current
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
        onShowSummary();
    }

    // UC-11 steps 2 and 3
    private void onShowSummary() {
        Farm farm = farmCombo.getValue();
        if (farm == null) {
            current = null;
            fieldTable.getItems().clear();
            categoryTable.getItems().clear();
            farmTotalsLabel.setText("");
            farmNetLabel.setText("");
            categoryHeader.setText("Costs by category");
            onHarvestDateChanged();
            return;
        }
        current = summaryService.farmSummary(farm.id(), yearSpinner.getValue());
        fieldTable.getItems().setAll(current.fields());

        farmTotalsLabel.setText(current.farmName() + ", " + current.cropYear() + ":  total cost "
                + Money.format(current.totalCostCents()) + " on " + ACRES.format(current.totalAcres())
                + " acres  =  " + Money.format(current.costPerAcreCents()) + " per acre");
        if (current.revenueCents() == null) {
            farmNetLabel.setText("No yields entered for " + current.cropYear() + " yet, so there's no revenue or net return.");
            farmNetLabel.setStyle("-fx-text-fill: #555555;");
        } else {
            farmNetLabel.setText("Revenue " + Money.format(current.revenueCents()) + "  -  cost "
                    + Money.format(current.totalCostCents()) + "  =  net " + Money.format(current.netCents())
                    + "  (" + Money.format(current.netPerAcreCents()) + " per acre)");
            farmNetLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                    + (current.netCents() < 0 ? "#B00020;" : "#2C5F2D;"));
        }
        showCategories(null);
        onHarvestDateChanged();
    }

    // Whole farm when no field row is picked
    private void showCategories(FieldSummary field) {
        if (current == null) {
            return;
        }
        Map<CostCategory, Long> byCategory = field == null ? current.costByCategory() : field.costByCategory();
        long total = field == null ? current.totalCostCents() : field.costCents();
        categoryHeader.setText("Costs by category: " + (field == null ? "whole farm" : field.fieldName()));
        List<CategoryRow> rows = new ArrayList<>();
        for (CostCategory c : CostCategory.values()) {
            Long cents = byCategory.get(c);
            if (cents != null && cents != 0) {
                rows.add(new CategoryRow(c.toString(), cents, total));
            }
        }
        categoryTable.getItems().setAll(rows);
    }

    // UC-11 steps 4 and 5
    private void onHarvestDateChanged() {
        Farm farm = farmCombo.getValue();
        if (farm == null || harvestPicker.getValue() == null) {
            workingCapitalLabel.setText("");
            workingCapitalDetail.setText("");
            return;
        }
        WorkingCapital w = financeService.workingCapital(farm.id(), harvestPicker.getValue());
        workingCapitalLabel.setText(Money.format(w.totalCents()));
        workingCapitalLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: "
                + (w.totalCents() < 0 ? "#B00020;" : "#2C5F2D;"));
        workingCapitalDetail.setText((w.cashAsOf() == null ? "No cash entered" : "Cash " + Money.format(w.cashCents())
                + " as of " + w.cashAsOf()) + "\nminus unpaid bills " + Money.format(w.billsCents())
                + "\nminus loan payments " + Money.format(w.loanPaymentsCents()));
    }

    // ---------- helpers ----------

    private static LocalDate defaultHarvestDate() {
        LocalDate today = LocalDate.now();
        LocalDate harvest = LocalDate.of(today.getYear(), 10, 15);
        return today.isAfter(harvest) ? harvest.plusYears(1) : harvest;
    }

    private static String money(Long cents) {
        return cents == null ? "" : Money.format(cents);
    }

    // Typed years only count once Enter is pressed or the box loses focus
    private void commitYear() {
        String text = yearSpinner.getEditor().getText();
        try {
            int year = Integer.parseInt(text.trim());
            if (year >= MIN_YEAR && year <= MAX_YEAR) {
                yearSpinner.getValueFactory().setValue(year);
                return;
            }
        } catch (NumberFormatException ignored) {
            // falls through to reset
        }
        yearSpinner.getEditor().setText(String.valueOf(yearSpinner.getValue()));
    }

    private static <T> void text(TableColumn<T, String> col, Function<T, String> getter) {
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(getter.apply(c.getValue())));
    }
}
