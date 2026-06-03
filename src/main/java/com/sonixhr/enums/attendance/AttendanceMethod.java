package com.sonixhr.enums.attendance;

public enum AttendanceMethod {
    SELF("Self Marking", "Employee marked their own attendance"),
    MANUAL("Manual Entry", "Admin/Manager marked on behalf"),
    BIOMETRIC("Biometric Device", "Auto-marked by biometric device");

    private final String displayName;
    private final String description;

    AttendanceMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
