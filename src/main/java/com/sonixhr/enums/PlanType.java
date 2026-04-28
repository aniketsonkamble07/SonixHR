package com.sonixhr.enums;

public enum PlanType {
    BASIC("basic", "Basic Plan", 100, 1024),
    MODERATE("moderate", "Moderate Plan", 500, 5120),
    PREMIUM("premium", "Premium Plan", 2000, 20480);

    private final String code;
    private final String displayName;
    private final int maxEmployees;
    private final int maxStorageMb;

    PlanType(String code, String displayName, int maxEmployees, int maxStorageMb) {
        this.code = code;
        this.displayName = displayName;
        this.maxEmployees = maxEmployees;
        this.maxStorageMb = maxStorageMb;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxEmployees() {
        return maxEmployees;
    }

    public int getMaxStorageMb() {
        return maxStorageMb;
    }

    public static PlanType fromCode(String code) {
        for (PlanType plan : PlanType.values()) {
            if (plan.code.equals(code)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Unknown plan type: " + code);
    }

    public boolean isBasic() {
        return this == BASIC;
    }

    public boolean isModerate() {
        return this == MODERATE;
    }

    public boolean isPremium() {
        return this == PREMIUM;
    }
}
