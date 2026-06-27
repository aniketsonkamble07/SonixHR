package com.sonixhr.dto.payroll;

import com.sonixhr.entity.payroll.LopBasis;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class EmployeeSalaryProfileRequest {
    private Long employeeId;
    private BigDecimal monthlyCtc;
    private String currency;
    private String taxRegime;
    private LopBasis lopBasisOverride;
    private Integer workingDaysOverride;
    private String promotionReason;
    private List<EmployeeComponentOverrideDTO> componentOverrides;
    private LocalDate effectiveFrom;
    private Long modifiedBy;
}