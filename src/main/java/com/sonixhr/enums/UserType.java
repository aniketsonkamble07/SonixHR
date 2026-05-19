package com.sonixhr.enums;

public enum UserType {
    PLATFORM("Platform User"),
    TENANT("Tenant User");

    private final String displayName;

    UserType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}