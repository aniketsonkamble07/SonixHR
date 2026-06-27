package com.sonixhr.dto.payroll;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAllowanceConfigDTO {
    private UUID id;
    private String componentCode;
    private String componentName;
    private Boolean isEnabled;
    private String calculationType;
    private BigDecimal fixedValue;
    private BigDecimal percentageValue;
    private String formulaExpression;
    private Boolean isMandatory;
    private Integer evaluationOrder;
    private Boolean isLopApplicable;
    private BigDecimal maxLimit;
    private BigDecimal minLimit;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
}
