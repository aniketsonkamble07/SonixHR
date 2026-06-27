package com.sonixhr.dto.payroll;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollCalculationResponse {
    private BigDecimal ctc;
    private BigDecimal grossEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    private BigDecimal wagesBase;
    private BigDecimal totalEmployerContributions;
    private BigDecimal reconciledCtc;
    private Map<String, BigDecimal> components;
}
