package com.sonixhr.enums;

public enum CancellationType {
    IMMEDIATE("Immediate cancellation"),
    END_OF_PERIOD("Cancel at end of billing period");

    private final String description;

    CancellationType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}