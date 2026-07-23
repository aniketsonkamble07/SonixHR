package com.sonixhr.enums;

public enum TriggerSource {
    USER("User initiated action"),
    SYSTEM("System automated action"),
    ADMIN("Admin initiated action"),
    WEBHOOK("External webhook trigger"),
    SCHEDULER("Scheduled job trigger");

    private final String description;

    TriggerSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}