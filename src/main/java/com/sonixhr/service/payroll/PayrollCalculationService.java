package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.exceptions.PayrunLockedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PayrollCalculationService {

    private final StatutoryRateConfigRepository statutoryRateConfigRepo;
    private final StateProfessionalTaxConfigRepository statePtConfigRepo;
    private final TenantPayrollConfigRepository tenantPayrollConfigRepo;
    private final TenantSalaryStructureRepository tenantSalaryStructureRepo;
    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    private final PayrunRepository payrunRepo;
    private final PayrunConfigRepository payrunConfigRepo;
    private final PayslipRepository payslipRepo;
    private final PayslipItemRepository payslipItemRepo;
    private final SalaryComponentDefinitionRepository componentDefinitionRepo;
    
    private final LeaveCalculator leaveCalculator;
    private final OvertimeCalculator overtimeCalculator;
    private final SnapshotService snapshotService;
    private final StatutoryCalculator statutoryCalculator;
    private final SalaryComponentCalculator salaryComponentCalculator;
    private final PayslipGenerator payslipGenerator;
    private final TdsCalculator tdsCalculator;
    private final LoanRecoveryCalculator loanRecoveryCalculator;
    private final ReimbursementCalculator reimbursementCalculator;
    private final com.sonixhr.service.common.AuditLogService auditLogService;

    /** Pairs a salary profile with its effective date segment within the payrun month. */
    private record ProfileSegment(
            EmployeeSalaryProfile profile,
            LocalDate start,
            LocalDate end,
            int activeDays) {}

    /**
     * Executes a full payrun calculation for a tenant in a specific month and year.
     */
    @Transactional
    public Payrun processPayrun(Long tenantId, int month, int year) {
        // 1. Ensure payrun doesn't already exist or isn't approved
        Optional<Payrun> existingPayrun = payrunRepo.findByTenantAndMonthAndYear(tenantId, month, year);
        if (existingPayrun.isPresent()) {
            Payrun oldPayrun = existingPayrun.get();
            if ("APPROVED".equalsIgnoreCase(oldPayrun.getStatus())
                    || "PAID".equalsIgnoreCase(oldPayrun.getStatus())) {
                throw new PayrunLockedException("Payrun is already locked and cannot be reprocessed.");
            }
            // Preserve the previous draft by marking it as SUPERSEDED
            oldPayrun.setStatus("SUPERSEDED");
            payrunRepo.save(oldPayrun);
            payrunRepo.flush();
        }

        // Determine next version number
        Integer latestVersion = payrunRepo.findLatestVersionNumber(tenantId, month, year);
        Integer nextVersion = (latestVersion == null) ? 1 : latestVersion + 1;

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd   = YearMonth.of(year, month).atEndOfMonth();

        // 2. Fetch tenant settings and structure
        TenantPayrollConfig tenantConfig = tenantPayrollConfigRepo.findActiveByTenantAndDate(tenantId, monthStart)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant payroll configuration not found for date: " + monthStart));

        List<TenantSalaryStructure> salaryStructure = tenantSalaryStructureRepo
                .findActiveByTenantAndDate(tenantId, monthStart);
        if (salaryStructure.isEmpty()) {
            throw new IllegalArgumentException("Tenant salary structure not defined or active.");
        }

        // Pre-sort structure by evaluation order once — reused across all per-employee calls
        List<TenantSalaryStructure> orderedStructure = new ArrayList<>(salaryStructure);
        orderedStructure.sort(Comparator.comparingInt(TenantSalaryStructure::getEvaluationOrder));

        // 3. Fetch statutory rules and professional tax slabs
        List<StatutoryRateConfig> statutoryRates = statutoryRateConfigRepo.findActiveByDate(monthStart);
        List<StateProfessionalTaxConfig> ptSlabs  = statePtConfigRepo.findAll();

        // Optimize PT lookup by pre-grouping professional tax slabs by state
        Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState = ptSlabs.stream()
                .filter(s -> s.getStateCode() != null)
                .collect(Collectors.groupingBy(StateProfessionalTaxConfig::getStateCode));

        // 4. Create new Payrun version record
        Payrun payrun = Payrun.builder()
                .tenant(tenantConfig.getTenant())
                .month(month)
                .year(year)
                .status("DRAFT")
                .version(nextVersion)
                .build();
        payrun.setProcessedAt(LocalDateTime.now());
        payrun = payrunRepo.save(payrun);

        // 5. Create PayrunConfig snapshot (frozen config JSONs)
        snapshotService.createPayrunSnapshot(payrun, statutoryRates, tenantConfig, salaryStructure, ptSlabs);

        // 6. Fetch active profiles overlapping this month.
        List<EmployeeSalaryProfile> allProfiles = employeeSalaryProfileRepo
                .findActiveProfilesByTenantInPeriod(tenantId, monthStart, monthEnd);

        Map<Long, List<EmployeeSalaryProfile>> profilesByEmployee = allProfiles.stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getId()));

        // Compute LOP days from approved unpaid leaves using LeaveCalculator
        Map<Long, BigDecimal> finalLopDays = leaveCalculator.calculateUnpaidLeaveDaysForTenant(tenantId, month, year);

        // Fetch custom component definitions once for O(1) type and name lookup
        List<SalaryComponentDefinition> componentDefs = componentDefinitionRepo.findAllowedByTenantAndDate(tenantId, monthStart);
        Map<String, String> customComponentTypes = componentDefs.stream()
                .collect(Collectors.toMap(
                        d -> d.getComponentCode().toUpperCase(),
                        d -> d.getComponentType().toUpperCase(),
                        (a, b) -> a
                ));
        Map<String, String> customComponentNames = componentDefs.stream()
                .collect(Collectors.toMap(
                        d -> d.getComponentCode().toUpperCase(),
                        d -> d.getComponentName(),
                        (a, b) -> a
                ));

        int totalDaysInMonth = YearMonth.of(year, month).lengthOfMonth();

        // 7. Process each employee
        List<String> payrunErrors = new ArrayList<>();
        for (Map.Entry<Long, List<EmployeeSalaryProfile>> entry : profilesByEmployee.entrySet()) {
            List<EmployeeSalaryProfile> profiles = entry.getValue();
            profiles.sort(Comparator.comparing(EmployeeSalaryProfile::getEffectiveFrom));

            BigDecimal empLopDays = finalLopDays.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            Employee employee = profiles.get(0).getEmployee();

            try {
                // Build profile segments for this month
                List<ProfileSegment> segments = new ArrayList<>();
                int totalActiveDays = 0;

                for (int i = 0; i < profiles.size(); i++) {
                    EmployeeSalaryProfile prof = profiles.get(i);
                    LocalDate segStart = prof.getEffectiveFrom().isBefore(monthStart)
                            ? monthStart : prof.getEffectiveFrom();
                    LocalDate segEnd;
                    if (i < profiles.size() - 1) {
                        segEnd = profiles.get(i + 1).getEffectiveFrom().minusDays(1);
                        if (segEnd.isAfter(monthEnd)) segEnd = monthEnd;
                    } else {
                        segEnd = (prof.getEffectiveTo() != null && prof.getEffectiveTo().isBefore(monthEnd))
                                ? prof.getEffectiveTo() : monthEnd;
                    }
                    if (segStart.isAfter(segEnd)) continue;

                    int segDays = (int) java.time.temporal.ChronoUnit.DAYS.between(segStart, segEnd) + 1;
                    totalActiveDays += segDays;
                    segments.add(new ProfileSegment(prof, segStart, segEnd, segDays));
                }

                if (segments.isEmpty()) continue;

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

            } catch (Exception e) {
                log.error("Failed to calculate payslip for employee ID: {}", employee.getId(), e);
                payrunErrors.add("Employee ID " + employee.getId() + ": " + e.getMessage());
            }
        }

        if (!payrunErrors.isEmpty()) {
            log.warn("Payrun processed with warnings/errors: {}", payrunErrors);
            throw new RuntimeException("Payrun errors: " + String.join("; ", payrunErrors));
        }

        auditLogService.log(
            tenantConfig.getTenant(),
            "PAYROLL_PROCESSED",
            "payrunStatus",
            null,
            payrun.getStatus(),
            "{\"month\":" + month + ",\"year\":" + year + ",\"payrunId\":\"" + payrun.getId() + "\"}"
        );

        return payrun;
    }

    /**
     * Merges two PeriodPayData objects (from consecutive salary profile segments).
     */
    private PeriodPayData mergePeriodData(PeriodPayData a, PeriodPayData b) {
        if (a == null) return b;
        if (b == null) return a;
        
        PeriodPayData merged = new PeriodPayData();
        merged.merge(a);
        merged.merge(b);
        
        return merged;
    }

    /**
     * Calculate arrears from retrospective salary revisions
     */
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

    /**
     * Calculate statutory bonus under Payment of Bonus Act, 1965
     */
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
