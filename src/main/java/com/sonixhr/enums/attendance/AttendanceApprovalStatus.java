package com.sonixhr.enums.attendance;



public enum AttendanceApprovalStatus {
    NOT_REQUIRED("No Approval Required", "Auto-approved"),
    PENDING("Pending Approval", "Waiting for manager approval"),
    APPROVED("Approved", "Approved by manager"),
    REJECTED("Rejected", "Rejected by manager");

    private final String displayName;
    private final String description;

    AttendanceApprovalStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}