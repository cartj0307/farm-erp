package com.farmerp.costs;

import com.farmerp.app.Selection;
import com.farmerp.common.DataAccessException;
import com.farmerp.common.Money;
import com.farmerp.common.ValidationException;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class CostsController {

    private final CostService costService;
    private final FieldService fields;
    private final Selection selection;
    private long seenVersion;

    @FXML private ComboBox<Farm> farmCombo;
    @FXML private ComboBox<Field> fieldCombo;
    @FXML private Spinner<Integer> yearSpinner;
    @FXML private VBox costPane;

    @FXML private TableView<InputCost> costTable;
    @FXML private TableColumn<InputCost, String> dateCol;
    @FXML private TableColumn<InputCost, String> categoryCol;
    @FXML private TableColumn<InputCost, String> descriptionCol;
    @FXML private TableColumn<InputCost, String> amountCol;
    @FXML private Label totalLabel;

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<CostCategory> categoryCombo;
    @FXML private TextField descriptionField;
    @FXML private TextField amountField;
    @FXML private Button deleteButton;

    // 0 means the form is for a new cost
    private long editingCostId;

    public CostsController(CostService costService, FieldService fields, Selection selection) {
        this.costService = costService;
        this.fields = fields;
        this.selection = selection;
    }

    @FXML
    private void initialize() {
        farmCombo.setConverter(converter(Farm::name));
        fieldCombo.setConverter(converter(Field::name));

        yearSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                CostService.MIN_YEAR, CostService.MAX_YEAR, LocalDate.now().getYear()));
        yearSpinner.getEditor().setOnAction(e -> commitYear());
        yearSpinner.getEditor().focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commitYear();
            }
        });

        text(dateCol, c -> c.date().toString());
        text(categoryCol, c -> c.category().toString());
        text(descriptionCol, InputCost::description);
        text(amountCol, c -> Money.format(c.amountCents()));
        amountCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        categoryCombo.getItems().setAll(CostCategory.values());

        costPane.disableProperty().bind(fieldCombo.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.disableProperty().bind(costTable.getSelectionModel().selectedItemProperty().isNull());

        farmCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, farm) -> loadFieldChoices(farm));
        fieldCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, field) -> loadCosts());
        yearSpinner.valueProperty().addListener((obs, old, year) -> loadCosts());
        costTable.getSelectionModel().selectedItemProperty().addListener((obs, old, cost) -> showCost(cost));

        refresh();
    }

    // Called when the Costs tab is opened so new farms and fields show up
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
        loadCosts();
    }

    private void loadFieldChoices(Farm farm) {
        fieldCombo.getItems().setAll(farm == null ? List.of() : fields.listFields(farm.id()));
        fieldCombo.setValue(null);
    }

    private void loadCosts() {
        Field field = fieldCombo.getValue();
        int year = yearSpinner.getValue();
        if (field == null) {
            costTable.getItems().clear();
            totalLabel.setText("");
        } else {
            costTable.getItems().setAll(costService.costsFor(field.id(), year));
            totalLabel.setText("Total for " + field.name() + " in " + year + ": "
                    + Money.format(costService.totalFor(field.id(), year)));
        }
        clearForm();
    }

    @FXML
    private void onNew() {
        costTable.getSelectionModel().clearSelection();
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
            long cents = Money.parseCents(amountField.getText(), "Amount");
            InputCost saved = costService.saveCost(new InputCost(editingCostId, field.id(), yearSpinner.getValue(),
                    datePicker.getValue(), categoryCombo.getValue(), descriptionField.getText(), cents));
            loadCosts();
            costTable.getItems().stream().filter(c -> c.id() == saved.id()).findFirst()
                    .ifPresent(c -> costTable.getSelectionModel().select(c));
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDelete() {
        InputCost cost = costTable.getSelectionModel().getSelectedItem();
        if (cost != null && confirm("Delete the " + cost.category().toString().toLowerCase() + " cost of "
                + Money.format(cost.amountCents()) + " from " + cost.date() + "?")) {
            try {
                costService.deleteCost(cost.id());
                loadCosts();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showCost(InputCost cost) {
        if (cost == null) {
            return;
        }
        editingCostId = cost.id();
        datePicker.setValue(cost.date());
        categoryCombo.setValue(cost.category());
        descriptionField.setText(cost.description());
        amountField.setText(BigDecimal.valueOf(cost.amountCents(), 2).toPlainString());
    }

    private void clearForm() {
        editingCostId = 0;
        datePicker.setValue(LocalDate.now());
        categoryCombo.setValue(null);
        descriptionField.clear();
        amountField.clear();
    }

    // Typed years only count once Enter is pressed or the box loses focus
    private void commitYear() {
        String text = yearSpinner.getEditor().getText();
        try {
            int year = Integer.parseInt(text.trim());
            if (year >= CostService.MIN_YEAR && year <= CostService.MAX_YEAR) {
                yearSpinner.getValueFactory().setValue(year);
                return;
            }
        } catch (NumberFormatException ignored) {
            // falls through to reset
        }
        yearSpinner.getEditor().setText(String.valueOf(yearSpinner.getValue()));
    }

    // ---------- helpers ----------

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
