package com.sonixhr.dto.payroll;

import com.sonixhr.entity.payroll.LopBasis;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EmployeeSalaryProfileResponse {
    private UUID id;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private Integer version;
    private BigDecimal monthlyCtc;
    private String currency;
    private String taxRegime;
    private LopBasis lopBasisOverride;
    private Integer workingDaysOverride;
    private String promotionReason;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private boolean isActive;
    private List<EmployeeComponentOverrideDTO> componentOverrides;
}