package com.sonixhr.enums.attendance;

public enum AttendanceStatus {
    PRESENT("Present"),
    LATE("Late"),
    ABSENT("Absent"),
    HALF_DAY("Half Day"),
    WEEK_OFF("Week Off"),
    HOLIDAY("Holiday");

    private final String description;

    AttendanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
