package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.EmployeeSalaryProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeePayrunProcessor {

    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    private final SalaryComponentCalculator salaryComponentCalculator;
    private final OvertimeCalculator overtimeCalculator;
    private final TdsCalculator tdsCalculator;
    private final LoanRecoveryCalculator loanRecoveryCalculator;
    private final ReimbursementCalculator reimbursementCalculator;
    private final PayslipGenerator payslipGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processEmployee(
            Payrun payrun,
            Employee employee,
            List<ProfileSegment> segments,
            int totalDaysInMonth,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> orderedStructure,
            List<StatutoryRateConfig> statutoryRates,
            Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState,
            BigDecimal empLopDays,
            int month, int year,
            Map<String, String> customComponentTypes,
            Map<String, String> customComponentNames,
            LocalDate monthStart,
            LocalDate monthEnd) {

        // Distribute LOP days starting from the first segment
        BigDecimal remainingLopDays = empLopDays;
        PeriodPayData merged = null;
        for (ProfileSegment seg : segments) {
            BigDecimal segLopDays = BigDecimal.ZERO;
            if (remainingLopDays.compareTo(BigDecimal.ZERO) > 0) {
                // Apply LOP to this segment, but not exceeding segment days
                segLopDays = remainingLopDays.min(BigDecimal.valueOf(seg.activeDays()));
                remainingLopDays = remainingLopDays.subtract(segLopDays);
            }

            PeriodPayData segData = salaryComponentCalculator.computePeriodPayData(
                    seg.profile(), seg.start(), seg.end(), totalDaysInMonth,
                    tenantConfig, orderedStructure, statutoryRates, ptSlabsByState, segLopDays, month, year,
                    customComponentTypes, customComponentNames);

            merged = (merged == null) ? segData : mergePeriodData(merged, segData);
        }

        // Compute arrears
        BigDecimal arrearsVal = calculateArrears(employee.getId(), tenantConfig.getTenant().getId(), monthStart);
        merged.setArrears(arrearsVal);
        if (arrearsVal.compareTo(BigDecimal.ZERO) > 0) {
            merged.putComponentValue("ARREARS", arrearsVal);
            merged.putExpression("ARREARS", "Retrospective salary revision arrears");
            merged.setGrossEarnings(merged.getGrossEarnings().add(arrearsVal));
            merged.setTaxableGrossEarnings(merged.getTaxableGrossEarnings().add(arrearsVal));
        }

        // Compute bonus (Payment of Bonus Act, 1965)
        int monthsWorkedInFY = getMonthsWorkedInFY(employee.getHireDate(), monthStart);
        BigDecimal basic = merged.getComponentValues().getOrDefault("BASIC", BigDecimal.ZERO);
        BigDecimal bonusVal = calculateMonthlyBonus(basic, merged.getGrossEarnings(), monthsWorkedInFY, null);
        merged.setBonus(bonusVal);
        if (bonusVal.compareTo(BigDecimal.ZERO) > 0) {
            merged.putComponentValue("BONUS", bonusVal);
            merged.putExpression("BONUS", "Statutory bonus (Payment of Bonus Act, 1965)");
            merged.setGrossEarnings(merged.getGrossEarnings().add(bonusVal));
            merged.setTaxableGrossEarnings(merged.getTaxableGrossEarnings().add(bonusVal));
        }

        // Compute overtime once per employee using OvertimeCalculator
        overtimeCalculator.calculateOvertime(employee, tenantConfig, monthStart, monthEnd, merged);

        // Resolve governing tax regime (last segment wins, fallback to tenant default)
        String regimeStr = segments.get(segments.size() - 1).profile().getTaxRegime();
        if (regimeStr == null) {
            regimeStr = tenantConfig.getDefaultTaxRegime();
        }
        com.sonixhr.enums.payroll.TaxRegime regime = com.sonixhr.enums.payroll.TaxRegime.NEW_REGIME;
        if (regimeStr != null && regimeStr.toUpperCase().contains("OLD")) {
            regime = com.sonixhr.enums.payroll.TaxRegime.OLD_REGIME;
        }

        // Compute TDS once per employee-month
        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, tenantConfig.getTenant().getId(), regime, merged.getTaxableGrossEarnings(), month, year);

        merged.putComponentValue("TDS", tds);
        merged.putExpression("TDS", "Projected annual tax / remaining months");
        merged.setTotalDeductions(merged.getTotalDeductions().add(tds));

        // Post-merge loan recovery deduction (once per employee-month, post-segment-merge)
        BigDecimal loanRecovery = loanRecoveryCalculator.calculateMonthlyRecovery(employee, tenantConfig.getTenant().getId(), merged);
        if (loanRecovery == null) {
            loanRecovery = BigDecimal.ZERO;
        }
        merged.putComponentValue("LOAN_EMI", loanRecovery);
        merged.putExpression("LOAN_EMI", "Derived balance recovery");
        merged.setTotalDeductions(merged.getTotalDeductions().add(loanRecovery));

        // Post-merge reimbursements calculation (does not affect gross earnings or deductions base)
        reimbursementCalculator.calculateReimbursements(employee, tenantConfig.getTenant().getId(), month, year, merged);

        // Persist the payslip and items
        payslipGenerator.persistPayslip(payrun, employee, tenantConfig, merged, orderedStructure, customComponentTypes, customComponentNames);
    }

    private PeriodPayData mergePeriodData(PeriodPayData a, PeriodPayData b) {
        if (a == null) return b;
        if (b == null) return a;
        
        PeriodPayData merged = new PeriodPayData();
        merged.merge(a);
        merged.merge(b);
        
        return merged;
    }

    private BigDecimal calculateArrears(Long employeeId, Long tenantId, LocalDate monthStart) {
        // Get all salary profile changes ordered by effective date
        List<EmployeeSalaryProfile> allProfiles = employeeSalaryProfileRepo
                .findByEmployeeIdOrderByEffectiveFromAsc(employeeId);
        
        List<EmployeeSalaryProfile> profiles = allProfiles.stream()
                .filter(p -> p.getEffectiveFrom().isBefore(monthStart) || p.getEffectiveFrom().isEqual(monthStart))
                .collect(Collectors.toList());
        
        if (profiles.size() < 2) {
            return BigDecimal.ZERO;
        }
        
        // Find the most recent change that was retrospective
        EmployeeSalaryProfile latest = profiles.get(profiles.size() - 1);
        EmployeeSalaryProfile previous = profiles.get(profiles.size() - 2);
        
        // Check if it's retrospective (effective date < current month start)
        if (latest.getEffectiveFrom().isAfter(monthStart)) {
            return BigDecimal.ZERO;
        }
        
        // Calculate difference for the months from effective date to current
        LocalDate effectiveDate = latest.getEffectiveFrom();
        BigDecimal ctcDiff = latest.getMonthlyCtc().subtract(previous.getMonthlyCtc());
        
        // Only positive arrears (increments)
        if (ctcDiff.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Calculate months between effective date and current month
        long monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(
            effectiveDate.withDayOfMonth(1), 
            monthStart.withDayOfMonth(1)
        );
        
        return ctcDiff.multiply(BigDecimal.valueOf(monthsDiff))
            .setScale(2, RoundingMode.HALF_EVEN);
    }

    private BigDecimal calculateMonthlyBonus(BigDecimal basicSalary, BigDecimal grossSalary, 
            int monthsWorkedInFY, BigDecimal bonusPercentage) {
        
        // Check eligibility
        if (basicSalary == null || basicSalary.compareTo(new BigDecimal("21000")) > 0) {
            return BigDecimal.ZERO;
        }
        
        // Monthly service eligibility: needs 30 days in FY
        if (monthsWorkedInFY < 1) {
            return BigDecimal.ZERO;
        }
        
        // Bonus calculation base is capped at ₹7,000 per month
        BigDecimal bonusBase = basicSalary.min(new BigDecimal("7000"));
        
        // Default minimum rate: 8.33% (1/12th of 100%)
        BigDecimal rate = bonusPercentage != null ? bonusPercentage : new BigDecimal("8.33");
        BigDecimal bonusMonthly = bonusBase
            .multiply(rate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN))
            .setScale(2, RoundingMode.HALF_EVEN);
        
        // For monthly proration
        return bonusMonthly.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_EVEN);
    }

    private int getMonthsWorkedInFY(LocalDate hireDate, LocalDate monthStart) {
        if (hireDate == null) {
            return 12;
        }
        int month = monthStart.getMonthValue();
        int year = monthStart.getYear();
        int fyStartYear = (month >= 4) ? year : year - 1;
        LocalDate fyStart = LocalDate.of(fyStartYear, 4, 1);
        
        LocalDate start = hireDate.isBefore(fyStart) ? fyStart : hireDate;
        if (start.isAfter(monthStart)) {
            return 0;
        }
        
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
            start.withDayOfMonth(1),
            monthStart.withDayOfMonth(1)
        ) + 1; // include current month
        
        return (int) months;
    }
}
