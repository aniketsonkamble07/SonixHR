package com.sonixhr.dto.payroll;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class SalaryComponentDefinitionResponse {
    private UUID id;
    private String componentCode;
    private String componentName;
    private String componentType;
    private String calculationType;
    private BigDecimal defaultValue;
    private String formulaExpression;
    private Integer evaluationOrder;
    private boolean isLopApplicable;
    private boolean isEmployerContribution;
    private boolean isMandatory;
    private boolean allowEmployeeOverride;
    private boolean isAllowedByTenant;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private boolean isActive;
}