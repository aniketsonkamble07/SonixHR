package com.sonixhr.enums.attendance;

import lombok.Getter;

@Getter
public enum AttendanceStatus {
    PRESENT("Present"),
    ABSENT("Absent"),
    LATE("Late"),
    HALF_DAY("Half Day"),
    ON_LEAVE("On Leave");

    private final String displayName;

    AttendanceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}