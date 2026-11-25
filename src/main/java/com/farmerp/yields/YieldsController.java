package com.farmerp.yields;

import com.farmerp.app.Selection;
import com.farmerp.common.DataAccessException;
import com.farmerp.common.Money;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import com.farmerp.fields.Farm;
import com.farmerp.fields.Field;
import com.farmerp.fields.FieldService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;

public class YieldsController {

    private static final DecimalFormat NUM = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PER_ACRE = new DecimalFormat("#,##0.0");
    private static final List<String> COMMON_CROPS = List.of("Corn", "Soybeans", "Wheat", "Oats", "Hay");

    private final YieldService yieldService;
    private final FieldService fields;
    private final Selection selection;
    private long seenVersion;

    @FXML private ComboBox<Farm> farmCombo;
    @FXML private ComboBox<Field> fieldCombo;
    @FXML private VBox yieldPane;

    @FXML private TableView<YieldRecord> yieldTable;
    @FXML private TableColumn<YieldRecord, String> yearCol;
    @FXML private TableColumn<YieldRecord, String> cropCol;
    @FXML private TableColumn<YieldRecord, String> harvestCol;
    @FXML private TableColumn<YieldRecord, String> totalCol;
    @FXML private TableColumn<YieldRecord, String> perAcreCol;
    @FXML private TableColumn<YieldRecord, String> priceCol;
    @FXML private TableColumn<YieldRecord, String> revenueCol;

    @FXML private Spinner<Integer> yearSpinner;
    @FXML private ComboBox<String> cropCombo;
    @FXML private DatePicker harvestPicker;
    @FXML private TextField totalField;
    @FXML private ComboBox<YieldUnit> unitCombo;
    @FXML private TextField priceField;
    @FXML private Button deleteButton;

    @FXML private ComboBox<String> expectedCropCombo;
    @FXML private Spinner<Integer> expectedYearSpinner;
    @FXML private Label expectedLabel;

    // 0 means the form is for a new yield
    private long editingYieldId;

    public YieldsController(YieldService yieldService, FieldService fields, Selection selection) {
        this.yieldService = yieldService;
        this.fields = fields;
        this.selection = selection;
    }

    @FXML
    private void initialize() {
        farmCombo.setConverter(converter(Farm::name));
        fieldCombo.setConverter(converter(Field::name));

        int thisYear = LocalDate.now().getYear();
        setUpYearSpinner(yearSpinner, thisYear);
        setUpYearSpinner(expectedYearSpinner, thisYear + 1);

        text(yearCol, y -> String.valueOf(y.cropYear()));
        text(cropCol, YieldRecord::crop);
        text(harvestCol, y -> y.harvestDate().toString());
        text(totalCol, y -> NUM.format(y.totalYield()) + " " + y.unit());
        text(perAcreCol, y -> perAcre(y));
        text(priceCol, y -> Money.format(y.priceCentsPerUnit()));
        text(revenueCol, y -> Money.format(YieldService.revenueCents(y.totalYield(), y.priceCentsPerUnit())));
        priceCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        revenueCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        unitCombo.getItems().setAll(YieldUnit.values());

        yieldPane.disableProperty().bind(fieldCombo.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(yieldTable.getSelectionModel().selectedItemProperty().isNull());

        farmCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, farm) -> loadFieldChoices(farm));
        fieldCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, field) -> loadYields());
        yieldTable.getSelectionModel().selectedItemProperty().addListener((obs, old, y) -> showYield(y));
        expectedCropCombo.valueProperty().addListener((obs, old, crop) -> showExpected());
        expectedYearSpinner.valueProperty().addListener((obs, old, year) -> showExpected());

        refresh();
    }

    // Called when the Yields tab is opened so new farms and fields show up
    public void refresh() {
        long farmId = farmCombo.getValue() == null ? 0 : farmCombo.getValue().id();
        long fieldId = fieldCombo.getValue() == null ? 0 : fieldCombo.getValue().id();

        // Follow the Fields tab only when its pick has changed since this tab last looked
        if (selection.version() != seenVersion) {
            seenVersion = selection.version();
            farmId = selection.farmId();
            fieldId = selection.fieldId();
        }

        long pickFarm = farmId;
        long pickField = fieldId;
        farmCombo.getItems().setAll(fields.listFarms());
        Farm farmMatch = farmCombo.getItems().stream()
                .filter(f -> f.id() == pickFarm).findFirst().orElse(null);
        farmCombo.setValue(farmMatch);
        loadFieldChoices(farmMatch);

        fieldCombo.getItems().stream().filter(f -> f.id() == pickField).findFirst()
                .ifPresent(fieldCombo::setValue);
        loadYields();
    }

    private void loadFieldChoices(Farm farm) {
        fieldCombo.getItems().setAll(farm == null ? List.of() : fields.listFields(farm.id()));
        fieldCombo.setValue(null);
    }

    private void loadYields() {
        Field field = fieldCombo.getValue();
        String expectedCrop = expectedCropCombo.getValue();
        if (field == null) {
            yieldTable.getItems().clear();
            cropCombo.getItems().setAll(COMMON_CROPS);
            expectedCropCombo.getItems().clear();
        } else {
            List<YieldRecord> list = yieldService.yieldsFor(field.id());
            yieldTable.getItems().setAll(list);

            // Crops already on this field, then common crops
            TreeSet<String> recorded = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            list.forEach(y -> recorded.add(y.crop()));
            TreeSet<String> choices = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            choices.addAll(recorded);
            choices.addAll(COMMON_CROPS);
            cropCombo.getItems().setAll(choices);
            expectedCropCombo.getItems().setAll(recorded);
            if (expectedCrop != null && recorded.contains(expectedCrop)) {
                expectedCropCombo.setValue(expectedCrop);
            } else {
                expectedCropCombo.setValue(recorded.isEmpty() ? null : recorded.first());
            }
        }
        clearForm();
        showExpected();
    }

    @FXML
    private void onNew() {
        yieldTable.getSelectionModel().clearSelection();
        clearForm();
    }

    @FXML
    private void onSave() {
        Field field = fieldCombo.getValue();
        if (field == null) {
            showError("Pick a field first.");
            return;
        }
        try {
            double total = Validator.parseNumber(totalField.getText(), "Total yield");
            long price = Money.parseCents(priceField.getText(), "Price per unit");
            YieldRecord saved = yieldService.saveYield(new YieldRecord(editingYieldId, field.id(),
                    yearSpinner.getValue(), cropCombo.getEditor().getText(), harvestPicker.getValue(),
                    total, unitCombo.getValue(), price));
            loadYields();
            yieldTable.getItems().stream().filter(y -> y.id() == saved.id()).findFirst()
                    .ifPresent(y -> yieldTable.getSelectionModel().select(y));
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDelete() {
        YieldRecord y = yieldTable.getSelectionModel().getSelectedItem();
        if (y != null && confirm("Delete the " + y.cropYear() + " " + y.crop() + " yield?")) {
            try {
                yieldService.deleteYield(y.id());
                loadYields();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showYield(YieldRecord y) {
        if (y == null) {
            return;
        }
        editingYieldId = y.id();
        yearSpinner.getValueFactory().setValue(y.cropYear());
        cropCombo.getEditor().setText(y.crop());
        harvestPicker.setValue(y.harvestDate());
        totalField.setText(NUM.format(y.totalYield()));
        unitCombo.setValue(y.unit());
        priceField.setText(BigDecimal.valueOf(y.priceCentsPerUnit(), 2).toPlainString());
    }

    private void clearForm() {
        editingYieldId = 0;
        yearSpinner.getValueFactory().setValue(LocalDate.now().getYear());
        cropCombo.getEditor().clear();
        harvestPicker.setValue(LocalDate.now());
        totalField.clear();
        unitCombo.setValue(YieldUnit.BUSHELS);
        priceField.clear();
    }

    // FR-9
    private void showExpected() {
        Field field = fieldCombo.getValue();
        String crop = expectedCropCombo.getValue();
        int year = expectedYearSpinner.getValue();
        if (field == null) {
            expectedLabel.setText("");
            return;
        }
        if (crop == null) {
            expectedLabel.setText("Enter a yield first. Expected yield is based on this field's past yields.");
            return;
        }
        Optional<ExpectedYield> expected = yieldService.expectedYieldPerAcre(field.id(), crop, year);
        if (expected.isEmpty()) {
            expectedLabel.setText("No " + crop + " yields on " + field.name() + " before " + year
                    + ", so there's no expected yield yet.");
        } else {
            ExpectedYield e = expected.get();
            expectedLabel.setText(PER_ACRE.format(e.perAcre()) + " " + e.unit().perAcreLabel()
                    + " (average of " + e.years() + (e.years() == 1 ? " prior year)" : " prior years)"));
        }
    }

    private String perAcre(YieldRecord y) {
        Field field = fieldCombo.getValue();
        return field == null ? "" : PER_ACRE.format(y.totalYield() / field.acres()) + " " + y.unit().perAcreLabel();
    }

    // ---------- helpers ----------

    private void setUpYearSpinner(Spinner<Integer> spinner, int start) {
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                YieldService.MIN_YEAR, YieldService.MAX_YEAR, start));
        spinner.getEditor().setOnAction(e -> commitYear(spinner));
        spinner.getEditor().focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commitYear(spinner);
            }
        });
    }

    // Typed years only count once Enter is pressed or the box loses focus
    private static void commitYear(Spinner<Integer> spinner) {
        String text = spinner.getEditor().getText();
        try {
            int year = Integer.parseInt(text.trim());
            if (year >= YieldService.MIN_YEAR && year <= YieldService.MAX_YEAR) {
                spinner.getValueFactory().setValue(year);
                return;
            }
        } catch (NumberFormatException ignored) {
            // falls through to reset
        }
        spinner.getEditor().setText(String.valueOf(spinner.getValue()));
    }

    private static <T> void text(TableColumn<T, String> col, Function<T, String> getter) {
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(getter.apply(c.getValue())));
    }

    private static <T> StringConverter<T> converter(Function<T, String> label) {
        return new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : label.apply(item);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
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
