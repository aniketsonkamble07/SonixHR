package com.sonixhr.enums.leave;

import lombok.Getter;

@Getter
public enum LeaveStatus {
    PENDING("Pending Approval", "#ff9800"),
    APPROVED("Approved", "#4caf50"),
    REJECTED("Rejected", "#f44336"),
    CANCELLED("Cancelled", "#9e9e9e");

    private final String displayName;
    private final String color;

    LeaveStatus(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public static LeaveStatus fromDisplayName(String displayName) {
        for (LeaveStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        return PENDING;
    }

    public boolean isActive() {
        return this == PENDING || this == APPROVED;
    }

    public boolean canApprove() {
        return this == PENDING;
    }

    public boolean canCancel() {
        return this == PENDING || this == APPROVED;
    }

    public String getStatusBadge() {
        switch (this) {
            case PENDING:
                return "badge-warning";
            case APPROVED:
                return "badge-success";
            case REJECTED:
                return "badge-danger";
            case CANCELLED:
                return "badge-secondary";
            default:
                return "badge-secondary";
        }
    }
}