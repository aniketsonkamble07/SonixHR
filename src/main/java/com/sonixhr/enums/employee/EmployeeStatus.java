package com.sonixhr.enums.employee;

public enum EmployeeStatus {
    ACTIVE("active", "Active", "success"),
    INACTIVE("inactive", "Inactive", "secondary"),
    PROBATION("probation", "On Probation", "warning"),
    RESIGNED("resigned", "Resigned", "secondary"),
    TERMINATED("terminated", "Terminated", "danger"),
    ON_LEAVE("on_leave", "On Leave", "info"),
    SUSPENDED("suspended", "Suspended", "danger"),
    INVITED("invited", "Invited", "primary");
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
        return ACTIVE; // Default
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isInactive() {
        return this == INACTIVE;
    }

    public boolean isOnProbation() {
        return this == PROBATION;
    }

    public boolean isResigned() {
        return this == RESIGNED;
    }

    public boolean isTerminated() {
        return this == TERMINATED;
    }

    public boolean isOnLeave() {
        return this == ON_LEAVE;
    }
}