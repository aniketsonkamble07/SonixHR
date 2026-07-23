package com.sonixhr.enums;

import lombok.Getter;

// PlanStatus enum for subscription lifecycle
@Getter
public enum PlanStatus {
    ACTIVE("active", "Active", "success"),
    PAST_DUE("past_due", "Past Due", "warning"),
    EXPIRED("expired", "Expired", "danger"),
    SUSPENDED("suspended", "Suspended", "danger"),
    CANCELLED("cancelled", "Cancelled", "secondary"),
    TERMINATED("terminated", "Terminated", "danger"),
    FROZEN("frozen", "Frozen", "warning"),
    PAUSED("paused", "Paused", "warning"),
    NOT_ACTIVATED("not_activated", "Not Activated", "secondary");

    private final String code;
    private final String displayName;
    private final String badgeColor;

    PlanStatus(String code, String displayName, String badgeColor) {
        this.code = code;
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static PlanStatus fromLegacy(String value) {
        if (value == null) return null;
        for (PlanStatus status : PlanStatus.values()) {
            if (status.name().equalsIgnoreCase(value) || status.code.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown plan status: " + value);
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String toLegacy() {
        return this.code;
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

    public boolean isSuspended() {
        return this == SUSPENDED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    public boolean isExpired() {
        return this == EXPIRED;
    }
}