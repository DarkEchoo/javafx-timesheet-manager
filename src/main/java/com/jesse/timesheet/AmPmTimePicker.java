package com.jesse.timesheet;

import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AmPmTimePicker extends HBox {

    private TextField timeField;
    private Button clockButton;
    private LocalTime selectedTime;
    private String defaultAmPm;

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");

    public AmPmTimePicker() {
        this("AM");
    }

    public AmPmTimePicker(String defaultAmPm) {
        this.defaultAmPm = defaultAmPm;
        selectedTime = defaultAmPm.equals("PM")
                ? LocalTime.of(18, 0)
                : LocalTime.of(6, 0);

        timeField = new TextField(getFormattedTime());
        timeField.setPrefWidth(100);
        timeField.setEditable(false);

        clockButton = new Button("🕒");

        timeField.setOnMouseClicked(e -> showClockPopup());
        clockButton.setOnAction(e -> showClockPopup());

        this.setSpacing(5);
        this.getChildren().addAll(timeField, clockButton);
    }

    private void showClockPopup() {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        Spinner<Integer> hourSpinner = new Spinner<>(1, 12, getDisplayHour());
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, selectedTime.getMinute());
        ComboBox<String> amPmBox = new ComboBox<>();

        amPmBox.getItems().addAll("AM", "PM");
        amPmBox.setValue(selectedTime.getHour() >= 12 ? "PM" : "AM");

        hourSpinner.setEditable(true);
        minuteSpinner.setEditable(true);

        hourSpinner.setPrefWidth(80);
        minuteSpinner.setPrefWidth(80);
        amPmBox.setPrefWidth(80);

        hourSpinner.getEditor().setOnMouseClicked(e -> hourSpinner.getEditor().selectAll());
        minuteSpinner.getEditor().setOnMouseClicked(e -> minuteSpinner.getEditor().selectAll());

        hourSpinner.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.UP) {
                hourSpinner.increment();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                hourSpinner.decrement();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            }
        });

        minuteSpinner.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.UP) {
                minuteSpinner.increment();
                e.consume();
            } else if (e.getCode() == KeyCode.DOWN) {
                minuteSpinner.decrement();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            }
        });

        amPmBox.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            }
        });

        Button setButton = new Button("Set Time");

        setButton.setOnAction(e -> {
            setSelectedTime(
                    hourSpinner.getValue(),
                    minuteSpinner.getValue(),
                    amPmBox.getValue()
            );

            popup.hide();
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: gray;");

        grid.add(new Label("Hour"), 0, 0);
        grid.add(hourSpinner, 1, 0);

        grid.add(new Label("Minute"), 0, 1);
        grid.add(minuteSpinner, 1, 1);

        grid.add(new Label("AM/PM"), 0, 2);
        grid.add(amPmBox, 1, 2);

        grid.add(setButton, 1, 3);

        popup.getContent().add(grid);

        Point2D point = clockButton.localToScreen(0, clockButton.getHeight());
        popup.show(clockButton, point.getX(), point.getY());

        hourSpinner.requestFocus();
    }

    private void setSelectedTime(int displayHour, int minute, String amPm) {
        int hour = displayHour;

        if (amPm.equals("AM") && hour == 12) {
            hour = 0;
        } else if (amPm.equals("PM") && hour != 12) {
            hour += 12;
        }

        selectedTime = LocalTime.of(hour, minute);
        timeField.setText(getFormattedTime());
    }

    private int getDisplayHour() {
        int hour = selectedTime.getHour();

        if (hour == 0) {
            return 12;
        }

        if (hour > 12) {
            return hour - 12;
        }

        return hour;
    }

    public LocalTime getTime() {
        return selectedTime;
    }

    public String getFormattedTime() {
        return selectedTime.format(formatter);
    }

    public void clear() {
        selectedTime = defaultAmPm.equals("PM")
                ? LocalTime.of(18, 0)
                : LocalTime.of(6, 0);

        timeField.setText(getFormattedTime());
    }
}