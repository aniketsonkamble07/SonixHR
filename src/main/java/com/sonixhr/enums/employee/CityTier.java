package com.sonixhr.enums.employee;

/**
 * Indian city tier classification used for HRA (House Rent Allowance) exemption calculation.
 *
 * IT Act Section 10(13A):
 *   Metro cities  → HRA exempt up to 50% of Basic salary
 *   All others    → HRA exempt up to 40% of Basic salary
 */
public enum CityTier {

    METRO("Metro", 50,
            "Delhi, Mumbai, Kolkata, Chennai — 50% HRA exemption under IT Act Sec 10(13A)"),

    TIER_1("Tier-1", 40,
            "Bengaluru, Hyderabad, Pune, Ahmedabad, Surat — 40% HRA"),

    TIER_2("Tier-2", 40,
            "Other urban/industrial cities — 40% HRA"),

    TIER_3("Tier-3", 40,
            "Semi-urban and rural areas — 40% HRA");

    private final String displayName;
    private final int hraExemptionPercent;
    private final String description;

    CityTier(String displayName, int hraExemptionPercent, String description) {
        this.displayName = displayName;
        this.hraExemptionPercent = hraExemptionPercent;
        this.description = description;
    }

    public String getDisplayName()       { return displayName; }
    public int    getHraExemptionPercent() { return hraExemptionPercent; }
    public String getDescription()       { return description; }
}
