package com.sonixhr.dto.payroll;

import com.sonixhr.entity.payroll.LopBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object for tenant payroll configuration")
public class TenantPayrollConfigResponse {

    @Schema(description = "Configuration ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Tenant ID", example = "1")
    private Long tenantId;

    @Schema(description = "LOP basis", example = "CALENDAR_DAYS")
    private LopBasis lopBasis;

    @Schema(description = "Working days per month", example = "22")
    private Integer workingDaysPerMonth;

    @Schema(description = "Enable PF capping", example = "true")
    private boolean enablePfCapping;

    @Schema(description = "Enable ESI", example = "true")
    private boolean enableEsi;

    @Schema(description = "Enable Professional Tax", example = "true")
    private boolean enablePt;

    @Schema(description = "Enforce New Labour Codes", example = "true")
    private boolean enforceNewLabourCodes;

    @Schema(description = "Default currency", example = "INR")
    private String defaultCurrency;

    @Schema(description = "Default tax regime", example = "OLD_REGIME")
    private String defaultTaxRegime;

    @Schema(description = "Enable Overtime Pay", example = "false")
    private boolean enableOvertime;

    @Schema(description = "Overtime Rate Per Hour", example = "150.00")
    private BigDecimal overtimeRatePerHour;

    @Schema(description = "Effective from date", example = "2024-01-01")
    private LocalDate effectiveFrom;

    @Schema(description = "Effective to date", example = "2024-12-31")
    private LocalDate effectiveTo;

    @Schema(description = "Is configuration active", example = "true")
    private boolean isActive;

    @Schema(description = "Salary structures for this configuration")
    private List<SalaryStructureResponse> salaryStructures;

    // ============================================================
    // STATIC INNER CLASS - IMPORTANT: Must be static
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Salary structure response")
    public static class SalaryStructureResponse {
        @Schema(description = "Structure ID", example = "123e4567-e89b-12d3-a456-426614174000")
        private UUID id;

        @Schema(description = "Component code", example = "BASIC")
        private String componentCode;

        @Schema(description = "Component name", example = "Basic Salary")
        private String componentName;

        @Schema(description = "Component type", example = "EARNING")
        private String componentType;

        @Schema(description = "Calculation type", example = "PERCENTAGE_OF_CTC")
        private String calculationType;

        @Schema(description = "Value", example = "50.00")
        private BigDecimal value;

        @Schema(description = "Evaluation order", example = "1")
        private Integer evaluationOrder;

        @Schema(description = "Is part of PF wages", example = "true")
        private boolean isPartOfPfWages;

        @Schema(description = "Is part of ESI wages", example = "true")
        private boolean isPartOfEsiWages;

        @Schema(description = "Is taxable", example = "true")
        private boolean isTaxable;

        @Schema(description = "Is LOP applicable", example = "true")
        private boolean isLopApplicable;

        @Schema(description = "Is employer contribution", example = "false")
        private boolean isEmployerContribution;

        @Schema(description = "Is mandatory", example = "true")
        private boolean isMandatory;

        @Schema(description = "Allow employee override", example = "true")
        private boolean allowEmployeeOverride;

        @Schema(description = "Minimum value", example = "0.00")
        private BigDecimal minValue;

        @Schema(description = "Maximum value", example = "100000.00")
        private BigDecimal maxValue;

        @Schema(description = "Formula expression", example = "CTC * 0.5")
        private String formulaExpression;

        @Schema(description = "Effective from date", example = "2024-01-01")
        private LocalDate effectiveFrom;

        @Schema(description = "Effective to date", example = "2024-12-31")
        private LocalDate effectiveTo;

        @Schema(description = "Is active", example = "true")
        private boolean isActive;
    }
}