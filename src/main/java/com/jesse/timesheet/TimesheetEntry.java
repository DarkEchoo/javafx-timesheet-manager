package com.jesse.timesheet;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public class TimesheetEntry {

    private String employeeName;
    private String jobSite;
    private LocalDate date;
    private LocalTime clockIn;
    private LocalTime clockOut;

    private int lunchMinutes;

    public TimesheetEntry(
            String employeeName,
            String jobSite,
            LocalDate date,
            LocalTime clockIn,
            LocalTime clockOut,
            int lunchMinutes
    ) {

        this.employeeName = employeeName;
        this.jobSite = jobSite;
        this.date = date;
        this.clockIn = clockIn;
        this.clockOut = clockOut;
        this.lunchMinutes = lunchMinutes;
    }

    public double getHoursWorked() {

        double totalHours =
                Duration.between(clockIn, clockOut).toMinutes() / 60.0;

        double lunchHours = lunchMinutes / 60.0;

        return totalHours - lunchHours;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getJobSite() {
        return jobSite;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getClockIn() {
        return clockIn;
    }

    public LocalTime getClockOut() {
        return clockOut;
    }

    public int getLunchMinutes() {
        return lunchMinutes;
    }
}