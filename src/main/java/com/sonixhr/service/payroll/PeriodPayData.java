package com.sonixhr.service.payroll;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class PeriodPayData {
    private final Map<String, BigDecimal> componentValues = new LinkedHashMap<>();
    private final Map<String, String> expressions = new LinkedHashMap<>();
    
    private BigDecimal grossEarnings = BigDecimal.ZERO;
    private BigDecimal totalDeductions = BigDecimal.ZERO;
    private BigDecimal lopDays = BigDecimal.ZERO;
    private BigDecimal wagesBase = BigDecimal.ZERO;
    private BigDecimal contributionPeriodGross = BigDecimal.ZERO;
    private BigDecimal overtimeHours = BigDecimal.ZERO;
    private BigDecimal overtimeRate = BigDecimal.ZERO;
    private BigDecimal overtimePay = BigDecimal.ZERO;
    private BigDecimal taxableGrossEarnings = BigDecimal.ZERO;
}
