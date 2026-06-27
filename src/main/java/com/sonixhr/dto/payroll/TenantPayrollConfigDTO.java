package com.sonixhr.dto.payroll;

import com.sonixhr.entity.payroll.LopBasis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPayrollConfigDTO {
    private UUID id;
    private Long tenantId;
    private LopBasis lopBasis;
    private Integer workingDaysPerMonth;
    private boolean enablePfCapping;
    private boolean enableEsi;
    private boolean enablePt;
    private boolean enforceNewLabourCodes;
    private boolean enableOvertime;
    private BigDecimal overtimeRatePerHour;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private List<TenantPayrollConfigResponse.SalaryStructureResponse> salaryStructures;
}
