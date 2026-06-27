package com.sonixhr.dto.payroll;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating/updating salary component definition")
public class SalaryComponentDefinitionRequest {

    @NotBlank(message = "Component code is required")
    @Schema(description = "Component code (unique identifier)", example = "BASIC", requiredMode = Schema.RequiredMode.REQUIRED)
    private String componentCode;

    @NotBlank(message = "Component name is required")
    @Schema(description = "Component display name", example = "Basic Salary", requiredMode = Schema.RequiredMode.REQUIRED)
    private String componentName;

    @NotBlank(message = "Component type is required")
    @Schema(description = "Component type - EARNING or DEDUCTION", example = "EARNING", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"EARNING", "DEDUCTION"})
    private String componentType;

    @NotBlank(message = "Calculation type is required")
    @Schema(description = "Calculation type", example = "PERCENTAGE_OF_CTC", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"FIXED", "PERCENTAGE_OF_CTC", "PERCENTAGE_OF_BASIC", "FORMULA"})
    private String calculationType;

    @Schema(description = "Default value (amount or percentage)", example = "50.00")
    private BigDecimal defaultValue;

    @Schema(description = "Formula expression (for FORMULA calculation type)", example = "CTC * 0.5")
    private String formulaExpression;

    @Schema(description = "Evaluation order (for dependency resolution)", example = "1")
    private Integer evaluationOrder;

    @Schema(description = "Is LOP applicable to this component", example = "true")
    private Boolean isLopApplicable;

    @Schema(description = "Is employer contribution (doesn't reduce net pay)", example = "false")
    private Boolean isEmployerContribution;

    @Schema(description = "Is mandatory (cannot be deleted)", example = "true")
    private Boolean isMandatory;

    @Schema(description = "Allow employee override", example = "true")
    private Boolean allowEmployeeOverride;

    @Schema(description = "Is allowed by tenant", example = "true")
    private Boolean isAllowedByTenant;

    @Schema(description = "Minimum value (for validation)", example = "0.00")
    private BigDecimal minValue;

    @Schema(description = "Maximum value (for validation)", example = "100000.00")
    private BigDecimal maxValue;

    @Schema(description = "Effective from date", example = "2024-01-01")
    private LocalDate effectiveFrom;

    @Schema(description = "Effective to date", example = "2024-12-31")
    private LocalDate effectiveTo;

    // Helper methods for safe boolean access
    public boolean isLopApplicable() {
        return isLopApplicable != null && isLopApplicable;
    }

    public boolean isEmployerContribution() {
        return isEmployerContribution != null && isEmployerContribution;
    }

    public boolean isMandatory() {
        return isMandatory != null && isMandatory;
    }

    public boolean isAllowEmployeeOverride() {
        return allowEmployeeOverride != null && allowEmployeeOverride;
    }

    public boolean isAllowedByTenant() {
        return isAllowedByTenant != null && isAllowedByTenant;
    }
}