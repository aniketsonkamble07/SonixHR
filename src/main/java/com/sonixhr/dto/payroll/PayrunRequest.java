package com.sonixhr.dto.payroll;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class PayrunRequest {
    private int month;
    private int year;
    private Map<Long, BigDecimal> employeeLopDays;
}