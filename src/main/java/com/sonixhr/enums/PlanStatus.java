package com.sonixhr.enums;

public enum PlanStatus {
    ACTIVE("active", "Active", "success"),
    TRIAL("trial", "Trial Period", "warning"),
    SUSPENDED("suspended", "Suspended", "danger"),
    CANCELLED("cancelled", "Cancelled", "secondary");

    private final String code;
    private final String displayName;
    private final String badgeColor;

    PlanStatus(String code, String displayName, String badgeColor) {
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

    public static PlanStatus fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Code cannot be null");
        }

        for (PlanStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown plan status: " + code);
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isTrial() {
        return this == TRIAL;
    }

    public boolean isSuspended() {
        return this == SUSPENDED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}