package com.sonixhr.service.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.exceptions.PayrunLockedException;
import com.sonixhr.exceptions.TechnicalException;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.enums.leave.LeaveType;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

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
    private final EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    private final PayrunRepository payrunRepo;
    private final PayrunConfigRepository payrunConfigRepo;
    private final PayslipRepository payslipRepo;
    private final PayslipItemRepository payslipItemRepo;
    private final SandboxedSpELEngine spelEngine;
    private final ObjectMapper objectMapper;
    private final LeaveRequestRepository leaveRequestRepo;
    private final TenantLeaveSettingsRepository tenantLeaveSettingsRepo;
    private final PublicHolidayRepository publicHolidayRepo;
    private final com.sonixhr.service.leave.LeaveConfigurationService leaveConfigService;
    private final ManualAttendanceRepository manualAttendanceRepo;
    private final com.sonixhr.service.common.AuditLogService auditLogService;

    /**
     * Internal holder for one salary-profile-period's computed payroll data.
     * Accumulated across profile segments and merged before persistence.
     */
    private static class PeriodPayData {
        final Map<String, BigDecimal> componentValues = new LinkedHashMap<>();
        final Map<String, String>     expressions     = new LinkedHashMap<>();
        BigDecimal grossEarnings           = BigDecimal.ZERO;
        BigDecimal totalDeductions         = BigDecimal.ZERO;
        BigDecimal lopDays                 = BigDecimal.ZERO;
        BigDecimal wagesBase               = BigDecimal.ZERO;
        BigDecimal contributionPeriodGross = BigDecimal.ZERO;
        BigDecimal overtimeHours           = BigDecimal.ZERO;
        BigDecimal overtimeRate            = BigDecimal.ZERO;
        BigDecimal overtimePay             = BigDecimal.ZERO;
    }

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
            if ("APPROVED".equalsIgnoreCase(existingPayrun.get().getStatus())
                    || "PAID".equalsIgnoreCase(existingPayrun.get().getStatus())) {
                throw new PayrunLockedException("Payrun is already locked and cannot be reprocessed.");
            }
            // If draft, clean up old payslips first
            List<Payslip> oldPayslips = payslipRepo.findByPayrunId(existingPayrun.get().getId());
            for (Payslip oldPayslip : oldPayslips) {
                List<PayslipItem> items = payslipItemRepo.findByPayslipId(oldPayslip.getId());
                payslipItemRepo.deleteAll(items);
            }
            payslipRepo.deleteAll(oldPayslips);
            payrunConfigRepo.findByPayrunId(existingPayrun.get().getId()).ifPresent(payrunConfigRepo::delete);
            payslipItemRepo.flush();
            payslipRepo.flush();
            payrunConfigRepo.flush();
        }

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

        // 4. Create or update Payrun record
        Payrun payrun = existingPayrun.orElseGet(() -> Payrun.builder()
                .tenant(tenantConfig.getTenant())
                .month(month)
                .year(year)
                .status("DRAFT")
                .build());
        payrun.setProcessedAt(LocalDateTime.now());
        payrun = payrunRepo.save(payrun);

        // 5. Create PayrunConfig snapshot (frozen config JSONs)
        try {
            PayrunConfig snapshot = PayrunConfig.builder()
                    .payrunId(payrun.getId())
                    .statutoryRatesJson(objectMapper.writeValueAsString(statutoryRates))
                    .tenantSettingsJson(objectMapper.writeValueAsString(tenantConfig))
                    .tenantStructureJson(objectMapper.writeValueAsString(salaryStructure))
                    .ptSlabsJson(objectMapper.writeValueAsString(ptSlabs))
                    .snapshotCreatedAt(LocalDateTime.now())
                    .build();
            payrunConfigRepo.save(snapshot);
        } catch (Exception e) {
            throw new TechnicalException("TECH_PAYRUN_SNAPSHOT", "Payrun processing failed",
                    "Failed to serialize payrun configurations for snapshot", e);
        }

        // 6. Fetch active profiles overlapping this month.
        // Bug 2 fix: group by employee to handle mid-month salary changes (multiple profiles per employee).
        // Previously only the latest profile was used, silently dropping pay from the earlier profile.
        List<EmployeeSalaryProfile> allProfiles = employeeSalaryProfileRepo
                .findActiveProfilesByTenantInPeriod(tenantId, monthStart, monthEnd);

        Map<Long, List<EmployeeSalaryProfile>> profilesByEmployee = allProfiles.stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getId()));

        // Compute LOP days from approved unpaid leaves
        Map<Long, BigDecimal> finalLopDays = calculateUnpaidLeaveDaysForTenant(tenantId, month, year);

        int totalDaysInMonth = YearMonth.of(year, month).lengthOfMonth();

        // 7. Process each employee
        List<String> payrunErrors = new ArrayList<>();
        for (Map.Entry<Long, List<EmployeeSalaryProfile>> entry : profilesByEmployee.entrySet()) {
            List<EmployeeSalaryProfile> profiles = entry.getValue();
            profiles.sort(Comparator.comparing(EmployeeSalaryProfile::getEffectiveFrom));

            BigDecimal empLopDays = finalLopDays.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            Employee employee = profiles.get(0).getEmployee();

            try {
                // Build profile segments for this month — each profile covers a date range within the month.
                List<ProfileSegment> segments = new ArrayList<>();
                int totalActiveDays = 0;

                for (int i = 0; i < profiles.size(); i++) {
                    EmployeeSalaryProfile prof = profiles.get(i);
                    LocalDate segStart = prof.getEffectiveFrom().isBefore(monthStart)
                            ? monthStart : prof.getEffectiveFrom();
                    LocalDate segEnd;
                    if (i < profiles.size() - 1) {
                        // This segment ends the day before the next profile begins
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
                // LOP days are distributed proportionally across segments by active day count.
                PeriodPayData merged = null;
                for (ProfileSegment seg : segments) {
                    BigDecimal segLopDays = (totalActiveDays > 0 && empLopDays.compareTo(BigDecimal.ZERO) > 0)
                            ? empLopDays.multiply(BigDecimal.valueOf(seg.activeDays()))
                                        .divide(BigDecimal.valueOf(totalActiveDays), 6, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    PeriodPayData segData = computePeriodPayData(
                            seg.profile(), seg.start(), seg.end(), totalDaysInMonth,
                            tenantConfig, orderedStructure, statutoryRates, ptSlabs, segLopDays, month, year);

                    merged = (merged == null) ? segData : mergePeriodData(merged, segData);
                }

                // Compute overtime once per employee (not per segment) to avoid double-counting
                if (tenantConfig.isEnableOvertime()) {
                    Double otHoursVal = manualAttendanceRepo.getTotalOvertimeByEmployeeAndDateRange(
                            tenantConfig.getTenant().getId(), employee.getId(), monthStart, monthEnd);
                    if (otHoursVal != null && otHoursVal > 0) {
                        merged.overtimeHours = BigDecimal.valueOf(otHoursVal);
                        merged.overtimeRate  = tenantConfig.getOvertimeRatePerHour() != null
                                ? tenantConfig.getOvertimeRatePerHour() : BigDecimal.ZERO;
                        merged.overtimePay   = merged.overtimeHours.multiply(merged.overtimeRate)
                                .setScale(2, RoundingMode.HALF_UP);
                        merged.grossEarnings = merged.grossEarnings.add(merged.overtimePay);
                    }
                }

                persistPayslip(payrun, employee, tenantConfig, merged, orderedStructure, month, year);

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
     * Earnings and deductions are summed; point-in-time fields use the most-recent segment's value.
     */
    private PeriodPayData mergePeriodData(PeriodPayData a, PeriodPayData b) {
        PeriodPayData merged = new PeriodPayData();

        // Component values: sum per component code across both segments
        merged.componentValues.putAll(a.componentValues);
        b.componentValues.forEach((code, val) ->
            merged.componentValues.merge(code, val, BigDecimal::add));

        // Expressions: last segment wins for the same key
        merged.expressions.putAll(a.expressions);
        merged.expressions.putAll(b.expressions);

        merged.grossEarnings   = a.grossEarnings.add(b.grossEarnings);
        merged.totalDeductions = a.totalDeductions.add(b.totalDeductions);
        merged.lopDays         = a.lopDays.add(b.lopDays);

        // Most-recent segment wins for point-in-time fields
        merged.wagesBase               = b.wagesBase;
        merged.contributionPeriodGross = b.contributionPeriodGross;

        // Overtime is computed once in processPayrun after the merge — leave as zero here
        return merged;
    }

    /**
     * Computes payroll for a single salary-profile period. Returns PeriodPayData without DB writes.
     * Called once per profile segment per employee; results are merged before persistence.
     */
    private PeriodPayData computePeriodPayData(
            EmployeeSalaryProfile profile,
            LocalDate periodStart, LocalDate periodEnd, int totalDaysInMonth,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> orderedStructure,
            List<StatutoryRateConfig> statutoryRates,
            List<StateProfessionalTaxConfig> ptSlabs,
            BigDecimal lopDays, int month, int year) {

        PeriodPayData data = new PeriodPayData();
        data.lopDays = lopDays;

        Employee employee = profile.getEmployee();

        // 1. Active calendar days for this period
        int activeDays = (int) java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        if (activeDays < 0) activeDays = 0;

        // 2. Proration Factor (active days / total calendar days in month)
        BigDecimal prorationFactor = BigDecimal.valueOf(activeDays)
                .divide(BigDecimal.valueOf(totalDaysInMonth), 6, RoundingMode.HALF_UP);

        // LOP daily wage denominator
        BigDecimal workingDays = BigDecimal.valueOf(totalDaysInMonth);
        if (LopBasis.WORKING_DAYS.equals(tenantConfig.getLopBasis())
                && tenantConfig.getWorkingDaysPerMonth() != null) {
            workingDays = BigDecimal.valueOf(tenantConfig.getWorkingDaysPerMonth());
        }

        // 3. Setup evaluation variables
        Map<String, Object> variables = new HashMap<>();
        BigDecimal monthlyCtc = profile.getMonthlyCtc();
        BigDecimal activeCtc  = monthlyCtc.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);

        // Use full monthly CTC initially for base allowance calculations
        variables.put("CTC", monthlyCtc.doubleValue());
        variables.put("LOP_DAYS", lopDays.doubleValue());
        variables.put("ACTIVE_DAYS", activeDays);
        variables.put("TOTAL_DAYS", totalDaysInMonth);

        // Inject statutory rates into variables
        for (StatutoryRateConfig rateConfig : statutoryRates) {
            variables.put(rateConfig.getComponentCode() + "_RATE", rateConfig.getRate().doubleValue());
            if (rateConfig.getCeilingAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CEILING",
                        rateConfig.getCeilingAmount().doubleValue());
            }
            if (rateConfig.getCapAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CAP",
                        rateConfig.getCapAmount().doubleValue());
            }
        }

        List<EmployeeSalaryComponent> overrides =
                employeeSalaryComponentRepo.findBySalaryProfileId(profile.getId());

        // Check which employer contributions are present in the structure
        boolean hasEpfEr = orderedStructure.stream().anyMatch(s -> "EPF_ER".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEpsEr = orderedStructure.stream().anyMatch(s -> "EPS_ER".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEdli  = orderedStructure.stream().anyMatch(s -> "EDLI".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEsiEr = orderedStructure.stream().anyMatch(s -> "ESI_ER".equalsIgnoreCase(s.getComponentCode()));

        // 4. First pass: Calculate Base Allowances (before proration and LOP)
        Map<String, BigDecimal> baseAllowances = new LinkedHashMap<>();
        BigDecimal otherBaseAllowancesSum = BigDecimal.ZERO;

        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode()))) {
                BigDecimal baseVal = BigDecimal.ZERO;
                if ("SPECIAL_ALLOWANCE".equalsIgnoreCase(item.getComponentCode())) {
                    // Compute employer contributions to determine SPECIAL_ALLOWANCE remainder
                    BigDecimal basicVal    = baseAllowances.getOrDefault("BASIC", BigDecimal.ZERO);
                    BigDecimal wagesBaseVal = basicVal;
                    if (tenantConfig.isEnforceNewLabourCodes()) {
                        BigDecimal floorVal = monthlyCtc.multiply(BigDecimal.valueOf(0.50))
                                .setScale(2, RoundingMode.HALF_UP);
                        wagesBaseVal = basicVal.max(floorVal);
                    }

                    double epfErRate   = (double) variables.getOrDefault("EPF_ER_RATE", 0.12);
                    double epsErRate   = (double) variables.getOrDefault("EPS_ER_RATE", 0.0833);
                    double epsErCap    = (double) variables.getOrDefault("EPS_ER_CAP", 1250.0);
                    double edliRate    = (double) variables.getOrDefault("EDLI_RATE", 0.005);
                    double edliCeiling = (double) variables.getOrDefault("EDLI_CEILING", 15000.0);

                    BigDecimal epsEr = BigDecimal.ZERO;
                    if (hasEpsEr) {
                        BigDecimal epsErVal = wagesBaseVal.multiply(BigDecimal.valueOf(epsErRate));
                        epsEr = BigDecimal.valueOf(Math.min(Math.round(epsErVal.doubleValue()), epsErCap))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    BigDecimal epfEr = BigDecimal.ZERO;
                    if (hasEpfEr) {
                        BigDecimal epfErVal = wagesBaseVal.multiply(BigDecimal.valueOf(epfErRate))
                                .setScale(2, RoundingMode.HALF_UP);
                        epfEr = epfErVal.subtract(epsEr).max(BigDecimal.ZERO);
                    }

                    BigDecimal edli = BigDecimal.ZERO;
                    if (hasEdli) {
                        BigDecimal edliLimit = wagesBaseVal.min(BigDecimal.valueOf(edliCeiling));
                        edli = edliLimit.multiply(BigDecimal.valueOf(edliRate)).setScale(2, RoundingMode.HALF_UP);
                    }

                    // Bug 4 fix: use monthlyCtc as ESI fallback in both Pass 1 and Pass 2 so that
                    // ESI eligibility is evaluated on the same basis in SPECIAL_ALLOWANCE pre-calc
                    // and in the actual deduction pass (previously Pass 2 used prorated grossEarnings).
                    BigDecimal esiEr = BigDecimal.ZERO;
                    if (hasEsiEr && tenantConfig.isEnableEsi()) {
                        BigDecimal cpGross = getContributionPeriodStartGross(
                                tenantConfig.getTenant().getId(), employee.getId(), year, month, monthlyCtc);
                        if (cpGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
                            double esiErRate = (double) variables.getOrDefault("ESI_ER_RATE", 0.0325);
                            esiEr = wagesBaseVal.multiply(BigDecimal.valueOf(esiErRate))
                                    .setScale(2, RoundingMode.HALF_UP);
                        }
                    }

                    BigDecimal employerContributionsSum = epfEr.add(epsEr).add(edli).add(esiEr);
                    baseVal = monthlyCtc.subtract(otherBaseAllowancesSum).subtract(employerContributionsSum)
                            .max(BigDecimal.ZERO);
                } else {
                    baseVal = calculateComponentValue(item, overrides, variables);
                    otherBaseAllowancesSum = otherBaseAllowancesSum.add(baseVal);
                }
                baseAllowances.put(item.getComponentCode(), baseVal);
                variables.put(item.getComponentCode(), baseVal.doubleValue());
            }
        }

        // 5. Second pass: Apply Proration and LOP deductions to base values
        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode()))) {
                BigDecimal baseVal    = baseAllowances.getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
                BigDecimal proratedVal = baseVal.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);

                if (lopDays.compareTo(BigDecimal.ZERO) > 0 && isLopApplicable(item.getComponentCode())) {
                    // Bug 1 fix: use baseVal (not proratedVal) as the daily wage base.
                    // proratedVal is already discounted for partial-month employment; dividing it again
                    // by the full month's working days under-charges LOP for mid-month joiners/leavers.
                    BigDecimal dailyWage    = baseVal.divide(workingDays, 6, RoundingMode.HALF_UP);
                    BigDecimal lopDeduction = dailyWage.multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
                    proratedVal = proratedVal.subtract(lopDeduction).max(BigDecimal.ZERO);
                }

                data.componentValues.put(item.getComponentCode(), proratedVal);
                data.expressions.put(item.getComponentCode(), getFormulaExpression(item, overrides));
                variables.put(item.getComponentCode(), proratedVal.doubleValue());
                data.grossEarnings = data.grossEarnings.add(proratedVal);
            }
        }

        // Switch CTC to prorated (active) value for deduction calculations
        variables.put("CTC", activeCtc.doubleValue());

        // 6. Calculate Wages Base (Code on Wages 2019 Rule)
        BigDecimal basic = data.componentValues.getOrDefault("BASIC", BigDecimal.ZERO);
        data.wagesBase = basic;
        if (tenantConfig.isEnforceNewLabourCodes()) {
            BigDecimal floor = activeCtc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);
            data.wagesBase = basic.max(floor);
        }
        variables.put("WAGES_BASE", data.wagesBase.doubleValue());
        variables.put("GROSS", data.grossEarnings.doubleValue());

        // Bug 4 fix: use monthlyCtc as fallback — consistent with Pass 1 above — so that a new joiner
        // with no historical payslip uses the same gross proxy for both SPECIAL_ALLOWANCE pre-calc and
        // the actual ESI deduction eligibility check.
        data.contributionPeriodGross = getContributionPeriodStartGross(
                tenantConfig.getTenant().getId(), employee.getId(), year, month, monthlyCtc);
        variables.put("CONTRIBUTION_PERIOD_GROSS", data.contributionPeriodGross.doubleValue());

        // 7. Deductions pass
        for (TenantSalaryStructure item : orderedStructure) {
            if ("DEDUCTION".equalsIgnoreCase(getComponentType(item.getComponentCode()))) {
                BigDecimal val = BigDecimal.ZERO;
                String code = item.getComponentCode();

                if ("ESI_EE".equalsIgnoreCase(code) || "ESI_ER".equalsIgnoreCase(code)) {
                    if (tenantConfig.isEnableEsi()
                            && data.contributionPeriodGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
                        val = calculateComponentValue(item, overrides, variables);
                    }
                } else if ("PT_DEDUCTION".equalsIgnoreCase(code) || "PT".equalsIgnoreCase(code)) {
                    if (tenantConfig.isEnablePt()) {
                        IndianState ptState = employee.getState();
                        if (ptState == null) {
                            log.warn("Employee ID {} missing state — PT skipped, payslip calculation failed",
                                    employee.getId());
                            throw new com.sonixhr.exceptions.BusinessException("PT_STATE_MISSING",
                                    "Employee is missing a valid state configuration for Professional Tax calculation");
                        }
                        val = calculatePTAmount(ptState, data.grossEarnings, month, ptSlabs);
                    }
                } else if ("EPF_EE".equalsIgnoreCase(code)) {
                    if (tenantConfig.isEnablePfCapping()) {
                        val = calculateComponentValue(item, overrides, variables);
                    } else {
                        // Bug 3 fix: use EPF_EE_RATE from statutory config rather than a hardcoded 0.12
                        // so that rate changes in the DB are automatically picked up.
                        double epfEeRate = (double) variables.getOrDefault("EPF_EE_RATE", 0.12);
                        val = data.wagesBase.multiply(BigDecimal.valueOf(epfEeRate))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    val = calculateComponentValue(item, overrides, variables);
                }

                data.componentValues.put(code, val);
                data.expressions.put(code, getFormulaExpression(item, overrides));
                variables.put(code, val.doubleValue());
                if (!isEmployerContribution(code)) {
                    data.totalDeductions = data.totalDeductions.add(val);
                }
            }
        }

        return data;
    }

    /**
     * Persists a merged PeriodPayData as a single Payslip + PayslipItems for the employee.
     */
    private void persistPayslip(Payrun payrun, Employee employee, TenantPayrollConfig tenantConfig,
            PeriodPayData data, List<TenantSalaryStructure> orderedStructure, int month, int year) {

        BigDecimal netPay = data.grossEarnings.subtract(data.totalDeductions).setScale(2, RoundingMode.HALF_UP);

        Payslip payslip = Payslip.builder()
                .tenant(tenantConfig.getTenant())
                .payrunId(payrun.getId())
                .employee(employee)
                .grossEarnings(data.grossEarnings)
                .totalDeductions(data.totalDeductions)
                .netPay(netPay)
                .lopDays(data.lopDays)
                .wagesBase(data.wagesBase)
                .contributionPeriodGross(data.contributionPeriodGross)
                .build();
        payslip = payslipRepo.save(payslip);

        // Persist one PayslipItem per salary structure component
        for (TenantSalaryStructure item : orderedStructure) {
            BigDecimal amount    = data.componentValues.getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
            String     expression= data.expressions.getOrDefault(item.getComponentCode(), "");

            Map<String, Object> auditVars = new HashMap<>();
            auditVars.put("WAGES_BASE", data.wagesBase);
            auditVars.put("GROSS",      data.grossEarnings);
            auditVars.put("value",      amount);

            String resolvedVarsJson = "";
            try {
                resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
            } catch (Exception ignored) {}

            payslipItemRepo.save(PayslipItem.builder()
                    .tenant(tenantConfig.getTenant())
                    .payslipId(payslip.getId())
                    .componentCode(item.getComponentCode())
                    .componentName(getComponentName(item.getComponentCode()))
                    .type(getComponentType(item.getComponentCode()))
                    .amount(amount)
                    .expressionUsed(expression)
                    .resolvedVariables(resolvedVarsJson)
                    .build());
        }

        // Persist overtime item if overtime was earned this month
        if (tenantConfig.isEnableOvertime() && data.overtimeHours.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> auditVars = new HashMap<>();
            auditVars.put("overtimeHours",       data.overtimeHours);
            auditVars.put("overtimeRatePerHour",  data.overtimeRate);
            auditVars.put("value",                data.overtimePay);

            String resolvedVarsJson = "";
            try {
                resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
            } catch (Exception ignored) {}

            payslipItemRepo.save(PayslipItem.builder()
                    .tenant(tenantConfig.getTenant())
                    .payslipId(payslip.getId())
                    .componentCode("OVERTIME")
                    .componentName("Overtime Payment")
                    .type("ALLOWANCE")
                    .amount(data.overtimePay)
                    .expressionUsed(data.overtimeHours + " hrs * " + data.overtimeRate + "/hr")
                    .resolvedVariables(resolvedVarsJson)
                    .build());
        }
    }

    private BigDecimal calculateComponentValue(TenantSalaryStructure structure,
            List<EmployeeSalaryComponent> overrides,
            Map<String, Object> variables) {
        // Check for employee-level override first
        Optional<EmployeeSalaryComponent> override = overrides.stream()
                .filter(o -> o.getComponentCode().equals(structure.getComponentCode()))
                .findFirst();

        if (override.isPresent()) {
            EmployeeSalaryComponent compOverride = override.get();
            if ("PERCENTAGE".equalsIgnoreCase(compOverride.getOverrideType())) {
                BigDecimal pct = compOverride.getOverrideValue() != null ? compOverride.getOverrideValue()
                        : BigDecimal.ZERO;
                if ("PERCENTAGE_OF_CTC".equalsIgnoreCase(structure.getCalculationType())) {
                    double ctc = (double) variables.getOrDefault("CTC", 0.0);
                    return BigDecimal.valueOf(ctc)
                            .multiply(pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                            .setScale(2, RoundingMode.HALF_UP);
                } else if ("PERCENTAGE_OF_BASIC".equalsIgnoreCase(structure.getCalculationType())) {
                    double basic = (double) variables.getOrDefault("BASIC", 0.0);
                    return BigDecimal.valueOf(basic)
                            .multiply(pct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            if (compOverride.getOverrideFormula() != null && !compOverride.getOverrideFormula().isEmpty()) {
                return spelEngine.evaluate(compOverride.getOverrideFormula(), variables);
            }
            return compOverride.getOverrideValue();
        }

        // Otherwise evaluate tenant salary structure rules
        if ("FIXED".equalsIgnoreCase(structure.getCalculationType())) {
            return structure.getValue();
        } else if ("PERCENTAGE_OF_CTC".equalsIgnoreCase(structure.getCalculationType())) {
            double ctc = (double) variables.getOrDefault("CTC", 0.0);
            return BigDecimal.valueOf(ctc)
                    .multiply(structure.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
        } else if ("PERCENTAGE_OF_BASIC".equalsIgnoreCase(structure.getCalculationType())) {
            double basic = (double) variables.getOrDefault("BASIC", 0.0);
            return BigDecimal.valueOf(basic)
                    .multiply(structure.getValue().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                    .setScale(2, RoundingMode.HALF_UP);
        } else if ("FORMULA".equalsIgnoreCase(structure.getCalculationType())) {
            // Evaluates formula (e.g. "#min(WAGES_BASE * EPF_EE_RATE, EPF_EE_CAP)")
            return spelEngine.evaluate(getFormulaForComponent(structure.getComponentCode()), variables);
        }

        return BigDecimal.ZERO;
    }

    private String getFormulaExpression(TenantSalaryStructure structure, List<EmployeeSalaryComponent> overrides) {
        Optional<EmployeeSalaryComponent> override = overrides.stream()
                .filter(o -> o.getComponentCode().equals(structure.getComponentCode()))
                .findFirst();
        if (override.isPresent()) {
            if (override.get().getOverrideFormula() != null) {
                return override.get().getOverrideFormula();
            }
            return "FIXED: " + override.get().getOverrideValue();
        }
        if ("FORMULA".equalsIgnoreCase(structure.getCalculationType())) {
            return getFormulaForComponent(structure.getComponentCode());
        }
        return structure.getCalculationType() + " : " + structure.getValue();
    }

    private String getFormulaForComponent(String componentCode) {
        switch (componentCode.toUpperCase()) {
            case "EPF_EE":
                return "#min(WAGES_BASE * EPF_EE_RATE, EPF_EE_CAP)";
            case "EPF_ER":
                return "#max(WAGES_BASE * EPF_ER_RATE - EPS_ER, 0)";
            case "EPS_ER":
                return "#min(#round(WAGES_BASE * EPS_ER_RATE), EPS_ER_CAP)";
            case "EDLI":
                return "#min(WAGES_BASE, EDLI_CEILING) * EDLI_RATE";
            case "ESI_EE":
                return "WAGES_BASE * ESI_EE_RATE";
            case "ESI_ER":
                return "WAGES_BASE * ESI_ER_RATE";
            default:
                return "0.00";
        }
    }

    private BigDecimal calculatePTAmount(IndianState state, BigDecimal grossEarnings, int month,
            List<StateProfessionalTaxConfig> slabs) {
        if (state == null)
            return BigDecimal.ZERO;

        for (StateProfessionalTaxConfig slab : slabs) {
            if (slab.getStateCode() == state) {
                // Check month-specific rule
                if (slab.getApplicableMonth() != null && slab.getApplicableMonth() != month) {
                    continue;
                }
                // Match gross range
                // Lower bound is exclusive if salaryRangeMin > 0, otherwise inclusive (>= 0)
                boolean matchesMin = (slab.getSalaryRangeMin().compareTo(BigDecimal.ZERO) == 0)
                        ? (grossEarnings.compareTo(slab.getSalaryRangeMin()) >= 0)
                        : (grossEarnings.compareTo(slab.getSalaryRangeMin()) > 0);

                boolean matchesMax = (slab.getSalaryRangeMax() == null)
                        || (grossEarnings.compareTo(slab.getSalaryRangeMax()) <= 0);

                if (matchesMin && matchesMax) {
                    return slab.getAmount();
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal getContributionPeriodStartGross(Long tenantId, Long employeeId, int payrunYear, int payrunMonth,
            BigDecimal currentGross) {
        // ESI periods: April to September (4-9) and October to March (10-3).
        // Find starting month of current contribution period
        int startMonth = (payrunMonth >= 4 && payrunMonth <= 9) ? 4 : 10;
        int startYear  = (payrunMonth < 4) ? payrunYear - 1 : payrunYear;

        if (startMonth == payrunMonth && startYear == payrunYear) {
            return currentGross;
        }

        // Query historical payslip at start of contribution period
        Optional<Payrun> startPayrun = payrunRepo.findByTenantAndMonthAndYear(tenantId, startMonth, startYear);
        if (startPayrun.isPresent()) {
            Optional<Payslip> startingPayslip = payslipRepo.findByPayrunIdAndEmployeeId(
                    startPayrun.get().getId(), employeeId);
            if (startingPayslip.isPresent()) {
                return startingPayslip.get().getGrossEarnings();
            }
        }

        // If no payslip is found (e.g. new joiner mid-period), use the provided fallback gross
        return currentGross;
    }

    private String getComponentType(String componentCode) {
        switch (componentCode.toUpperCase()) {
            case "BASIC":
            case "HRA":
            case "LTA":
            case "CONVEYANCE":
            case "SPECIAL_ALLOWANCE":
                return "ALLOWANCE";
            case "EPF_EE":
            case "EPF_ER":
            case "EPS_ER":
            case "EDLI":
            case "ESI_EE":
            case "ESI_ER":
            case "PT_DEDUCTION":
            case "PT":
            case "TDS":
                return "DEDUCTION";
            default:
                // Bug 5: unknown codes default to ALLOWANCE (LOP-applicable, added to grossEarnings).
                // To handle custom component types correctly, add a component_type column to
                // tenant_salary_structures and read it directly instead of relying on this switch.
                log.warn("Unknown component code '{}' — defaulting to ALLOWANCE type. "
                        + "Custom deduction codes will be incorrectly included in gross earnings. "
                        + "Add a component_type column to tenant_salary_structures to fix this.",
                        componentCode);
                return "ALLOWANCE";
        }
    }

    private String getComponentName(String componentCode) {
        switch (componentCode.toUpperCase()) {
            case "BASIC":             return "Basic Salary";
            case "HRA":               return "House Rent Allowance";
            case "LTA":               return "Leave Travel Allowance";
            case "CONVEYANCE":        return "Conveyance Allowance";
            case "SPECIAL_ALLOWANCE": return "Special Allowance";
            case "EPF_EE":            return "EPF Employee Contribution";
            case "EPF_ER":            return "EPF Employer Contribution";
            case "EPS_ER":            return "EPS Pension Share";
            case "EDLI":              return "EDLI Insurance Premium";
            case "ESI_EE":            return "ESI Employee Contribution";
            case "ESI_ER":            return "ESI Employer Contribution";
            case "PT_DEDUCTION":
            case "PT":                return "Professional Tax";
            case "TDS":               return "Tax Deducted at Source";
            default:                  return componentCode;
        }
    }

    private boolean isLopApplicable(String componentCode) {
        // LOP applies to earnings (allowances) only, not deductions.
        return "ALLOWANCE".equals(getComponentType(componentCode));
    }

    private boolean isEmployerContribution(String componentCode) {
        switch (componentCode.toUpperCase()) {
            case "EPF_ER":
            case "EPS_ER":
            case "EDLI":
            case "ESI_ER":
                return true;
            default:
                return false;
        }
    }

    private Map<Long, BigDecimal> calculateUnpaidLeaveDaysForTenant(Long tenantId, int month, int year) {
        Map<Long, BigDecimal> employeeLopDays = new HashMap<>();

        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd   = YearMonth.of(year, month).atEndOfMonth();

        // Fetch all approved unpaid leaves overlapping this month
        List<LeaveRequest> unpaidLeaves = leaveRequestRepo
                .findAllApprovedLeavesInDateRange(tenantId, monthStart, monthEnd).stream()
                .filter(l -> l.getLeaveType() == LeaveType.UNPAID)
                .collect(Collectors.toList());

        // Fetch Tenant Leave Settings
        com.sonixhr.entity.leave.TenantLeaveSettings settings =
                tenantLeaveSettingsRepo.findById(tenantId).orElse(null);

        for (LeaveRequest leave : unpaidLeaves) {
            Employee employee = leave.getEmployee();
            Long employeeId   = employee.getId();

            // Calculate overlap dates in this month
            LocalDate overlapStart = leave.getStartDate().isBefore(monthStart) ? monthStart : leave.getStartDate();
            LocalDate overlapEnd   = leave.getEndDate().isAfter(monthEnd) ? monthEnd : leave.getEndDate();

            // Pre-compute holiday dates for the overlap range — needed for both half-day and full-day checks
            Set<LocalDate> holidayDates = new HashSet<>();
            if (settings != null && (settings.getIncludeNationalHolidays() || settings.getIncludeStateHolidays())) {
                List<com.sonixhr.entity.leave.PublicHoliday> holidays = publicHolidayRepo
                        .findByTenantIdAndHolidayDateBetween(tenantId, overlapStart, overlapEnd);
                for (com.sonixhr.entity.leave.PublicHoliday h : holidays) {
                    boolean isNational = "NATIONAL".equalsIgnoreCase(h.getType())
                            && settings.getIncludeNationalHolidays();
                    boolean isState = settings.getIncludeStateHolidays() &&
                            ((settings.getState() != null
                                    && settings.getState().name().equalsIgnoreCase(h.getRegion())) ||
                             (settings.getStateText() != null
                                    && settings.getStateText().equalsIgnoreCase(h.getRegion())));
                    if (isNational || isState) {
                        holidayDates.add(h.getHolidayDate());
                    }
                }
            }

            BigDecimal leaveDays;
            if (Boolean.TRUE.equals(leave.getIsHalfDay())) {
                // Bug 7 fix: check whether the half-day falls on a non-working day before counting it.
                // Previously 0.5 was returned unconditionally, ignoring holidays and weekends.
                LocalDate halfDayDate = overlapStart; // for a half-day leave, startDate == endDate
                boolean isWeekend = leaveConfigService.isWeekendForEmployee(halfDayDate, employee, settings);
                boolean isHoliday = holidayDates.contains(halfDayDate);
                boolean skipDay   = (isWeekend  && (settings == null || settings.getCountWeekendsAsLeave() == null
                                              || !settings.getCountWeekendsAsLeave()))
                                 || (isHoliday && (settings == null || settings.getCountHolidaysAsLeave() == null
                                              || !settings.getCountHolidaysAsLeave()));
                leaveDays = skipDay ? BigDecimal.ZERO : BigDecimal.valueOf(0.5);
            } else {
                double days = 0;
                LocalDate date = overlapStart;

                while (!date.isAfter(overlapEnd)) {
                    boolean isWeekend = leaveConfigService.isWeekendForEmployee(date, employee, settings);
                    boolean isHoliday = holidayDates.contains(date);

                    boolean countDay = true;
                    if (isWeekend && (settings == null || settings.getCountWeekendsAsLeave() == null
                            || !settings.getCountWeekendsAsLeave())) {
                        countDay = false;
                    }
                    if (isHoliday && (settings == null || settings.getCountHolidaysAsLeave() == null
                            || !settings.getCountHolidaysAsLeave())) {
                        countDay = false;
                    }

                    if (countDay) {
                        days++;
                    }
                    date = date.plusDays(1);
                }
                leaveDays = BigDecimal.valueOf(days);
            }

            BigDecimal currentSum = employeeLopDays.getOrDefault(employeeId, BigDecimal.ZERO);
            employeeLopDays.put(employeeId, currentSum.add(leaveDays));
        }

        return employeeLopDays;
    }
}
