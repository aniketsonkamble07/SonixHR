package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

public class GratuityCalculatorTest {

    private final GratuityCalculator calculator = new GratuityCalculator();

    @Test
    public void testGratuity_Ineligible_LessThanFiveYears() {
        Employee employee = new Employee();
        employee.setHireDate(LocalDate.of(2023, 1, 1));
        LocalDate terminationDateIneligible = LocalDate.of(2026, 5, 1); // 3 years 4 months -> rounded to 3 years.

        GratuityCalculator.GratuityResult result = calculator.calculateGratuity(employee, BigDecimal.valueOf(50000), terminationDateIneligible);
        assertFalse(result.isEligible());
        assertEquals(BigDecimal.ZERO, result.getGratuityAmount());
    }

    @Test
    public void testGratuity_Eligible_RoundedService() {
        Employee employee = new Employee();
        employee.setHireDate(LocalDate.of(2020, 1, 1));
        LocalDate terminationDate = LocalDate.of(2025, 7, 1); // 5 years 6 months -> rounded to 6 years of service.

        GratuityCalculator.GratuityResult result = calculator.calculateGratuity(employee, BigDecimal.valueOf(50000), terminationDate);
        assertTrue(result.isEligible());
        assertTrue(new BigDecimal("173076.92").compareTo(result.getGratuityAmount()) == 0);
        assertTrue(new BigDecimal("173076.92").compareTo(result.getExemptAmount()) == 0);
        assertTrue(BigDecimal.ZERO.compareTo(result.getTaxableAmount()) == 0);
    }

    @Test
    public void testGratuity_ExceedExemptionLimit() {
        Employee employee = new Employee();
        employee.setHireDate(LocalDate.of(2000, 1, 1));
        LocalDate terminationDate = LocalDate.of(2025, 1, 1); // 25 years

        GratuityCalculator.GratuityResult result = calculator.calculateGratuity(employee, BigDecimal.valueOf(250000), terminationDate);
        assertTrue(result.isEligible());
        assertTrue(new BigDecimal("3605769.23").compareTo(result.getGratuityAmount()) == 0);
        assertTrue(new BigDecimal("2000000").compareTo(result.getExemptAmount()) == 0);
        assertTrue(new BigDecimal("1605769.23").compareTo(result.getTaxableAmount()) == 0);
    }
}
