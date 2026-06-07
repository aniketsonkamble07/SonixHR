package com.sonixhr.enums;

import lombok.Getter;

import java.text.NumberFormat;
import java.util.Locale;

@Getter
public enum PlanType {

    TRIAL("trial", "Trial Plan", 0.00, 10, 512, 14),           // 14 days trial
    BASIC("basic", "Basic Plan", 49.00, 100, 1024, 0),         // $49/month
    MODERATE("moderate", "Moderate Plan", 99.00, 500, 5120, 0), // $99/month
    PREMIUM("premium", "Premium Plan", 299.00, 2000, 20480, 0),  // $299/month
    ENTERPRISE("enterprise", "Enterprise Plan", 999.00, 10000, 102400, 0);  // Custom pricing

    private final String code;
    private final String displayName;
    private final double monthlyPrice;
    private final int maxEmployees;
    private final int maxStorageMb;
    private final int trialDays;  // ✅ Added for trial plans

    PlanType(String code, String displayName, double monthlyPrice,
             int maxEmployees, int maxStorageMb, int trialDays) {
        this.code = code;
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.maxEmployees = maxEmployees;
        this.maxStorageMb = maxStorageMb;
        this.trialDays = trialDays;
    }

    // =====================================================
    // GETTERS
    // =====================================================

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMonthlyPrice() {
        return monthlyPrice;
    }

    public double getYearlyPrice() {
        return monthlyPrice * 12 * 0.9;  // 10% discount for yearly
    }

    public String getFormattedMonthlyPrice() {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return currencyFormat.format(monthlyPrice);
    }

    public String getFormattedYearlyPrice() {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
        return currencyFormat.format(getYearlyPrice());
    }

    public int getMaxEmployees() {
        return maxEmployees;
    }

    public int getMaxStorageMb() {
        return maxStorageMb;
    }

    public int getTrialDays() {
        return trialDays;
    }

    public boolean isTrial() {
        return this == TRIAL;
    }

    public boolean isPaid() {
        return this != TRIAL;
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    public static PlanType fromCode(String code) {
        if (code == null) {
            return TRIAL;  // ✅ Default to TRIAL
        }
        for (PlanType plan : PlanType.values()) {
            if (plan.code.equalsIgnoreCase(code)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Unknown plan type: " + code);
    }

    public static PlanType fromName(String name) {
        try {
            return PlanType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TRIAL;
        }
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

    public boolean isEnterprise() {
        return this == ENTERPRISE;
    }

    /**
     * Check if plan allows additional employees beyond max
     */
    public boolean allowsOverages() {
        return this == ENTERPRISE;
    }

    /**
     * Get feature list for UI display
     */
    public String[] getFeatures() {
        switch (this) {
            case TRIAL:
                return new String[]{
                        "Full feature access for 14 days",
                        "Up to " + maxEmployees + " employees",
                        maxStorageMb + " MB storage",
                        "Email support"
                };
            case BASIC:
                return new String[]{
                        "Core HR features",
                        "Up to " + maxEmployees + " employees",
                        maxStorageMb + " MB storage",
                        "Email support",
                        "Basic reports"
                };
            case MODERATE:
                return new String[]{
                        "Advanced HR features",
                        "Up to " + maxEmployees + " employees",
                        maxStorageMb + " MB storage",
                        "Priority email support",
                        "Advanced reports",
                        "API access"
                };
            case PREMIUM:
                return new String[]{
                        "All HR features",
                        "Up to " + maxEmployees + " employees",
                        maxStorageMb + " MB storage",
                        "24/7 phone support",
                        "Custom reports",
                        "API access",
                        "SSO integration"
                };
            case ENTERPRISE:
                return new String[]{
                        "Custom features",
                        "Unlimited employees",
                        "Custom storage",
                        "Dedicated support",
                        "Custom reports",
                        "Full API access",
                        "SSO + SAML",
                        "SLA agreement"
                };
            default:
                return new String[]{};
        }
    }
}