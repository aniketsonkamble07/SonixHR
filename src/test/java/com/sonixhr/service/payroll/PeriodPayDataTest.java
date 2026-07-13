package com.sonixhr.service.payroll;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class PeriodPayDataTest {

    @Test
    public void testMergePeriodPayData() {
        PeriodPayData a = new PeriodPayData();
        a.putComponentValue("BASIC", BigDecimal.valueOf(30000));
        a.putLoanRecovery("LOAN_01", BigDecimal.valueOf(1000));
        a.setGrossEarnings(BigDecimal.valueOf(30000));
        a.setOvertimeHours(BigDecimal.valueOf(5));
        a.setOvertimePay(BigDecimal.valueOf(500));

        a.setArrears(BigDecimal.valueOf(5000));
        a.setBonus(BigDecimal.valueOf(250));

        PeriodPayData b = new PeriodPayData();
        b.putComponentValue("BASIC", BigDecimal.valueOf(20000));
        b.putLoanRecovery("LOAN_01", BigDecimal.valueOf(1000));
        b.putLoanRecovery("LOAN_02", BigDecimal.valueOf(1500));
        b.setGrossEarnings(BigDecimal.valueOf(20000));
        b.setOvertimeHours(BigDecimal.valueOf(3));
        b.setOvertimePay(BigDecimal.valueOf(300));
        b.setWagesBase(BigDecimal.valueOf(20000));
        b.setArrears(BigDecimal.valueOf(3000));
        b.setBonus(BigDecimal.valueOf(150));

        // Merge b into a
        a.merge(b);

        assertEquals(BigDecimal.valueOf(50000).setScale(2), a.getComponentValues().get("BASIC"));
        assertEquals(BigDecimal.valueOf(2000).setScale(2), a.getLoanRecoveryBreakdown().get("LOAN_01"));
        assertEquals(BigDecimal.valueOf(1500).setScale(2), a.getLoanRecoveryBreakdown().get("LOAN_02"));
        assertEquals(BigDecimal.valueOf(50000), a.getGrossEarnings());
        assertEquals(BigDecimal.valueOf(8), a.getOvertimeHours());
        assertEquals(BigDecimal.valueOf(800), a.getOvertimePay());
        assertEquals(BigDecimal.valueOf(20000), a.getWagesBase()); // point-in-time from b
        assertEquals(BigDecimal.valueOf(8000), a.getArrears());
        assertEquals(BigDecimal.valueOf(400), a.getBonus());
    }
}
