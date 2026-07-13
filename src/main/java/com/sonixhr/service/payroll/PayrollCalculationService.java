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

                // Compute each profile segment's pay.
                PeriodPayData merged = null;
                for (ProfileSegment seg : segments) {
                    BigDecimal segLopDays = (totalActiveDays > 0 && empLopDays.compareTo(BigDecimal.ZERO) > 0)
                            ? empLopDays.multiply(BigDecimal.valueOf(seg.activeDays()))
                                        .divide(BigDecimal.valueOf(totalActiveDays), 6, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    PeriodPayData segData = salaryComponentCalculator.computePeriodPayData(
                            seg.profile(), seg.start(), seg.end(), totalDaysInMonth,
                            tenantConfig, orderedStructure, statutoryRates, ptSlabsByState, segLopDays, month, year,
                            customComponentTypes, customComponentNames);

                    merged = (merged == null) ? segData : mergePeriodData(merged, segData);
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

                merged.getComponentValues().put("TDS", tds);
                merged.getExpressions().put("TDS", "Projected annual tax / remaining months");
                merged.setTotalDeductions(merged.getTotalDeductions().add(tds));

                // Post-merge loan recovery deduction (once per employee-month, post-segment-merge)
                BigDecimal loanRecovery = loanRecoveryCalculator.calculateMonthlyRecovery(employee, tenantConfig.getTenant().getId(), merged);
                if (loanRecovery == null) {
                    loanRecovery = BigDecimal.ZERO;
                }
                merged.getComponentValues().put("LOAN_EMI", loanRecovery);
                merged.getExpressions().put("LOAN_EMI", "Derived balance recovery");
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
        PeriodPayData merged = new PeriodPayData();

        // Component values: sum per component code across both segments
        merged.getComponentValues().putAll(a.getComponentValues());
        b.getComponentValues().forEach((code, val) ->
            merged.getComponentValues().merge(code, val, BigDecimal::add));

        // Expressions: last segment wins for the same key
        merged.getExpressions().putAll(a.getExpressions());
        merged.getExpressions().putAll(b.getExpressions());

        merged.setGrossEarnings(a.getGrossEarnings().add(b.getGrossEarnings()));
        merged.setTotalDeductions(a.getTotalDeductions().add(b.getTotalDeductions()));
        merged.setLopDays(a.getLopDays().add(b.getLopDays()));
        merged.setTaxableGrossEarnings(a.getTaxableGrossEarnings().add(b.getTaxableGrossEarnings()));

        // Most-recent segment wins for point-in-time fields
        merged.setWagesBase(b.getWagesBase());
        merged.setContributionPeriodGross(b.getContributionPeriodGross());

        return merged;
    }
}
