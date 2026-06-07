package com.sonixhr.enums.leave;

public enum WeekendConfig {
    SATURDAY_SUNDAY("Saturday & Sunday"),
    FRIDAY_SATURDAY("Friday & Saturday"),
    SUNDAY_ONLY("Sunday Only"),
    CUSTOM("Custom Days");

    private final String displayName;

    WeekendConfig(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}