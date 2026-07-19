package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.EmployeeSalaryComponentRepository;
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
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    private final SalaryComponentCalculator salaryComponentCalculator;
    private final OvertimeCalculator overtimeCalculator;
    private final TdsCalculator tdsCalculator;
    private final LoanRecoveryCalculator loanRecoveryCalculator;
    private final ReimbursementCalculator reimbursementCalculator;
    private final PayslipGenerator payslipGenerator;
    private final StatutoryCalculator statutoryCalculator;

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
            Map<String, String> customComponentFormulas,
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
                    customComponentTypes, customComponentNames, customComponentFormulas);

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

        // PT and ESI calculations post-merge (Issue 9b)
        EmployeeSalaryProfile activeProfile = segments.get(segments.size() - 1).profile();
        calculatePtAndEsi(merged, employee, tenantConfig, orderedStructure, statutoryRates, ptSlabsByState,
                month, year, customComponentTypes, customComponentFormulas, activeProfile);

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
        BigDecimal nonRecurringGross = arrearsVal.add(bonusVal);
        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, tenantConfig.getTenant().getId(), regime, merged.getTaxableGrossEarnings(), nonRecurringGross, month, year);

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
        
        // Find the most recent change
        EmployeeSalaryProfile latest = profiles.get(profiles.size() - 1);
        EmployeeSalaryProfile previous = profiles.get(profiles.size() - 2);
        
        // Check if it's retrospective (effective date < current month start)
        if (latest.getEffectiveFrom().isAfter(monthStart)) {
            return BigDecimal.ZERO;
        }
        
        LocalDate effectiveDate = latest.getEffectiveFrom();
        BigDecimal ctcDiff = latest.getMonthlyCtc().subtract(previous.getMonthlyCtc());
        
        // Only positive arrears (salary increments)
        if (ctcDiff.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Check if arrears were already paid for this change
        if (latest.isArrearsPaid()) {
            return BigDecimal.ZERO;
        }
        
        long monthsDiff = java.time.temporal.ChronoUnit.MONTHS.between(
            effectiveDate.withDayOfMonth(1), 
            monthStart.withDayOfMonth(1)
        );
        
        if (monthsDiff <= 0) {
            return BigDecimal.ZERO;
        }
        
        latest.setArrearsPaid(true);
        employeeSalaryProfileRepo.save(latest);
        
        return ctcDiff.multiply(BigDecimal.valueOf(monthsDiff)).setScale(2, RoundingMode.HALF_EVEN);
    }

    private BigDecimal calculateMonthlyBonus(BigDecimal basicSalary, BigDecimal grossSalary, 
            int monthsWorkedInFY, BigDecimal bonusPercentage) {
        
        // Check eligibility: null basic or no service in FY disqualifies
        if (basicSalary == null) {
            return BigDecimal.ZERO;
        }
        
        // Bonus eligibility cap: basic exceeds ₹21,000 disqualifies
        if (basicSalary.compareTo(new BigDecimal("21000")) > 0) {
            return BigDecimal.ZERO;
        }
        
        // Monthly service eligibility: needs at least 1 month in FY (30+ days)
        if (monthsWorkedInFY < 1) {
            return BigDecimal.ZERO;
        }
        
        // Bonus calculation base per Payment of Bonus Act, 1965:
        // Capped at ₹21,000/month for eligibility, but computation on ₹7,000
        BigDecimal bonusBase = basicSalary.min(new BigDecimal("7000"));
        
        // Default rate: 8.33% (1/12th of 100% annual equivalent)
        BigDecimal rate = bonusPercentage != null ? bonusPercentage : new BigDecimal("8.33");
        BigDecimal bonusMonthly = bonusBase
            .multiply(rate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN))
            .setScale(2, RoundingMode.HALF_EVEN);
        
        // Already calculated as monthly amount (8.33% annually ÷ 12 months)
        return bonusMonthly;
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

    private void calculatePtAndEsi(
            PeriodPayData merged,
            Employee employee,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> orderedStructure,
            List<StatutoryRateConfig> statutoryRates,
            Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState,
            int month, int year,
            Map<String, String> customComponentTypes,
            Map<String, String> customComponentFormulas,
            EmployeeSalaryProfile activeProfile) {
        
        // ESI ceiling resolution
        BigDecimal esiCeiling = BigDecimal.valueOf(21000); // Default fallback
        for (StatutoryRateConfig rateConfig : statutoryRates) {
            if ("ESI_ER".equalsIgnoreCase(rateConfig.getComponentCode())) {
                if (rateConfig.getCeilingAmount() != null) {
                    esiCeiling = rateConfig.getCeilingAmount();
                    break;
                }
            }
        }

        // Calculate contribution period gross (Issue 9a: pass merged.getGrossEarnings())
        BigDecimal contributionPeriodGross = statutoryCalculator.getContributionPeriodStartGross(
                tenantConfig.getTenant().getId(), employee.getId(), year, month, merged.getGrossEarnings());
        merged.setContributionPeriodGross(contributionPeriodGross);

        // Variables map for SpEL evaluations
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("CTC", activeProfile.getMonthlyCtc());
        variables.put("BASIC", merged.getComponentValues().getOrDefault("BASIC", BigDecimal.ZERO));
        variables.put("WAGES_BASE", merged.getWagesBase());
        variables.put("GROSS", merged.getGrossEarnings());
        variables.put("LOP_DAYS", merged.getLopDays());
        variables.put("CONTRIBUTION_PERIOD_GROSS", contributionPeriodGross);

        for (StatutoryRateConfig rateConfig : statutoryRates) {
            variables.put(rateConfig.getComponentCode() + "_RATE", rateConfig.getRate());
            if (rateConfig.getCeilingAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CEILING", rateConfig.getCeilingAmount());
            }
            if (rateConfig.getCapAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CAP", rateConfig.getCapAmount());
            }
        }

        List<EmployeeSalaryComponent> overrides = employeeSalaryComponentRepo.findBySalaryProfileId(activeProfile.getId());

        for (TenantSalaryStructure item : orderedStructure) {
            String code = item.getComponentCode();
            if ("DEDUCTION".equalsIgnoreCase(salaryComponentCalculator.getComponentType(code, customComponentTypes))) {
                if ("ESI_EE".equalsIgnoreCase(code) || "ESI_ER".equalsIgnoreCase(code)) {
                    BigDecimal val = BigDecimal.ZERO;
                    if (tenantConfig.isEnableEsi() && contributionPeriodGross.compareTo(esiCeiling) <= 0) {
                        val = salaryComponentCalculator.calculateComponentValue(item, overrides, variables, customComponentFormulas);
                    }
                    merged.putComponentValue(code, val);
                    merged.putExpression(code, salaryComponentCalculator.getFormulaExpression(item, overrides, customComponentFormulas));
                    variables.put(code, val);
                    if (!salaryComponentCalculator.isEmployerContribution(code)) {
                        merged.setTotalDeductions(merged.getTotalDeductions().add(val));
                    }
                } else if ("PT_DEDUCTION".equalsIgnoreCase(code) || "PT".equalsIgnoreCase(code)) {
                    BigDecimal val = BigDecimal.ZERO;
                    if (tenantConfig.isEnablePt()) {
                        String workCountry = employee.getWorkCountry();
                        if (workCountry != null && !"IN".equalsIgnoreCase(workCountry)) {
                            val = BigDecimal.ZERO;
                        } else {
                            IndianState ptState = employee.getWorkState();
                            if (ptState == null && employee.getTenant() != null) {
                                ptState = employee.getTenant().getState();
                            }
                            
                            if (ptState == null) {
                                log.error("Employee ID {} has no work state configured; PT calculation cannot proceed", employee.getId());
                                throw new com.sonixhr.exceptions.BusinessException("PT_STATE_MISSING",
                                        "Employee must have a work state configured (work location). PT is calculated based on work location, not residence.");
                            }
                            val = statutoryCalculator.calculatePTAmount(ptState, merged.getGrossEarnings(), month, ptSlabsByState);
                        }
                    }
                    merged.putComponentValue(code, val);
                    merged.putExpression(code, "Professional Tax slab rate");
                    variables.put(code, val);
                    if (!salaryComponentCalculator.isEmployerContribution(code)) {
                        merged.setTotalDeductions(merged.getTotalDeductions().add(val));
                    }
                }
            }
        }
    }
}
