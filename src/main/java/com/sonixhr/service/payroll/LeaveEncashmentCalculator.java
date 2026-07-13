package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LeaveEncashmentCalculator {

    @Value
    public static class LeaveEncashmentResult {
        BigDecimal actualAmount;
        BigDecimal exemptAmount;
        BigDecimal taxableAmount;
    }

    public LeaveEncashmentResult calculateEncashment(
            Employee employee,
            BigDecimal basicSalary,
            BigDecimal perDayRate,
            int earnedLeaveDays,
            int yearsOfService,
            BigDecimal avgMonthlySalaryLast10Months) {

        BigDecimal actualAmount = perDayRate.multiply(BigDecimal.valueOf(earnedLeaveDays));

        int cappedLeaveDays = Math.min(earnedLeaveDays, yearsOfService * 30); // 30 days/year of service cap
        BigDecimal formulaCapAmount = perDayRate.multiply(BigDecimal.valueOf(cappedLeaveDays));

        BigDecimal tenMonthsSalary = avgMonthlySalaryLast10Months.multiply(BigDecimal.valueOf(10));
        BigDecimal statutoryCeiling = BigDecimal.valueOf(2500000); // Sec 10(10AA) current limit

        BigDecimal exemptAmount = actualAmount.min(formulaCapAmount).min(tenMonthsSalary).min(statutoryCeiling);
        BigDecimal taxableAmount = actualAmount.subtract(exemptAmount).max(BigDecimal.ZERO);

        return new LeaveEncashmentResult(actualAmount, exemptAmount, taxableAmount);
    }
}
