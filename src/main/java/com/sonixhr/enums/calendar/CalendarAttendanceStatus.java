package com.sonixhr.enums.calendar;

import lombok.Getter;

@Getter
public enum CalendarAttendanceStatus {
    PRESENT("Present", "#4caf50"),
    ABSENT("Absent", "#f44336"),
    LATE("Late", "#ff9800"),
    HALF_DAY("Half Day", "#2196f3"),
    ON_LEAVE("On Leave", "#9c27b0"),
    WEEKEND("Weekend", "#f44336"),
    HOLIDAY("Holiday", "#9c27b0");

    private final String displayName;
    private final String color;

    CalendarAttendanceStatus(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }
    public static CalendarAttendanceStatus fromString(String status) {
        for (CalendarAttendanceStatus s : values()) {
            if (s.name().equalsIgnoreCase(status)) {
                return s;
            }
        }
        return null;
    }
}