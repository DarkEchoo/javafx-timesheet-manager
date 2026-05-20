package com.jesse.timesheet;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainApp extends Application {

    private ListView<String> entryList = new ListView<>();
    private ComboBox<String> employeeBox = new ComboBox<>();
    private ComboBox<String> jobSiteBox = new ComboBox<>();

    private boolean hasUnsavedChanges = false;
    private String lastEmployeeName = "";

    private final File appFolder = new File(
            System.getProperty("user.home") + File.separator + "Documents",
            "Timesheets"
    );

    private final File employeesFile = new File(appFolder, "employees.txt");
    private final File jobSitesFile = new File(appFolder, "job_sites.txt");
    private final File settingsFile = new File(appFolder, "settings.txt");
    
    private List<String> allEntries = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        appFolder.mkdirs();

        loadEntries();
        loadEmployees();
        loadSettings();

        employeeBox.setEditable(true);
        employeeBox.setPromptText("Employee name");

        if (!lastEmployeeName.isEmpty()) {
            employeeBox.setValue(lastEmployeeName);
        }

        loadJobSites();

        jobSiteBox.setEditable(true);
        jobSiteBox.setPromptText("Job site");
        
        ComboBox<Integer> lunchBox = new ComboBox<>();

        lunchBox.getItems().addAll(
                0,
                15,
                30,
                45,
                60
        );

        lunchBox.setValue(30);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        
        ComboBox<String> nameFilterField = new ComboBox<>();
        nameFilterField.setEditable(true);
        nameFilterField.setPromptText("Filter by employee");

        ComboBox<String> jobFilterField = new ComboBox<>();
        jobFilterField.setEditable(true);
        jobFilterField.setPromptText("Filter by job site");

        nameFilterField.setItems(employeeBox.getItems());
        jobFilterField.setItems(jobSiteBox.getItems());

        DatePicker weekFilterPicker = new DatePicker();
        weekFilterPicker.setPromptText("Filter by week");

        Button clearFiltersButton = new Button("Clear Filters");

        HBox filterRow = new HBox(
                10,
                new Label("Filters:"),
                nameFilterField,
                jobFilterField,
                weekFilterPicker,
                clearFiltersButton
        );

        AmPmTimePicker clockInPicker = new AmPmTimePicker("AM");
        AmPmTimePicker clockOutPicker = new AmPmTimePicker("PM");

        HBox clockInRow = new HBox(10, new Label("Clock In"), clockInPicker);
        HBox clockOutRow = new HBox(10, new Label("Clock Out"), clockOutPicker);

        Button addButton = new Button("Add Entry");
        Button removeButton = new Button("Remove Entry");
        Button saveButton = new Button("Save Entries");
        Button exportButton = new Button("Export As");
        Button emailButton = new Button("Send Email");

        emailButton.setDisable(false);

        ToolBar toolBar = new ToolBar(
                addButton,
                removeButton,

                new Separator(),

                saveButton,
                exportButton,

                new Separator(),

                emailButton
        );

        addButton.setOnAction(e -> {
            try {
                String employee = employeeBox.getEditor().getText().trim();
                String jobSite = jobSiteBox.getEditor().getText().trim();
                
                if (employee.isEmpty()) {
                    throw new IllegalArgumentException("Employee name is required.");
                }

                if (jobSite.isEmpty()) {
                    throw new IllegalArgumentException("Job site is required.");
                }

                LocalDate date = datePicker.getValue();
                LocalTime clockIn = clockInPicker.getTime();
                LocalTime clockOut = clockOutPicker.getTime();

                if (!clockOut.isAfter(clockIn)) {
                    throw new IllegalArgumentException("Clock out time must be after clock in time.");
                }

                TimesheetEntry entry = new TimesheetEntry(
                        employee,
                        jobSite,
                        date,
                        clockIn,
                        clockOut,
                        lunchBox.getValue()
                );

                double totalHours = entry.getHoursWorked();

                if (totalHours < 0) {
                    throw new IllegalArgumentException("Lunch cannot be longer than the shift.");
                }

                String entryText =
                        entry.getEmployeeName() + " | " +
                        entry.getJobSite() + " | " +
                        entry.getDate() + " | " +
                        clockInPicker.getFormattedTime() + " - " +
                        clockOutPicker.getFormattedTime() + " | " +
                        "Lunch: " + lunchBox.getValue() + " min | " +
                        String.format("%.2f", totalHours) +
                        " hours";
                allEntries.add(entryText);
                entryList.getItems().setAll(allEntries);

                saveEmployeeName(employee);
                saveJobSite(jobSite);

                lastEmployeeName = employee;

                hasUnsavedChanges = true;

                jobSiteBox.setValue("");
                clockInPicker.clear();
                clockOutPicker.clear();

                employeeBox.setValue(lastEmployeeName);

            } catch (IllegalArgumentException ex) {
                showError("Invalid Entry", "Please check your timesheet entry.", ex.getMessage());
            }
        });
        
        removeButton.setOnAction(e -> {
            String selectedEntry = entryList.getSelectionModel().getSelectedItem();

            if (selectedEntry == null) {
                showError(
                        "No Entry Selected",
                        "Please select a timesheet entry.",
                        "Click an entry in the list before trying to remove it."
                );
                return;
            }

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Remove Entry");
            alert.setHeaderText("Remove selected timesheet entry?");
            alert.setContentText("This will remove the entry from the current list.");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    allEntries.remove(selectedEntry);
                    entryList.getItems().remove(selectedEntry);
                    hasUnsavedChanges = true;
                }
            });
        });

        saveButton.setOnAction(e -> saveAllData());
        exportButton.setOnAction(e -> exportEntries(stage));
        emailButton.setOnAction(e -> emailExcelFile());
        
        nameFilterField.getEditor().textProperty().addListener((obs, oldValue, newValue) ->
        	applyFilters(
                nameFilterField.getEditor().getText(),
                jobFilterField.getEditor().getText(),
                weekFilterPicker.getValue()
        		)
        );

        jobFilterField.getEditor().textProperty().addListener((obs, oldValue, newValue) ->
        	applyFilters(
                nameFilterField.getEditor().getText(),
                jobFilterField.getEditor().getText(),
                weekFilterPicker.getValue()
        		)
        );

        weekFilterPicker.valueProperty().addListener((obs, oldValue, newValue) ->
        	applyFilters(
                nameFilterField.getEditor().getText(),
                jobFilterField.getEditor().getText(),
                weekFilterPicker.getValue()
        		)
        );

        clearFiltersButton.setOnAction(e -> {
        	nameFilterField.getEditor().clear();
        	jobFilterField.getEditor().clear();
        	weekFilterPicker.setValue(null);

        	entryList.getItems().setAll(allEntries);
        });

        stage.setOnCloseRequest(e -> {
            if (hasUnsavedChanges) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("You have unsaved timesheet entries.");
                alert.setContentText("Do you want to save before exiting?");

                ButtonType saveType = new ButtonType("Save");
                ButtonType exitType = new ButtonType("Exit Without Saving");
                ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(saveType, exitType, cancelType);

                alert.showAndWait().ifPresent(response -> {
                    if (response == saveType) {
                        e.consume();
                        saveAllData();

                        if (!hasUnsavedChanges) {
                            stage.close();
                        }

                    } else if (response == cancelType) {
                        e.consume();
                    }
                });
            }
        });

        VBox form = new VBox(10);
        form.setStyle("-fx-padding: 20;");

        form.getChildren().addAll(
                new Label("Timesheet Entry App"),
                new Label("Employee Name"),
                employeeBox,
                new Label("Job Site"),
                jobSiteBox,
                new Label("Date"),
                datePicker,
                clockInRow,
                clockOutRow,
                new Label("Lunch Deduction"),
                lunchBox,
                addButton,
                new Label("Timesheet Entries"),
                filterRow,
                entryList
        );

        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        root.setCenter(form);

        Scene scene = new Scene(root, 850, 650);

        stage.setTitle("Timesheet App");
        stage.setScene(scene);
        stage.show();
    }

    private File getWeeklyEntriesFile(LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        File yearFolder = new File(appFolder, String.valueOf(weekStart.getYear()));
        yearFolder.mkdirs();

        return new File(yearFolder, "Week_" + weekStart + "_to_" + weekEnd + ".txt");
    }

    private void saveAllData() {
        saveEntries();
        saveEmployees();
        saveJobSites();
        saveSettings();

        hasUnsavedChanges = false;

        showInfo("Saved", "Timesheet data saved successfully.");
    }

    private void saveEntries() {
        try {
            File[] yearFolders = appFolder.listFiles(File::isDirectory);

            if (yearFolders != null) {
                for (File yearFolder : yearFolders) {
                    File[] weeklyFiles = yearFolder.listFiles(
                            (dir, name) -> name.startsWith("Week_") && name.endsWith(".txt")
                    );

                    if (weeklyFiles != null) {
                        for (File weeklyFile : weeklyFiles) {
                            weeklyFile.delete();
                        }
                    }
                }
            }

            for (String entry : allEntries) {
                String[] parts = entry.split("\\|");

                if (parts.length < 3) {
                    continue;
                }

                LocalDate date = LocalDate.parse(parts[2].trim());
                File weeklyFile = getWeeklyEntriesFile(date);

                try (PrintWriter writer = new PrintWriter(new FileWriter(weeklyFile, true))) {
                    writer.println(entry);
                }
            }

        } catch (Exception ex) {
            showError("Save Error", "Could not save entries.", ex.getMessage());
        }
    }
    
    private void saveJobSite(String jobSite) {
        if (!jobSiteBox.getItems().contains(jobSite)) {
            jobSiteBox.getItems().add(jobSite);
        }
    }

    private void saveJobSites() {
        try (PrintWriter writer = new PrintWriter(jobSitesFile)) {
            for (String jobSite : jobSiteBox.getItems()) {
                writer.println(jobSite);
            }
        } catch (Exception ex) {
            showError("Save Error", "Could not save job sites.", ex.getMessage());
        }
    }

    private void loadJobSites() {
        if (!jobSitesFile.exists()) {
            return;
        }

        try {
            List<String> jobSites = Files.readAllLines(jobSitesFile.toPath());
            jobSiteBox.setItems(FXCollections.observableArrayList(jobSites));
        } catch (Exception ex) {
            showError("Load Error", "Could not load job sites.", ex.getMessage());
        }
    }

    private void loadEntries() {
        try {
            if (!appFolder.exists()) {
                return;
            }

            entryList.getItems().clear();
            allEntries.clear();

            File[] yearFolders = appFolder.listFiles(File::isDirectory);

            if (yearFolders == null) {
                return;
            }

            for (File yearFolder : yearFolders) {
                File[] weeklyFiles = yearFolder.listFiles((dir, name) -> name.endsWith(".txt"));

                if (weeklyFiles == null) {
                    continue;
                }

                for (File weeklyFile : weeklyFiles) {
                    List<String> entries = Files.readAllLines(weeklyFile.toPath());
                    allEntries.addAll(entries);
                    entryList.getItems().setAll(allEntries);
                }
            }

        } catch (Exception ex) {
            showError("Load Error", "Could not load previous entries.", ex.getMessage());
        }
    }

    private void saveEmployeeName(String employeeName) {
        if (!employeeBox.getItems().contains(employeeName)) {
            employeeBox.getItems().add(employeeName);
        }
    }

    private void saveEmployees() {
        try (PrintWriter writer = new PrintWriter(employeesFile)) {
            for (String employee : employeeBox.getItems()) {
                writer.println(employee);
            }
        } catch (Exception ex) {
            showError("Save Error", "Could not save employee names.", ex.getMessage());
        }
    }

    private void loadEmployees() {
        if (!employeesFile.exists()) {
            return;
        }

        try {
            List<String> employees = Files.readAllLines(employeesFile.toPath());
            employeeBox.setItems(FXCollections.observableArrayList(employees));
        } catch (Exception ex) {
            showError("Load Error", "Could not load employee names.", ex.getMessage());
        }
    }

    private void saveSettings() {
        try (PrintWriter writer = new PrintWriter(settingsFile)) {
            writer.println(lastEmployeeName);
        } catch (Exception ex) {
            showError("Save Error", "Could not save settings.", ex.getMessage());
        }
    }

    private void loadSettings() {
        if (!settingsFile.exists()) {
            return;
        }

        try {
            List<String> settings = Files.readAllLines(settingsFile.toPath());

            if (!settings.isEmpty()) {
                lastEmployeeName = settings.get(0);
            }

        } catch (Exception ex) {
            showError("Load Error", "Could not load settings.", ex.getMessage());
        }
    }

    private void exportEntries(Stage stage) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                "Excel Spreadsheet",
                "Excel Spreadsheet",
                "CSV File",
                "Text File"
        );

        dialog.setTitle("Export As");
        dialog.setHeaderText("Choose export file type");
        dialog.setContentText("Export as:");

        dialog.showAndWait().ifPresent(choice -> {
        	if (choice.equals("Excel Spreadsheet")) {
        	    exportAsExcel(stage);
        	} else if (choice.equals("CSV File")) {
        	    exportAsCsv(stage);
        	} else if (choice.equals("Text File")) {
        	    exportAsText(stage);
        	}
        });
    }

    private void exportAsCsv(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export As CSV");
        fileChooser.setInitialFileName("timesheet_export.csv");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File exportFile = fileChooser.showSaveDialog(stage);

        if (exportFile == null) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(exportFile)) {
            writer.println("Employee,Job Site,Date,Clock In,Clock Out,Lunch Deduction,Total Hours");

            for (String entry : entryList.getItems()) {
                String[] parts = entry.split("\\|");

                if (parts.length < 6) {
                    continue;
                }

                String employee = parts[0].trim();
                String jobSite = parts[1].trim();
                String date = parts[2].trim();

                String[] times = parts[3].trim().split(" - ");
                String clockIn = times[0].trim();
                String clockOut = times[1].trim();

                String lunch = parts[4].replace("Lunch:", "").trim();
                String hours = parts[5].replace("hours", "").trim();

                writer.println(
                        escapeCsv(employee) + "," +
                        escapeCsv(jobSite) + "," +
                        escapeCsv(date) + "," +
                        escapeCsv(clockIn) + "," +
                        escapeCsv(clockOut) + "," +
                        escapeCsv(lunch) + "," +
                        escapeCsv(hours)
                );
            }

            showInfo("Export Complete", "Timesheet entries exported as CSV successfully.");

        } catch (Exception ex) {
            showError("Export Error", "Could not export CSV file.", ex.getMessage());
        }
    }
    
    private void exportAsExcel(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export As Excel Spreadsheet");
        fileChooser.setInitialFileName("timesheet_export.xlsx");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File exportFile = fileChooser.showSaveDialog(stage);

        if (exportFile == null) {
            return;
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Timesheet");

            String[] headers = {
                    "Employee",
                    "Job Site",
                    "Date",
                    "Clock In",
                    "Clock Out",
                    "Lunch Deduction",
                    "Total Hours"
            };

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Timesheet Export");
            titleCell.setCellStyle(titleStyle);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(2);

            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 3;

            for (String entry : entryList.getItems()) {
                String[] parts = entry.split("\\|");

                if (parts.length < 6) {
                    continue;
                }

                String employee = parts[0].trim();
                String jobSite = parts[1].trim();
                String date = parts[2].trim();

                String[] times = parts[3].trim().split(" - ");
                String clockIn = times[0].trim();
                String clockOut = times[1].trim();

                String lunch = parts[4].replace("Lunch:", "").trim();
                String hours = parts[5].replace("hours", "").trim();

                Row row = sheet.createRow(rowNumber++);

                row.createCell(0).setCellValue(employee);
                row.createCell(1).setCellValue(jobSite);
                row.createCell(2).setCellValue(date);
                row.createCell(3).setCellValue(clockIn);
                row.createCell(4).setCellValue(clockOut);
                row.createCell(5).setCellValue(lunch);
                row.createCell(6).setCellValue(Double.parseDouble(hours));
            }

            Row totalRow = sheet.createRow(rowNumber + 1);
            totalRow.createCell(5).setCellValue("Total Hours:");
            totalRow.createCell(6).setCellFormula("SUM(G4:G" + rowNumber + ")");

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
                workbook.write(outputStream);
            }

            showInfo("Export Complete", "Timesheet entries exported as an Excel spreadsheet.");

        } catch (Exception ex) {
            showError("Export Error", "Could not export Excel file.", ex.getMessage());
        }
    }

    private void exportAsText(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export As Text");
        fileChooser.setInitialFileName("timesheet_export.txt");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        File exportFile = fileChooser.showSaveDialog(stage);

        if (exportFile == null) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(exportFile)) {
            writer.println("Timesheet Export");
            writer.println("============================================================");
            writer.println();

            for (String entry : entryList.getItems()) {
                String[] parts = entry.split("\\|");

                if (parts.length < 6) {
                    continue;
                }

                String employee = parts[0].trim();
                String jobSite = parts[1].trim();
                String date = parts[2].trim();

                String[] times = parts[3].trim().split(" - ");
                String clockIn = times[0].trim();
                String clockOut = times[1].trim();

                String lunch = parts[4].replace("Lunch:", "").trim();
                String hours = parts[5].replace("hours", "").trim();

                writer.println("Employee        : " + employee);
                writer.println("Job Site        : " + jobSite);
                writer.println("Date            : " + date);
                writer.println("Clock In        : " + clockIn);
                writer.println("Clock Out       : " + clockOut);
                writer.println("Lunch Deduction : " + lunch);
                writer.println("Total Hours     : " + hours);
                writer.println("------------------------------------------------------------");
            }

            showInfo("Export Complete", "Timesheet entries exported as text successfully.");

        } catch (Exception ex) {
            showError("Export Error", "Could not export text file.", ex.getMessage());
        }
    }

    private void applyFilters(
            String employeeFilter,
            String jobFilter,
            LocalDate selectedWeekDate
    ) {

        entryList.getItems().clear();

        for (String entry : allEntries) {

            String[] parts = entry.split("\\|");

            if (parts.length < 3) {
                continue;
            }

            String employee = parts[0].trim().toLowerCase();
            String jobSite = parts[1].trim().toLowerCase();

            LocalDate entryDate =
                    LocalDate.parse(parts[2].trim());

            boolean matchesEmployee =
                    employeeFilter == null ||
                    employeeFilter.isBlank() ||
                    employee.contains(employeeFilter.toLowerCase());

            boolean matchesJob =
                    jobFilter == null ||
                    jobFilter.isBlank() ||
                    jobSite.contains(jobFilter.toLowerCase());

            boolean matchesWeek = true;

            if (selectedWeekDate != null) {

                LocalDate weekStart =
                        selectedWeekDate.with(
                                TemporalAdjusters.previousOrSame(
                                        DayOfWeek.MONDAY
                                )
                        );

                LocalDate weekEnd =
                        selectedWeekDate.with(
                                TemporalAdjusters.nextOrSame(
                                        DayOfWeek.SUNDAY
                                )
                        );

                matchesWeek =
                        !entryDate.isBefore(weekStart) &&
                        !entryDate.isAfter(weekEnd);
            }

            if (matchesEmployee &&
                    matchesJob &&
                    matchesWeek) {

                entryList.getItems().add(entry);
            }
        }
    }
    
    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }

        return value;
    }
    
    private void openEmailClient() {
        try {
            String subject = "Timesheet Entries";
            String body = buildEmailBody();

            String mailto = "mailto:?subject=" +
                    URLEncoder.encode(subject, StandardCharsets.UTF_8) +
                    "&body=" +
                    URLEncoder.encode(body, StandardCharsets.UTF_8);

            if (Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {

                Desktop.getDesktop().mail(new URI(mailto));

            } else {
                showError(
                        "Email Error",
                        "Could not open email client.",
                        "No default email application is set on this computer."
                );
            }

        } catch (Exception ex) {
            showError(
                    "Email Error",
                    "Could not open email client.",
                    ex.getMessage()
            );
        }
    }
    
    private String buildEmailBody() {
        StringBuilder body = new StringBuilder();

        body.append("Timesheet Entries\n");
        body.append("=================\n\n");

        for (String entry : entryList.getItems()) {
            body.append(entry).append("\n");
        }

        return body.toString();
    }
    
    private void emailExcelFile() {
        try {
            File exportFolder = new File(appFolder, "Exports");
            exportFolder.mkdirs();

            File excelFile = new File(exportFolder, "timesheet_email_export.xlsx");

            createExcelFile(excelFile);

            String subject = URLEncoder.encode(
                    "Timesheet Entries",
                    StandardCharsets.UTF_8
            ).replace("+", "%20");

            String bodyText =
                    "Hello,\n\n" +
                    "Please find the attached timesheet Excel file.\n\n" +
                    "The export folder has been opened automatically.";

            String body = URLEncoder.encode(
                    bodyText,
                    StandardCharsets.UTF_8
            ).replace("+", "%20");
            
            URI mailUri = new URI("mailto:?subject=" + subject + "&body=" + body);

            if (Desktop.isDesktopSupported() &&
                    Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {

                Desktop.getDesktop().mail(mailUri);
                Desktop.getDesktop().open(exportFolder);
                Desktop.getDesktop().open(exportFolder);

            } else {
                showError(
                        "Email Error",
                        "Could not open email client.",
                        "No default email application is set."
                );
            }

        } catch (Exception ex) {
            showError(
                    "Email Error",
                    "Could not create email export.",
                    ex.getMessage()
            );
        }
    }
    
    private void createExcelFile(File exportFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Timesheet");

            String[] headers = {
                    "Employee",
                    "Job Site",
                    "Date",
                    "Clock In",
                    "Clock Out",
                    "Lunch Deduction",
                    "Total Hours"
            };

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Row titleRow = sheet.createRow(0);
            org.apache.poi.ss.usermodel.Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Timesheet Export");
            titleCell.setCellStyle(titleStyle);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(2);

            for (int i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 3;

            for (String entry : entryList.getItems()) {
                String[] parts = entry.split("\\|");

                if (parts.length < 6) {
                    continue;
                }

                String employee = parts[0].trim();
                String jobSite = parts[1].trim();
                String date = parts[2].trim();

                String[] times = parts[3].trim().split(" - ");
                String clockIn = times[0].trim();
                String clockOut = times[1].trim();

                String lunch = parts[4].replace("Lunch:", "").trim();
                String hours = parts[5].replace("hours", "").trim();

                Row row = sheet.createRow(rowNumber++);

                row.createCell(0).setCellValue(employee);
                row.createCell(1).setCellValue(jobSite);
                row.createCell(2).setCellValue(date);
                row.createCell(3).setCellValue(clockIn);
                row.createCell(4).setCellValue(clockOut);
                row.createCell(5).setCellValue(lunch);
                row.createCell(6).setCellValue(Double.parseDouble(hours));
            }

            Row totalRow = sheet.createRow(rowNumber + 1);
            totalRow.createCell(5).setCellValue("Total Hours:");
            totalRow.createCell(6).setCellFormula("SUM(G4:G" + rowNumber + ")");

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream outputStream = new FileOutputStream(exportFile)) {
                workbook.write(outputStream);
            }
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
