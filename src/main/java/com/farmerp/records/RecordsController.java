package com.farmerp.records;

import com.farmerp.app.Selection;
import com.farmerp.common.DataAccessException;
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
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class RecordsController {

    private static final DecimalFormat NUM = new DecimalFormat("#,##0.##");

    private final RecordService records;
    private final FieldService fields;
    private final Selection selection;
    private long seenVersion;

    @FXML private ComboBox<Farm> farmCombo;
    @FXML private ComboBox<Field> fieldCombo;
    @FXML private TabPane recordTabs;

    @FXML private TableView<SoilSample> sampleTable;
    @FXML private TableColumn<SoilSample, String> sampleDateCol;
    @FXML private TableColumn<SoilSample, String> nitrogenCol;
    @FXML private TableColumn<SoilSample, String> phosphorusCol;
    @FXML private TableColumn<SoilSample, String> potassiumCol;
    @FXML private TableColumn<SoilSample, String> phCol;
    @FXML private TableColumn<SoilSample, String> organicMatterCol;
    @FXML private DatePicker sampleDatePicker;
    @FXML private TextField nitrogenField;
    @FXML private TextField phosphorusField;
    @FXML private TextField potassiumField;
    @FXML private TextField phField;
    @FXML private TextField organicMatterField;
    @FXML private Button deleteSampleButton;

    @FXML private TableView<Application> appTable;
    @FXML private TableColumn<Application, String> appDateCol;
    @FXML private TableColumn<Application, String> appTypeCol;
    @FXML private TableColumn<Application, String> appProductCol;
    @FXML private TableColumn<Application, String> appAmountCol;
    @FXML private TableColumn<Application, String> appUnitCol;
    @FXML private DatePicker appDatePicker;
    @FXML private ComboBox<ApplicationType> appTypeCombo;
    @FXML private TextField productField;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> unitCombo;
    @FXML private Button deleteAppButton;

    @FXML private TableView<HistoryEntry> historyTable;
    @FXML private TableColumn<HistoryEntry, String> historyDateCol;
    @FXML private TableColumn<HistoryEntry, String> historyKindCol;
    @FXML private TableColumn<HistoryEntry, String> historyDetailsCol;

    // 0 means the form is for a new record
    private long editingSampleId;
    private long editingAppId;

    public RecordsController(RecordService records, FieldService fields, Selection selection) {
        this.records = records;
        this.fields = fields;
        this.selection = selection;
    }

    @FXML
    private void initialize() {
        farmCombo.setConverter(converter(Farm::name));
        fieldCombo.setConverter(converter(Field::name));

        text(sampleDateCol, s -> s.sampleDate().toString());
        text(nitrogenCol, s -> num(s.nitrogenPpm()));
        text(phosphorusCol, s -> num(s.phosphorusPpm()));
        text(potassiumCol, s -> num(s.potassiumPpm()));
        text(phCol, s -> num(s.ph()));
        text(organicMatterCol, s -> num(s.organicMatterPct()));

        text(appDateCol, a -> a.date().toString());
        text(appTypeCol, a -> a.type().toString());
        text(appProductCol, Application::product);
        text(appAmountCol, a -> NUM.format(a.amount()));
        text(appUnitCol, Application::unit);

        text(historyDateCol, h -> h.date().toString());
        text(historyKindCol, HistoryEntry::kind);
        text(historyDetailsCol, HistoryEntry::details);

        appTypeCombo.getItems().setAll(ApplicationType.values());
        unitCombo.getItems().setAll("gallons", "acre-inches", "lb", "tons", "oz", "pints", "quarts");

        recordTabs.disableProperty().bind(fieldCombo.getSelectionModel().selectedItemProperty().isNull());
        deleteSampleButton.disableProperty().bind(sampleTable.getSelectionModel().selectedItemProperty().isNull());
        deleteAppButton.disableProperty().bind(appTable.getSelectionModel().selectedItemProperty().isNull());

        farmCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, farm) -> loadFieldChoices(farm));
        fieldCombo.getSelectionModel().selectedItemProperty().addListener((obs, old, field) -> loadRecords());
        sampleTable.getSelectionModel().selectedItemProperty().addListener((obs, old, s) -> showSample(s));
        appTable.getSelectionModel().selectedItemProperty().addListener((obs, old, a) -> showApp(a));

        refresh();
    }

    // Called when the Records tab is opened so new farms and fields show up
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
        loadRecords();
    }

    private void loadFieldChoices(Farm farm) {
        fieldCombo.getItems().setAll(farm == null ? List.of() : fields.listFields(farm.id()));
        fieldCombo.setValue(null);
    }

    private void loadRecords() {
        Field field = fieldCombo.getValue();
        if (field == null) {
            sampleTable.getItems().clear();
            appTable.getItems().clear();
            historyTable.getItems().clear();
        } else {
            sampleTable.getItems().setAll(records.listSoilSamples(field.id()));
            appTable.getItems().setAll(records.listApplications(field.id()));
            historyTable.getItems().setAll(records.fieldHistory(field.id()));
        }
        clearSampleForm();
        clearAppForm();
    }

    // ---------- soil samples ----------

    @FXML
    private void onNewSample() {
        sampleTable.getSelectionModel().clearSelection();
        clearSampleForm();
    }

    @FXML
    private void onSaveSample() {
        Field field = fieldCombo.getValue();
        if (field == null) {
            showError("Pick a field first.");
            return;
        }
        try {
            SoilSample saved = records.saveSoilSample(new SoilSample(editingSampleId, field.id(),
                    sampleDatePicker.getValue(),
                    optionalNumber(nitrogenField, "Nitrogen"),
                    optionalNumber(phosphorusField, "Phosphorus"),
                    optionalNumber(potassiumField, "Potassium"),
                    optionalNumber(phField, "pH"),
                    optionalNumber(organicMatterField, "Organic matter")));
            loadRecords();
            sampleTable.getItems().stream().filter(s -> s.id() == saved.id()).findFirst()
                    .ifPresent(s -> sampleTable.getSelectionModel().select(s));
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteSample() {
        SoilSample s = sampleTable.getSelectionModel().getSelectedItem();
        if (s != null && confirm("Delete the soil sample from " + s.sampleDate() + "?")) {
            try {
                records.deleteSoilSample(s.id());
                loadRecords();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showSample(SoilSample s) {
        if (s == null) {
            return;
        }
        editingSampleId = s.id();
        sampleDatePicker.setValue(s.sampleDate());
        nitrogenField.setText(num(s.nitrogenPpm()));
        phosphorusField.setText(num(s.phosphorusPpm()));
        potassiumField.setText(num(s.potassiumPpm()));
        phField.setText(num(s.ph()));
        organicMatterField.setText(num(s.organicMatterPct()));
    }

    private void clearSampleForm() {
        editingSampleId = 0;
        sampleDatePicker.setValue(LocalDate.now());
        nitrogenField.clear();
        phosphorusField.clear();
        potassiumField.clear();
        phField.clear();
        organicMatterField.clear();
    }

    // ---------- applications ----------

    @FXML
    private void onNewApp() {
        appTable.getSelectionModel().clearSelection();
        clearAppForm();
    }

    @FXML
    private void onSaveApp() {
        Field field = fieldCombo.getValue();
        if (field == null) {
            showError("Pick a field first.");
            return;
        }
        try {
            double amount = Validator.parseNumber(amountField.getText(), "Amount");
            Application saved = records.saveApplication(new Application(editingAppId, field.id(),
                    appDatePicker.getValue(), appTypeCombo.getValue(), productField.getText(),
                    amount, unitCombo.getEditor().getText()));
            loadRecords();
            appTable.getItems().stream().filter(a -> a.id() == saved.id()).findFirst()
                    .ifPresent(a -> appTable.getSelectionModel().select(a));
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteApp() {
        Application a = appTable.getSelectionModel().getSelectedItem();
        if (a != null && confirm("Delete the " + a.type().toString().toLowerCase() + " application from " + a.date() + "?")) {
            try {
                records.deleteApplication(a.id());
                loadRecords();
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void showApp(Application a) {
        if (a == null) {
            return;
        }
        editingAppId = a.id();
        appDatePicker.setValue(a.date());
        appTypeCombo.setValue(a.type());
        productField.setText(a.product());
        amountField.setText(NUM.format(a.amount()));
        unitCombo.getEditor().setText(a.unit());
    }

    private void clearAppForm() {
        editingAppId = 0;
        appDatePicker.setValue(LocalDate.now());
        appTypeCombo.setValue(null);
        productField.clear();
        amountField.clear();
        unitCombo.getEditor().clear();
    }

    // ---------- helpers ----------

    // Blank box = not reported
    private static Double optionalNumber(TextField box, String name) {
        String text = box.getText();
        return text == null || text.isBlank() ? null : Validator.parseNumber(text, name);
    }

    private static String num(Double value) {
        return value == null ? "" : NUM.format(value);
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
