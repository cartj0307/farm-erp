package com.farmerp.app;

import com.farmerp.costs.CostService;
import com.farmerp.costs.CostsController;
import com.farmerp.fields.FieldService;
import com.farmerp.fields.FieldsController;
import com.farmerp.finance.FinanceController;
import com.farmerp.finance.FinanceService;
import com.farmerp.records.RecordService;
import com.farmerp.records.RecordsController;
import com.farmerp.summary.SummaryController;
import com.farmerp.summary.SummaryService;
import com.farmerp.yields.YieldService;
import com.farmerp.yields.YieldsController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

public class MainApp extends Application {

    private static Database database;

    // Home base farm and field shared by every tab
    private final Selection selection = new Selection();

    public static Database database() {
        return database;
    }

    @Override
    public void init() {
        // Create the database file and tables on first run
        database = Database.openDefault();
        database.initSchema();
    }

    @Override
    public void start(Stage stage) {
        TabPane tabs = new TabPane(
                fieldsTab(),
                recordsTab(),
                costsTab(),
                yieldsTab(),
                financeTab(),
                summaryTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        stage.setTitle("Farm ERP");
        stage.setScene(new Scene(tabs, 1000, 650));
        stage.show();
    }

    // Increment 1
    private Tab fieldsTab() {
        FieldService service = new FieldService(database);
        Parent view = loadView("/com/farmerp/fields/FieldsView.fxml", new FieldsController(service, selection));
        return new Tab("Fields", view);
    }

    // Increment 2
    private Tab recordsTab() {
        RecordsController controller = new RecordsController(new RecordService(database), new FieldService(database), selection);
        Tab tab = new Tab("Records", loadView("/com/farmerp/records/RecordsView.fxml", controller));
        // Pick up farms and fields added on the Fields tab
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                controller.refresh();
            }
        });
        return tab;
    }

    // Increment 3
    private Tab costsTab() {
        CostsController controller = new CostsController(new CostService(database), new FieldService(database), selection);
        Tab tab = new Tab("Costs", loadView("/com/farmerp/costs/CostsView.fxml", controller));
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                controller.refresh();
            }
        });
        return tab;
    }

    // Increment 4
    private Tab yieldsTab() {
        YieldsController controller = new YieldsController(new YieldService(database), new FieldService(database), selection);
        Tab tab = new Tab("Yields", loadView("/com/farmerp/yields/YieldsView.fxml", controller));
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                controller.refresh();
            }
        });
        return tab;
    }

    // Increment 5
    private Tab financeTab() {
        FinanceController controller = new FinanceController(new FinanceService(database), new FieldService(database), selection);
        Tab tab = new Tab("Finance", loadView("/com/farmerp/finance/FinanceView.fxml", controller));
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                controller.refresh();
            }
        });
        return tab;
    }

    // Increment 6
    private Tab summaryTab() {
        SummaryController controller = new SummaryController(new SummaryService(database),
                new FinanceService(database), new FieldService(database), selection);
        Tab tab = new Tab("Summary", loadView("/com/farmerp/summary/SummaryView.fxml", controller));
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                controller.refresh();
            }
        });
        return tab;
    }

    // Loads an FXML view with a controller that already has its service
    private Parent loadView(String path, Object controller) {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(path));
        loader.setControllerFactory(type -> controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + path, e);
        }
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
