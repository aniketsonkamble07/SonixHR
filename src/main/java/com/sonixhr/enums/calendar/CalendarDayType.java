package com.sonixhr.enums.calendar;

import lombok.Getter;

@Getter
public enum CalendarDayType {
    ATTENDANCE("Attendance"),
    LEAVE("Leave"),
    HOLIDAY("Holiday"),
    WEEKEND("Weekend"),
    ABSENT("Absent");

    private final String displayName;

    CalendarDayType(String displayName) {
        this.displayName = displayName;
    }
}