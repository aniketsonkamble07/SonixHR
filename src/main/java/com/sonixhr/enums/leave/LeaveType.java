package com.sonixhr.enums.leave;

import lombok.Getter;

@Getter
public enum LeaveType {
    CASUAL("Casual Leave", 12, "CL", "For personal/family events"),
    SICK("Sick Leave", 12, "SL", "For medical reasons"),
    EARNED("Earned Leave", 15, "EL", "Accrued based on service"),
    EMERGENCY("Emergency Leave", 3, "EML", "For urgent situations"),
    MATERNITY("Maternity Leave", 182, "ML", "For new mothers"),
    PATERNITY("Paternity Leave", 5, "PL", "For new fathers"),
    UNPAID("Unpaid Leave", 0, "UL", "Leave without pay"),
    COMPENSATORY("Compensatory Off", 0, "CO", "Compensation for extra work");

    private final String displayName;
    private final int defaultDaysPerYear;
    private final String shortCode;
    private final String description;

    LeaveType(String displayName, int defaultDaysPerYear, String shortCode, String description) {
        this.displayName = displayName;
        this.defaultDaysPerYear = defaultDaysPerYear;
        this.shortCode = shortCode;
        this.description = description;
    }

    public static LeaveType fromCode(String code) {
        for (LeaveType type : values()) {
            if (type.name().equalsIgnoreCase(code) || type.shortCode.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return CASUAL;
    }

    public boolean isPaid() {
        return this != UNPAID;
    }

    public boolean hasLimit() {
        return this != UNPAID && this != COMPENSATORY;
    }
}