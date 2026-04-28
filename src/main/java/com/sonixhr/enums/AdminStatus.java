package com.sonixhr.enums;

public enum AdminStatus {
    ACTIVE("Active", "success"),
    INACTIVE("Inactive", "secondary"),
    SUSPENDED("Suspended", "danger"),
    LOCKED("Locked", "warning"),
    PENDING_VERIFICATION("Pending Verification", "info");

    private final String displayName;
    private final String badgeColor;

    AdminStatus(String displayName, String badgeColor) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    public String getDisplayName() { return displayName; }
    public String getBadgeColor() { return badgeColor; }
}
