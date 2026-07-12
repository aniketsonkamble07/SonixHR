package com.sonixhr.enums.payroll;

public enum PayrollComponent {
    BASIC("Basic Salary", "ALLOWANCE", null),
    HRA("House Rent Allowance", "ALLOWANCE", null),
    LTA("Leave Travel Allowance", "ALLOWANCE", null),
    CONVEYANCE("Conveyance Allowance", "ALLOWANCE", null),
    SPECIAL_ALLOWANCE("Special Allowance", "ALLOWANCE", null),
    EPF_EE("EPF Employee Contribution", "DEDUCTION", "#min(WAGES_BASE * EPF_EE_RATE, EPF_EE_CAP)"),
    EPF_ER("EPF Employer Contribution", "DEDUCTION", "#max(WAGES_BASE * EPF_ER_RATE - EPS_ER, 0)"),
    EPS_ER("EPS Pension Share", "DEDUCTION", "#min(#round(WAGES_BASE * EPS_ER_RATE), EPS_ER_CAP)"),
    EDLI("EDLI Insurance Premium", "DEDUCTION", "#min(WAGES_BASE, EDLI_CEILING) * EDLI_RATE"),
    ESI_EE("ESI Employee Contribution", "DEDUCTION", "WAGES_BASE * ESI_EE_RATE"),
    ESI_ER("ESI Employer Contribution", "DEDUCTION", "WAGES_BASE * ESI_ER_RATE"),
    PT_DEDUCTION("Professional Tax", "DEDUCTION", null),
    PT("Professional Tax", "DEDUCTION", null),
    TDS("Tax Deducted at Source", "DEDUCTION", null),
    OVERTIME("Overtime Payment", "ALLOWANCE", null);

    private final String name;
    private final String type;
    private final String defaultFormula;

    PayrollComponent(String name, String type, String defaultFormula) {
        this.name = name;
        this.type = type;
        this.defaultFormula = defaultFormula;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDefaultFormula() {
        return defaultFormula;
    }

    public static PayrollComponent fromCode(String code) {
        if (code == null) {
            return null;
        }
        try {
            return PayrollComponent.valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
