package com.sonixhr.dto.payroll;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayslipSummaryResponse {
    private UUID id;
    private UUID payrunId;
    private Long employeeId;
    private String employeeCode;
    private String fullName;
    private Integer month;
    private Integer year;
    private BigDecimal grossEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
}
