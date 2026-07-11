package com.sonixhr.enums;

public enum MaritalStatus {
    SINGLE("single", "Single", "secondary"),
    MARRIED("married", "Married", "primary"),
    DIVORCED("divorced", "Divorced", "warning"),
    WIDOWED("widowed", "Widowed", "secondary");

    private final String code;
    private final String displayName;
    private final String badgeType;

    MaritalStatus(String code, String displayName, String badgeType) {
        this.code = code;
        this.displayName = displayName;
        this.badgeType = badgeType;
    }

    // Getters (no setters needed since enum values are constants)
    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeType() {
        return badgeType;
    }

    // Helper method to get enum from code
    public static MaritalStatus fromCode(String code) {
        for (MaritalStatus status : MaritalStatus.values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    // Helper method to get enum from display name
    public static MaritalStatus fromDisplayName(String displayName) {
        for (MaritalStatus status : MaritalStatus.values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static MaritalStatus fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim();
        for (MaritalStatus status : values()) {
            if (status.name().equalsIgnoreCase(clean) ||
                status.code.equalsIgnoreCase(clean) ||
                status.displayName.equalsIgnoreCase(clean)) {
                return status;
            }
        }
        return null;
    }
}