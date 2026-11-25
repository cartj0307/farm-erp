# Farm ERP

Offline desktop farm management app for small and medium farms. It keeps each field's soil samples, water and nutrient applications, input costs, and yields in one place, and works out cost per acre, revenue, net return per acre, expected yield, and working capital through harvest. Built with Java 21, JavaFX, and SQLite for SE 511 Software Engineering at Grand Valley State University.

## Features

- **Fields:** farms and the fields on each farm
- **Records:** soil samples (N, P, K, pH, organic matter), water and nutrient applications, and each field's full history
- **Costs:** input costs per field and crop year, with totals
- **Yields:** yields, revenue, and expected yield per acre from the field's past years
- **Finance:** cash on hand, loans and interest, bills, and working capital through harvest
- **Summary:** cost, revenue, and net return per field and per acre, with costs by category

Everything is stored in one local file and runs with no internet connection.

## Download and run (Windows)

1. Go to the [Releases](../../releases) page.
2. Download `Farm-ERP-Windows.zip` from the latest release.
3. Right-click the zip and click **Extract All**.
4. Open the `Farm ERP` folder and double-click `Farm ERP.exe`.

Java comes bundled, so nothing else needs to be installed. If Windows shows "Windows protected your PC," click **More info**, then **Run anyway**. The app isn't code signed, which is why that warning shows up.

# Building from source

## What you need

- Java 21 or higher (check with `java -version`)
- An internet connection the first time you build, so the Maven Wrapper can download Maven and the libraries
- Maven does not need to be installed. The Maven Wrapper (`mvnw.cmd` on Windows, `mvnw` on Mac and Linux) downloads it into your user folder the first time it runs. No admin rights needed.
- VS Code with the Extension Pack for Java is optional, for editing the code

## Install

1. Download or clone the project.
2. If you downloaded a zip, extract it.
3. Open a terminal in the `farm-erp` folder, the one that has `pom.xml` in it.
   - In VS Code: File > Open Folder, pick `farm-erp`, then Terminal > New Terminal.

## Run

Windows:

    .\mvnw.cmd javafx:run

Mac or Linux:

    ./mvnw javafx:run

The first run takes a few minutes while Maven and the libraries download. After that it starts in a few seconds. A window titled Farm ERP opens with tabs for Fields, Records, Costs, Yields, Finance, and Summary.

The farm and field picked on the Fields tab are the home base. The other tabs switch to them when they change. Picking a different farm or field on another tab is a side lookup that only stays on that tab.

## Test

Windows:

    .\mvnw.cmd test

Mac or Linux:

    ./mvnw test

A passing run ends with `BUILD SUCCESS`.

## Data

The database is created on first run at `<home folder>/FarmERP/farm_erp.db`. Back it up by copying that file. To start over with an empty database, close the app and delete that file.

## Sample data

`src/main/resources/sample-data.sql` has made-up data for testing and demos: 2 farms, 6 fields, soil samples, applications, costs and yields for 2023 to 2026, cash, loans, and bills. It only loads into a database with no farms in it.

Close the app first, then run:

Windows:

    .\mvnw.cmd compile exec:java

Mac or Linux:

    ./mvnw compile exec:java

## Build the Windows .exe

Run these from the `farm-erp` folder on Windows. `jpackage` comes with the JDK.

    .\mvnw.cmd clean package dependency:copy-dependencies -DskipTests -DincludeScope=runtime -DoutputDirectory=target\libs
    copy target\farm-erp-0.1.0.jar target\libs\
    jpackage --type app-image --name "Farm ERP" --input target\libs --main-jar farm-erp-0.1.0.jar --main-class com.farmerp.app.Launcher --dest dist

The program is in `dist\Farm ERP\Farm ERP.exe`. Zip the `Farm ERP` folder to share it.

## Build progress

| Increment | Module | Status |
| --- | --- | --- |
| 1 | Farms and fields | Done |
| 2 | Soil samples, applications, field history | Done |
| 3 | Input costs | Done |
| 4 | Yields and expected yield | Done |
| 5 | Cash, loans, bills, working capital | Done |
| 6 | Financial summaries | Done |

## Project layout

    farm-erp/
    ├── pom.xml                              Maven build: Java 21, JavaFX, SQLite driver, JUnit
    ├── mvnw, mvnw.cmd, .mvn/                Maven Wrapper
    ├── .gitignore, .gitattributes
    ├── README.md
    └── src/
        ├── main/
        │   ├── java/com/farmerp/
        │   │   ├── app/
        │   │   │   ├── MainApp.java          Main window and tabs
        │   │   │   ├── Launcher.java         Starts MainApp from the VS Code Run button
        │   │   │   ├── Database.java         SQLite connection and schema setup
        │   │   │   ├── Selection.java        Home base farm and field picked on the Fields tab
        │   │   │   └── SampleData.java       Loads sample-data.sql into an empty database
        │   │   ├── common/
        │   │   │   ├── Dao.java              Interface every DAO implements
        │   │   │   ├── Money.java            Dollars and cents parsing and formatting
        │   │   │   ├── Validator.java        Input checks
        │   │   │   ├── ValidationException.java
        │   │   │   └── DataAccessException.java
        │   │   ├── fields/                   Increment 1
        │   │   │   ├── Farm.java
        │   │   │   ├── Field.java
        │   │   │   ├── FarmDao.java
        │   │   │   ├── FieldDao.java
        │   │   │   ├── FieldService.java
        │   │   │   └── FieldsController.java
        │   │   ├── records/                  Increment 2
        │   │   │   ├── SoilSample.java
        │   │   │   ├── Application.java
        │   │   │   ├── ApplicationType.java
        │   │   │   ├── HistoryEntry.java
        │   │   │   ├── SoilSampleDao.java
        │   │   │   ├── ApplicationDao.java
        │   │   │   ├── RecordService.java
        │   │   │   └── RecordsController.java
        │   │   ├── costs/                    Increment 3
        │   │   │   ├── InputCost.java
        │   │   │   ├── CostCategory.java
        │   │   │   ├── CostTotal.java
        │   │   │   ├── InputCostDao.java
        │   │   │   ├── CostService.java
        │   │   │   └── CostsController.java
        │   │   ├── yields/                   Increment 4
        │   │   │   ├── YieldRecord.java
        │   │   │   ├── YieldUnit.java
        │   │   │   ├── ExpectedYield.java
        │   │   │   ├── YieldDao.java
        │   │   │   ├── YieldService.java
        │   │   │   └── YieldsController.java
        │   │   ├── finance/                  Increment 5
        │   │   │   ├── CashEntry.java
        │   │   │   ├── Loan.java
        │   │   │   ├── Bill.java
        │   │   │   ├── WorkingCapital.java
        │   │   │   ├── CashEntryDao.java
        │   │   │   ├── LoanDao.java
        │   │   │   ├── BillDao.java
        │   │   │   ├── FinanceService.java
        │   │   │   └── FinanceController.java
        │   │   └── summary/                  Increment 6
        │   │       ├── FarmSummary.java
        │   │       ├── FieldSummary.java
        │   │       ├── SummaryService.java
        │   │       └── SummaryController.java
        │   └── resources/
        │       ├── schema.sql                Database tables
        │       ├── sample-data.sql           Made-up data for testing and demos
        │       └── com/farmerp/
        │           ├── fields/FieldsView.fxml
        │           ├── records/RecordsView.fxml
        │           ├── costs/CostsView.fxml
        │           ├── yields/YieldsView.fxml
        │           ├── finance/FinanceView.fxml
        │           └── summary/SummaryView.fxml
        └── test/java/com/farmerp/
            ├── app/DatabaseTest.java
            ├── app/SampleDataTest.java
            ├── app/SelectionTest.java
            ├── common/MoneyTest.java
            ├── common/ValidatorTest.java
            ├── fields/FieldServiceTest.java
            ├── records/RecordServiceTest.java
            ├── costs/CostServiceTest.java
            ├── yields/YieldServiceTest.java
            ├── finance/FinanceServiceTest.java
            └── summary/SummaryServiceTest.java

Each package under `com/farmerp/` also has a `package-info.java` describing the module.

## Testing

117 JUnit tests cover the services, calculations, and database, and 61 system test cases cover every use case in the running app. All of them pass.

## Author

James Carte
