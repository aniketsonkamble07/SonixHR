package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

public class LeaveEncashmentCalculatorTest {

    private final LeaveEncashmentCalculator calculator = new LeaveEncashmentCalculator();

    @Test
    public void testLeaveEncashment_Standard() {
        Employee employee = new Employee();
        BigDecimal basicSalary = BigDecimal.valueOf(60000);
        BigDecimal perDayRate = BigDecimal.valueOf(2000);
        int earnedLeaveDays = 15;
        int yearsOfService = 5;
        BigDecimal avgMonthlySalaryLast10Months = BigDecimal.valueOf(60000);

        LeaveEncashmentCalculator.LeaveEncashmentResult result = calculator.calculateEncashment(
                employee, basicSalary, perDayRate, earnedLeaveDays, yearsOfService, avgMonthlySalaryLast10Months);

        assertTrue(new BigDecimal("30000").compareTo(result.getActualAmount()) == 0);
        assertTrue(new BigDecimal("30000").compareTo(result.getExemptAmount()) == 0);
        assertTrue(BigDecimal.ZERO.compareTo(result.getTaxableAmount()) == 0);
    }

    @Test
    public void testLeaveEncashment_ExceedCappedLeaveDays() {
        Employee employee = new Employee();
        BigDecimal basicSalary = BigDecimal.valueOf(60000);
        BigDecimal perDayRate = BigDecimal.valueOf(2000);
        int earnedLeaveDays = 40;
        int yearsOfService = 1; // max capped leaves = 30 days
        BigDecimal avgMonthlySalaryLast10Months = BigDecimal.valueOf(60000);

        LeaveEncashmentCalculator.LeaveEncashmentResult result = calculator.calculateEncashment(
                employee, basicSalary, perDayRate, earnedLeaveDays, yearsOfService, avgMonthlySalaryLast10Months);

        assertTrue(new BigDecimal("80000").compareTo(result.getActualAmount()) == 0);
        assertTrue(new BigDecimal("60000").compareTo(result.getExemptAmount()) == 0);
        assertTrue(new BigDecimal("20000").compareTo(result.getTaxableAmount()) == 0);
    }
}
