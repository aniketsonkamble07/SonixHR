package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import lombok.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class GratuityCalculator {

    @Value
    public static class GratuityResult {
        BigDecimal gratuityAmount;
        BigDecimal exemptAmount;
        BigDecimal taxableAmount;
        boolean eligible;

        public static GratuityResult notEligible() {
            return new GratuityResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
    }

    public GratuityResult calculateGratuity(Employee employee, BigDecimal lastDrawnBasic, LocalDate terminationDate) {
        if (employee.getHireDate() == null || terminationDate == null) {
            return GratuityResult.notEligible();
        }

        long fullYears = ChronoUnit.YEARS.between(employee.getHireDate(), terminationDate);
        long remainingMonths = ChronoUnit.MONTHS.between(
                employee.getHireDate().plusYears(fullYears), terminationDate);
        long yearsOfService = fullYears + (remainingMonths >= 6 ? 1 : 0); // 6-month rounding rule

        if (yearsOfService < 5) {
            return GratuityResult.notEligible(); // Payment of Gratuity Act minimum service requirement
        }

        BigDecimal gratuityAmount = lastDrawnBasic
                .multiply(BigDecimal.valueOf(15))
                .multiply(BigDecimal.valueOf(yearsOfService))
                .divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);

        BigDecimal exemptionCap = BigDecimal.valueOf(2000000); // Sec 10(10)
        BigDecimal exemptAmount = gratuityAmount.min(exemptionCap);
        BigDecimal taxableAmount = gratuityAmount.subtract(exemptAmount).max(BigDecimal.ZERO);

        return new GratuityResult(gratuityAmount, exemptAmount, taxableAmount, true);
    }
}
