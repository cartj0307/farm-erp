package com.farmerp.fields;

import com.farmerp.app.Selection;
import com.farmerp.common.DataAccessException;
import com.farmerp.common.ValidationException;
import com.farmerp.common.Validator;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.text.DecimalFormat;
import java.util.Optional;

public class FieldsController {

    private static final DecimalFormat ACRES = new DecimalFormat("#,##0.##");

    private final FieldService service;
    private final Selection selection;

    @FXML private TableView<Farm> farmTable;
    @FXML private TableColumn<Farm, String> farmNameCol;
    @FXML private TableColumn<Farm, String> farmLocationCol;
    @FXML private TableColumn<Farm, String> farmAcresCol;
    @FXML private TextField farmNameField;
    @FXML private TextField farmLocationField;
    @FXML private TextField farmAcresField;
    @FXML private Button deleteFarmButton;

    @FXML private VBox fieldPane;
    @FXML private Label fieldsHeader;
    @FXML private TableView<Field> fieldTable;
    @FXML private TableColumn<Field, String> fieldNameCol;
    @FXML private TableColumn<Field, String> fieldAcresCol;
    @FXML private TableColumn<Field, String> fieldNotesCol;
    @FXML private TextField fieldNameField;
    @FXML private TextField fieldAcresField;
    @FXML private TextArea fieldNotesArea;
    @FXML private Button deleteFieldButton;

    // 0 means the form is for a new record
    private long editingFarmId;
    private long editingFieldId;

    public FieldsController(FieldService service, Selection selection) {
        this.service = service;
        this.selection = selection;
    }

    @FXML
    private void initialize() {
        farmNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        farmLocationCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().location()));
        farmAcresCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(ACRES.format(c.getValue().totalAcres())));
        fieldNameCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        fieldAcresCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(ACRES.format(c.getValue().acres())));
        fieldNotesCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().notes()));

        deleteFarmButton.disableProperty().bind(farmTable.getSelectionModel().selectedItemProperty().isNull());
        deleteFieldButton.disableProperty().bind(fieldTable.getSelectionModel().selectedItemProperty().isNull());
        fieldPane.disableProperty().bind(farmTable.getSelectionModel().selectedItemProperty().isNull());

        farmTable.getSelectionModel().selectedItemProperty().addListener((obs, old, farm) -> {
            // Sets the home base the other tabs follow
            if (farm != null) {
                selection.selectFarm(farm.id());
            }
            showFarm(farm);
        });
        fieldTable.getSelectionModel().selectedItemProperty().addListener((obs, old, field) -> {
            if (field != null) {
                selection.selectField(field.farmId(), field.id());
            }
            showField(field);
        });

        loadFarms(0);
        showFarm(null);
    }

    // ---------- farms ----------

    @FXML
    private void onNewFarm() {
        farmTable.getSelectionModel().clearSelection();
        clearFarmForm();
        farmNameField.requestFocus();
    }

    @FXML
    private void onSaveFarm() {
        try {
            double acres = Validator.parseNumber(farmAcresField.getText(), "Total acres");
            Farm saved = service.saveFarm(new Farm(editingFarmId, farmNameField.getText(),
                    farmLocationField.getText(), acres));
            loadFarms(saved.id());
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteFarm() {
        Farm farm = farmTable.getSelectionModel().getSelectedItem();
        if (farm == null) {
            return;
        }
        int fieldCount = service.listFields(farm.id()).size();
        String message = "Delete " + farm.name() + "?"
                + (fieldCount > 0 ? " This also deletes its " + fieldCount + " field(s) and their records." : "");
        if (confirm(message)) {
            try {
                service.deleteFarm(farm.id());
                selection.forgetFarm(farm.id());
                loadFarms(0);
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void loadFarms(long selectId) {
        farmTable.getItems().setAll(service.listFarms());
        if (selectId == 0) {
            farmTable.getSelectionModel().clearSelection();
            clearFarmForm();
            return;
        }
        farmTable.getItems().stream()
                .filter(f -> f.id() == selectId)
                .findFirst()
                .ifPresent(f -> farmTable.getSelectionModel().select(f));
    }

    private void showFarm(Farm farm) {
        if (farm == null) {
            fieldsHeader.setText("Fields");
            fieldTable.getItems().clear();
            clearFieldForm();
            return;
        }
        editingFarmId = farm.id();
        farmNameField.setText(farm.name());
        farmLocationField.setText(farm.location());
        farmAcresField.setText(ACRES.format(farm.totalAcres()));
        fieldsHeader.setText("Fields on " + farm.name());
        loadFields(0);
    }

    private void clearFarmForm() {
        editingFarmId = 0;
        farmNameField.clear();
        farmLocationField.clear();
        farmAcresField.clear();
    }

    // ---------- fields ----------

    @FXML
    private void onNewField() {
        fieldTable.getSelectionModel().clearSelection();
        clearFieldForm();
        fieldNameField.requestFocus();
    }

    @FXML
    private void onSaveField() {
        Farm farm = farmTable.getSelectionModel().getSelectedItem();
        if (farm == null) {
            showError("Pick a farm before adding a field.");
            return;
        }
        try {
            double acres = Validator.parseNumber(fieldAcresField.getText(), "Acres");
            Field saved = service.saveField(new Field(editingFieldId, farm.id(), fieldNameField.getText(),
                    acres, fieldNotesArea.getText()));
            loadFields(saved.id());
        } catch (ValidationException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            showError(e.getMessage() + ".");
        }
    }

    @FXML
    private void onDeleteField() {
        Field field = fieldTable.getSelectionModel().getSelectedItem();
        if (field == null) {
            return;
        }
        if (confirm("Delete " + field.name() + "? This also deletes its records.")) {
            try {
                service.deleteField(field.id());
                selection.forgetField(field.id());
                loadFields(0);
            } catch (DataAccessException e) {
                showError(e.getMessage() + ".");
            }
        }
    }

    private void loadFields(long selectId) {
        Farm farm = farmTable.getSelectionModel().getSelectedItem();
        if (farm == null) {
            fieldTable.getItems().clear();
            clearFieldForm();
            return;
        }
        fieldTable.getItems().setAll(service.listFields(farm.id()));
        if (selectId == 0) {
            fieldTable.getSelectionModel().clearSelection();
            clearFieldForm();
            return;
        }
        fieldTable.getItems().stream()
                .filter(f -> f.id() == selectId)
                .findFirst()
                .ifPresent(f -> fieldTable.getSelectionModel().select(f));
    }

    private void showField(Field field) {
        if (field == null) {
            return;
        }
        editingFieldId = field.id();
        fieldNameField.setText(field.name());
        fieldAcresField.setText(ACRES.format(field.acres()));
        fieldNotesArea.setText(field.notes());
    }

    private void clearFieldForm() {
        editingFieldId = 0;
        fieldNameField.clear();
        fieldAcresField.clear();
        fieldNotesArea.clear();
    }

    // ---------- dialogs ----------

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
