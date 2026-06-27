package com.sonixhr.dto.payroll;

import com.sonixhr.enums.IndianState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollCalculationRequest {
    private BigDecimal ctc;
    private IndianState state;
    private int month;
    private int year;
    private BigDecimal lopDays;
    private boolean compliantMode;
    private boolean pfCapping;
    private BigDecimal esiPeriodStartGross;
}
