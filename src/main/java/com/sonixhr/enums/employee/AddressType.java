package com.sonixhr.enums.employee;

public enum AddressType {

    CURRENT_RESIDENTIAL("Current Residential",
            "Where the employee currently lives"),

    PERMANENT("Permanent",
            "Permanent/domicile address - used for PF nomination and legal documents"),

    WORK("Work",
            "Work office address - determines Professional Tax (PT) and LWF jurisdiction"),

    CORRESPONDENCE("Correspondence",
            "Where to send payslips, offer letters, and official communications");

    private final String displayName;
    private final String description;

    AddressType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription()  { return description; }
}
