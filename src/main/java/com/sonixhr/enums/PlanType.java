package com.sonixhr.enums;

public enum PlanType {
    BASIC("basic", "Basic Plan", 49.00, 100, 1024),           // $49/month
    MODERATE("moderate", "Moderate Plan", 99.00, 500, 5120),  // $99/month
    PREMIUM("premium", "Premium Plan", 299.00, 2000, 20480);   // $299/month

    private final String code;
    private final String displayName;
    private final double monthlyPrice;
    private final int maxEmployees;
    private final int maxStorageMb;

    PlanType(String code, String displayName, double monthlyPrice, int maxEmployees, int maxStorageMb) {
        this.code = code;
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.maxEmployees = maxEmployees;
        this.maxStorageMb = maxStorageMb;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMonthlyPrice() {
        return monthlyPrice;
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