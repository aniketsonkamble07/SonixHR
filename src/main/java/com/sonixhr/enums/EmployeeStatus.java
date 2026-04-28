package com.sonixhr.enums;

public enum EmployeeStatus {
    ACTIVE("active", "Active", "success"),
    PROBATION("probation", "On Probation", "warning"),
    RESIGNED("resigned", "Resigned", "secondary");

    private final String code;
    private final String displayName;
    private final String badgeColor;

    EmployeeStatus(String code, String displayName, String badgeColor) {
        this.code = code;
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public static EmployeeStatus fromCode(String code) {
        for (EmployeeStatus status : EmployeeStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown employee status: " + code);
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isOnProbation() {
        return this == PROBATION;
    }

    public boolean isResigned() {
        return this == RESIGNED;
    }
}