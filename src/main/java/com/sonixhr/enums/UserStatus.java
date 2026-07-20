package com.sonixhr.enums;

import lombok.Getter;

@Getter
public enum UserStatus {

    ACTIVE("Active", "success", "User can log in and perform actions"),
    INACTIVE("Inactive", "secondary", "User cannot log in"),
    SUSPENDED("Suspended", "danger", "Temporarily blocked"),
    DELETED("Deleted", "danger", "Account has been permanently deleted"),
    PENDING_VERIFICATION("Pending Verification", "warning", "User needs to verify email before logging in");

    private final String displayName;
    private final String badgeColor;
    private final String description;

    UserStatus(String displayName, String badgeColor, String description) {
        this.displayName = displayName;
        this.badgeColor = badgeColor;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public String getDescription() {
        return description;
    }

    // =====================================================
    // STATUS CHECK METHODS
    // =====================================================

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isInactive() {
        return this == INACTIVE;
    }

    public boolean isSuspended() {
        return this == SUSPENDED;
    }



    public boolean isDeleted() {
        return this == DELETED;
    }



    // ✅ FIXED: Use isActive() instead of duplicate logic
    public boolean canLogin() {
        return isActive();
    }



    // =====================================================
    // HELPER METHODS
    // =====================================================

    /**
     * Get enum from display name (case insensitive)
     */
    public static UserStatus fromDisplayName(String displayName) {
        for (UserStatus status : values()) {
            if (status.getDisplayName().equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Get enum from string value (case insensitive)
     */
    public static UserStatus fromString(String value) {
        try {
            return UserStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }



    /**
     * Get CSS class for UI display
     */
    public String getCssClass() {
        switch (this) {
            case ACTIVE: return "status-active";
            case INACTIVE: return "status-inactive";
            case SUSPENDED: return "status-suspended";
            case DELETED: return "status-deleted";
            default: return "status-unknown";
        }
    }
}