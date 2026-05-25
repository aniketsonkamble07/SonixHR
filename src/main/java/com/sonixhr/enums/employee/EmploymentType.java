package com.sonixhr.enums.employee;
public enum EmploymentType {
    FULL_TIME("full_time", "Full Time", "primary"),
    PART_TIME("part_time", "Part Time", "info"),
    CONTRACT("contract", "Contract", "warning"),
    INTERN("intern", "Intern", "secondary"),
    CONSULTANT("consultant", "Consultant", "info"),
    FREELANCER("freelancer", "Freelancer", "secondary");

    private final String code;
    private final String displayName;
    private final String badgeColor;

    EmploymentType(String code, String displayName, String badgeColor) {
        this.code = code;
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public static EmploymentType fromCode(String code) {
        for (EmploymentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return FULL_TIME;
    }

    public boolean isFullTime() {
        return this == FULL_TIME;
    }

    public boolean isPartTime() {
        return this == PART_TIME;
    }

    public boolean isContract() {
        return this == CONTRACT;
    }
}