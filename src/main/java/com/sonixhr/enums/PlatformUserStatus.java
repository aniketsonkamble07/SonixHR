package com.sonixhr.enums;

public enum PlatformUserStatus {
    ACTIVE("Active", "success", "User can log in and perform actions"),
    INACTIVE("Inactive", "secondary", "User cannot log in"),
    SUSPENDED("Suspended", "danger", "Temporarily blocked"),
    PENDING_VERIFICATION("Pending Verification", "warning", "Awaiting email verification"),
    LOCKED("Locked", "danger", "Account locked due to multiple failed attempts"),
    DELETED("Deleted", "danger", "Account has been permanently deleted");

    private final String displayName;
    private final String badgeColor;
    private final String description;

    PlatformUserStatus(String displayName, String badgeColor, String description) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getBadgeColor() { return badgeColor; }
    public String getDescription() { return description; }

    public boolean isActive() { return this == ACTIVE; }
    public boolean isLocked() { return this == LOCKED; }
    public boolean isDeleted() { return this == DELETED; }
    public boolean isSuspended() { return this == SUSPENDED; }
    public boolean isInactive() { return this == INACTIVE; }


    public boolean canLogin() {
        return this == ACTIVE;
    }


    public boolean needsVerification() {
        return this == PENDING_VERIFICATION;
    }
}