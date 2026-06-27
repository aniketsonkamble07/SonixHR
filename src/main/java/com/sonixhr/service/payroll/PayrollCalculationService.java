package com.sonixhr.service.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.exceptions.PayrunLockedException;
import com.sonixhr.entity.leave.LeaveRequest;
import com.sonixhr.enums.leave.LeaveType;
import com.sonixhr.repository.leave.LeaveRequestRepository;
import com.sonixhr.repository.leave.PublicHolidayRepository;
import com.sonixhr.repository.leave.TenantLeaveSettingsRepository;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class PayrollCalculationService {

    @Autowired
    private StatutoryRateConfigRepository statutoryRateConfigRepo;

    @Autowired
    private StateProfessionalTaxConfigRepository statePtConfigRepo;

    @Autowired
    private TenantPayrollConfigRepository tenantPayrollConfigRepo;

    @Autowired
    private TenantSalaryStructureRepository tenantSalaryStructureRepo;

    @Autowired
    private EmployeeSalaryProfileRepository employeeSalaryProfileRepo;

    @Autowired
    private EmployeeSalaryComponentRepository employeeSalaryComponentRepo;

    @Autowired
    private PayrunRepository payrunRepo;

    @Autowired
    private PayrunConfigRepository payrunConfigRepo;

    @Autowired
    private PayslipRepository payslipRepo;

    @Autowired
    private PayslipItemRepository payslipItemRepo;

    @Autowired
    private SandboxedSpELEngine spelEngine;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LeaveRequestRepository leaveRequestRepo;

    @Autowired
    private TenantLeaveSettingsRepository tenantLeaveSettingsRepo;

    @Autowired
    private PublicHolidayRepository publicHolidayRepo;

    @Autowired
    private com.sonixhr.service.leave.LeaveConfigurationService leaveConfigService;

    @Autowired
    private ManualAttendanceRepository manualAttendanceRepo;

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
            // If draft, we'll clean up old payslips first
            List<Payslip> oldPayslips = payslipRepo.findByPayrunId(existingPayrun.get().getId());
            for (Payslip oldPayslip : oldPayslips) {
                List<PayslipItem> items = payslipItemRepo.findByPayslipId(oldPayslip.getId());
                payslipItemRepo.deleteAll(items);
            }
            payslipRepo.deleteAll(oldPayslips);
            payrunConfigRepo.findByPayrunId(existingPayrun.get().getId()).ifPresent(payrunConfigRepo::delete);
        }

        LocalDate payrunDate = LocalDate.of(year, month, 1);

        // 2. Fetch tenant settings and structure
        TenantPayrollConfig tenantConfig = tenantPayrollConfigRepo.findActiveByTenantAndDate(tenantId, payrunDate)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant payroll configuration not found for date: " + payrunDate));

        List<TenantSalaryStructure> salaryStructure = tenantSalaryStructureRepo.findActiveByTenantAndDate(tenantId,
                payrunDate);
        if (salaryStructure.isEmpty()) {
            throw new IllegalArgumentException("Tenant salary structure not defined or active.");
        }

        // 3. Fetch statutory rules and professional tax slabs
        List<StatutoryRateConfig> statutoryRates = statutoryRateConfigRepo.findActiveByDate(payrunDate);
        List<StateProfessionalTaxConfig> ptSlabs = statePtConfigRepo.findAll(); // Simple fetch for snapshot

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
            throw new RuntimeException("Failed to serialize payrun configurations for snapshot", e);
        }

        // 6. Fetch active employee profiles overlapping this month
        LocalDate endDate = YearMonth.of(year, month).atEndOfMonth();
        List<EmployeeSalaryProfile> allProfiles = employeeSalaryProfileRepo.findActiveProfilesByTenantInPeriod(tenantId,
                payrunDate, endDate);

        // Group by employee and select the profile with the latest effectiveFrom date
        // to handle mid-month joiners/changes
        Collection<EmployeeSalaryProfile> employeeProfiles = allProfiles.stream()
                .collect(Collectors.toMap(
                        p -> p.getEmployee().getId(),
                        p -> p,
                        (p1, p2) -> p1.getEffectiveFrom().isAfter(p2.getEffectiveFrom()) ? p1 : p2))
                .values();

        // Compute LOP days from approved unpaid leaves
        Map<Long, BigDecimal> finalLopDays = calculateUnpaidLeaveDaysForTenant(tenantId, month, year);

        // 7. Process each employee
        for (EmployeeSalaryProfile profile : employeeProfiles) {
            BigDecimal lopDays = finalLopDays.getOrDefault(profile.getEmployee().getId(), BigDecimal.ZERO);
            calculateEmployeePayslip(payrun, profile, tenantConfig, salaryStructure, statutoryRates, ptSlabs, lopDays,
                    month, year);
        }

        return payrun;
    }

    /**
     * Calculates the payslip for a single employee and persists it.
     */
    private void calculateEmployeePayslip(Payrun payrun, EmployeeSalaryProfile profile,
            TenantPayrollConfig tenantConfig,
            List<TenantSalaryStructure> salaryStructure,
            List<StatutoryRateConfig> statutoryRates,
            List<StateProfessionalTaxConfig> ptSlabs,
            BigDecimal lopDays, int month, int year) {
        Employee employee = profile.getEmployee();
        LocalDate lastDayOfMonth = YearMonth.of(year, month).atEndOfMonth();

        // 1. Calculate active calendar days in this month (handling mid-month joining
        // or termination)
        int totalDaysInMonth = YearMonth.of(year, month).lengthOfMonth();
        int activeDays = totalDaysInMonth;

        LocalDate activeStart = profile.getEffectiveFrom().isAfter(LocalDate.of(year, month, 1))
                ? profile.getEffectiveFrom()
                : LocalDate.of(year, month, 1);

        LocalDate activeEnd = (profile.getEffectiveTo() != null && profile.getEffectiveTo().isBefore(lastDayOfMonth))
                ? profile.getEffectiveTo()
                : lastDayOfMonth;

        if (activeStart.isAfter(LocalDate.of(year, month, 1)) || activeEnd.isBefore(lastDayOfMonth)) {
            activeDays = (int) java.time.temporal.ChronoUnit.DAYS.between(activeStart, activeEnd) + 1;
            if (activeDays < 0)
                activeDays = 0;
        }

        // 2. Proration Factor (Active Days / Total Days)
        BigDecimal prorationFactor = BigDecimal.valueOf(activeDays).divide(BigDecimal.valueOf(totalDaysInMonth), 6,
                RoundingMode.HALF_UP);

        // Determine Daily LOP wage base
        BigDecimal workingDays = BigDecimal.valueOf(totalDaysInMonth);
        if (LopBasis.WORKING_DAYS.equals(tenantConfig.getLopBasis()) && tenantConfig.getWorkingDaysPerMonth() != null) {
            workingDays = BigDecimal.valueOf(tenantConfig.getWorkingDaysPerMonth());
        }

        // 3. Setup evaluation variables
        Map<String, Object> variables = new HashMap<>();
        BigDecimal monthlyCtc = profile.getMonthlyCtc();
        BigDecimal activeCtc = monthlyCtc.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);

        // Put full monthly CTC initially for base allowance calculations
        variables.put("CTC", monthlyCtc.doubleValue());
        variables.put("LOP_DAYS", lopDays.doubleValue());
        variables.put("ACTIVE_DAYS", activeDays);
        variables.put("TOTAL_DAYS", totalDaysInMonth);

        // Inject statutory rates into variables
        for (StatutoryRateConfig rateConfig : statutoryRates) {
            variables.put(rateConfig.getComponentCode() + "_RATE", rateConfig.getRate().doubleValue());
            if (rateConfig.getCeilingAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CEILING", rateConfig.getCeilingAmount().doubleValue());
            }
            if (rateConfig.getCapAmount() != null) {
                variables.put(rateConfig.getComponentCode() + "_CAP", rateConfig.getCapAmount().doubleValue());
            }
        }

        // 4. Evaluate Allowances (Topologically ordered)
        BigDecimal grossEarnings = BigDecimal.ZERO;
        Map<String, BigDecimal> calculatedValues = new HashMap<>();

        // We sort the structure by evaluationOrder to ensure proper dependencies
        // resolution
        List<TenantSalaryStructure> orderedStructure = new ArrayList<>(salaryStructure);
        orderedStructure.sort(Comparator.comparingInt(TenantSalaryStructure::getEvaluationOrder));

        // Evaluate allowances & employee custom overrides
        List<EmployeeSalaryComponent> overrides = employeeSalaryComponentRepo.findBySalaryProfileId(profile.getId());

        // First pass: Calculate Base Allowances (before proration and LOP)
        Map<String, BigDecimal> baseAllowances = new HashMap<>();
        BigDecimal otherBaseAllowancesSum = BigDecimal.ZERO;

        // Check which employer contributions are in the structure
        boolean hasEpfEr = orderedStructure.stream().anyMatch(s -> "EPF_ER".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEpsEr = orderedStructure.stream().anyMatch(s -> "EPS_ER".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEdli = orderedStructure.stream().anyMatch(s -> "EDLI".equalsIgnoreCase(s.getComponentCode()));
        boolean hasEsiEr = orderedStructure.stream().anyMatch(s -> "ESI_ER".equalsIgnoreCase(s.getComponentCode()));

        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode()))) {
                BigDecimal baseVal = BigDecimal.ZERO;
                if ("SPECIAL_ALLOWANCE".equalsIgnoreCase(item.getComponentCode())) {
                    // Calculate wagesBase for employer contributions calculation
                    BigDecimal basicVal = baseAllowances.getOrDefault("BASIC", BigDecimal.ZERO);
                    BigDecimal wagesBaseVal = basicVal;
                    if (tenantConfig.isEnforceNewLabourCodes()) {
                        BigDecimal floorVal = monthlyCtc.multiply(BigDecimal.valueOf(0.50)).setScale(2,
                                RoundingMode.HALF_UP);
                        wagesBaseVal = basicVal.max(floorVal);
                    }

                    // Get rates
                    double epfErRate = (double) variables.getOrDefault("EPF_ER_RATE", 0.12);
                    double epsErRate = (double) variables.getOrDefault("EPS_ER_RATE", 0.0833);
                    double epsErCap = (double) variables.getOrDefault("EPS_ER_CAP", 1250.0);
                    double edliRate = (double) variables.getOrDefault("EDLI_RATE", 0.005);
                    double edliCeiling = (double) variables.getOrDefault("EDLI_CEILING", 15000.0);

                    // Compute EPS_ER if applicable
                    BigDecimal epsEr = BigDecimal.ZERO;
                    if (hasEpsEr) {
                        BigDecimal epsErVal = wagesBaseVal.multiply(BigDecimal.valueOf(epsErRate));
                        double roundedEpsEr = Math.round(epsErVal.doubleValue());
                        epsEr = BigDecimal.valueOf(Math.min(roundedEpsEr, epsErCap)).setScale(2, RoundingMode.HALF_UP);
                    }

                    // Compute EPF_ER if applicable
                    BigDecimal epfEr = BigDecimal.ZERO;
                    if (hasEpfEr) {
                        BigDecimal epfErVal = wagesBaseVal.multiply(BigDecimal.valueOf(epfErRate)).setScale(2,
                                RoundingMode.HALF_UP);
                        epfEr = epfErVal.subtract(epsEr);
                        if (epfEr.compareTo(BigDecimal.ZERO) < 0) {
                            epfEr = BigDecimal.ZERO;
                        }
                    }

                    // Compute EDLI if applicable
                    BigDecimal edli = BigDecimal.ZERO;
                    if (hasEdli) {
                        BigDecimal edliLimit = wagesBaseVal.min(BigDecimal.valueOf(edliCeiling));
                        edli = edliLimit.multiply(BigDecimal.valueOf(edliRate)).setScale(2, RoundingMode.HALF_UP);
                    }

                    // Compute ESI_ER if applicable
                    BigDecimal esiEr = BigDecimal.ZERO;
                    if (hasEsiEr && tenantConfig.isEnableEsi()) {
                        BigDecimal contributionPeriodGross = getContributionPeriodStartGross(
                                tenantConfig.getTenant().getId(), profile.getEmployee().getId(), year, month,
                                monthlyCtc);
                        if (contributionPeriodGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
                            double esiErRate = (double) variables.getOrDefault("ESI_ER_RATE", 0.0325);
                            esiEr = wagesBaseVal.multiply(BigDecimal.valueOf(esiErRate)).setScale(2,
                                    RoundingMode.HALF_UP);
                        }
                    }

                    BigDecimal employerContributionsSum = epfEr.add(epsEr).add(edli).add(esiEr);

                    // Special Allowance is the remainder of CTC minus all other allowances and
                    // employer contributions
                    baseVal = monthlyCtc.subtract(otherBaseAllowancesSum).subtract(employerContributionsSum);
                    if (baseVal.compareTo(BigDecimal.ZERO) < 0) {
                        baseVal = BigDecimal.ZERO;
                    }
                } else {
                    baseVal = calculateComponentValue(item, overrides, variables);
                    otherBaseAllowancesSum = otherBaseAllowancesSum.add(baseVal);
                }

                baseAllowances.put(item.getComponentCode(), baseVal);
                variables.put(item.getComponentCode(), baseVal.doubleValue());
            }
        }

        // Second pass: Apply Proration and LOP deductions to base values
        for (TenantSalaryStructure item : orderedStructure) {
            if ("ALLOWANCE".equalsIgnoreCase(getComponentType(item.getComponentCode()))) {
                BigDecimal baseVal = baseAllowances.getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
                BigDecimal proratedVal = baseVal.multiply(prorationFactor).setScale(2, RoundingMode.HALF_UP);

                // If LOP applies to this component, deduct LOP amount
                if (lopDays.compareTo(BigDecimal.ZERO) > 0 && isLopApplicable(item.getComponentCode())) {
                    BigDecimal dailyWage = proratedVal.divide(workingDays, 6, RoundingMode.HALF_UP);
                    BigDecimal lopDeduction = dailyWage.multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
                    proratedVal = proratedVal.subtract(lopDeduction);
                    if (proratedVal.compareTo(BigDecimal.ZERO) < 0) {
                        proratedVal = BigDecimal.ZERO;
                    }
                }

                calculatedValues.put(item.getComponentCode(), proratedVal);
                variables.put(item.getComponentCode(), proratedVal.doubleValue());
                grossEarnings = grossEarnings.add(proratedVal);
            }
        }

        // Calculate and add Overtime Pay if enabled
        BigDecimal overtimeHours = BigDecimal.ZERO;
        BigDecimal overtimeRate = BigDecimal.ZERO;
        BigDecimal overtimePay = BigDecimal.ZERO;
        if (tenantConfig.isEnableOvertime()) {
            LocalDate monthStart = LocalDate.of(year, month, 1);
            LocalDate monthEnd = YearMonth.of(year, month).atEndOfMonth();
            Double otHoursVal = manualAttendanceRepo.getTotalOvertimeByEmployeeAndDateRange(
                    tenantConfig.getTenant().getId(), employee.getId(), monthStart, monthEnd);
            if (otHoursVal != null && otHoursVal > 0) {
                overtimeHours = BigDecimal.valueOf(otHoursVal);
                overtimeRate = tenantConfig.getOvertimeRatePerHour() != null
                        ? tenantConfig.getOvertimeRatePerHour()
                        : BigDecimal.ZERO;
                overtimePay = overtimeHours.multiply(overtimeRate).setScale(2, RoundingMode.HALF_UP);
                grossEarnings = grossEarnings.add(overtimePay);
                variables.put("OVERTIME", overtimePay.doubleValue());
                variables.put("OVERTIME_HOURS", overtimeHours.doubleValue());
            }
        }

        // Set CTC back to active (prorated) CTC for the rest of calculations
        variables.put("CTC", activeCtc.doubleValue());

        // Calculate Wages Base (Code on Wages 2019 Rule)
        BigDecimal basic = calculatedValues.getOrDefault("BASIC", BigDecimal.ZERO);
        BigDecimal wagesBase = basic;
        if (tenantConfig.isEnforceNewLabourCodes()) {
            // Statutory Wages Base must be at least 50% of CTC
            BigDecimal floor = activeCtc.multiply(BigDecimal.valueOf(0.50)).setScale(2, RoundingMode.HALF_UP);
            wagesBase = basic.max(floor);
        }
        variables.put("WAGES_BASE", wagesBase.doubleValue());
        variables.put("GROSS", grossEarnings.doubleValue());

        // ESI Period Eligibility check
        BigDecimal contributionPeriodGross = getContributionPeriodStartGross(tenantConfig.getTenant().getId(),
                profile.getEmployee().getId(), year, month, grossEarnings);
        variables.put("CONTRIBUTION_PERIOD_GROSS", contributionPeriodGross.doubleValue());

        // Second pass: Resolve Deductions
        BigDecimal totalDeductions = BigDecimal.ZERO;
        for (TenantSalaryStructure item : orderedStructure) {
            if ("DEDUCTION".equalsIgnoreCase(getComponentType(item.getComponentCode()))) {
                BigDecimal val = BigDecimal.ZERO;
                if ("ESI_EE".equalsIgnoreCase(item.getComponentCode())
                        || "ESI_ER".equalsIgnoreCase(item.getComponentCode())) {
                    if (tenantConfig.isEnableEsi()
                            && contributionPeriodGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
                        val = calculateComponentValue(item, overrides, variables);
                    }
                } else if ("PT_DEDUCTION".equalsIgnoreCase(item.getComponentCode())
                        || "PT".equalsIgnoreCase(item.getComponentCode())) {
                    if (tenantConfig.isEnablePt()) {
                        IndianState ptState = employee.getState();
                        if (ptState == null && employee.getWorkLocation() != null
                                && !employee.getWorkLocation().trim().isEmpty()) {
                            String wl = employee.getWorkLocation().trim();
                            ptState = IndianState.fromCode(wl);
                            if (ptState == null) {
                                String upperLoc = wl.toUpperCase();
                                for (IndianState s : IndianState.values()) {
                                    if (upperLoc.contains(s.getDisplayName().toUpperCase()) ||
                                            upperLoc.matches(".*\\b" + s.name() + "\\b.*")) {
                                        ptState = s;
                                        break;
                                    }
                                }
                            }
                        }
                        val = calculatePTAmount(ptState, grossEarnings, month, ptSlabs);
                    }
                } else if ("EPF_EE".equalsIgnoreCase(item.getComponentCode())
                        || "EPF_ER".equalsIgnoreCase(item.getComponentCode())
                        || "EPS_ER".equalsIgnoreCase(item.getComponentCode())
                        || "EDLI".equalsIgnoreCase(item.getComponentCode())) {
                    if ("EPF_EE".equalsIgnoreCase(item.getComponentCode()) && tenantConfig.isEnablePfCapping()) {
                        val = calculateComponentValue(item, overrides, variables);
                    } else if ("EPF_EE".equalsIgnoreCase(item.getComponentCode())) {
                        // Uncapped EPF (12% of wages base)
                        val = wagesBase.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP);
                    } else {
                        val = calculateComponentValue(item, overrides, variables);
                    }
                } else {
                    val = calculateComponentValue(item, overrides, variables);
                }

                calculatedValues.put(item.getComponentCode(), val);
                variables.put(item.getComponentCode(), val.doubleValue());
                if (!isEmployerContribution(item.getComponentCode())) {
                    totalDeductions = totalDeductions.add(val);
                }
            }
        }

        // Calculate Net Pay
        BigDecimal netPay = grossEarnings.subtract(totalDeductions).setScale(2, RoundingMode.HALF_UP);

        // 5. Persist Payslip
        Payslip payslip = Payslip.builder()
                .tenant(tenantConfig.getTenant())
                .payrunId(payrun.getId())
                .employee(employee)
                .grossEarnings(grossEarnings)
                .totalDeductions(totalDeductions)
                .netPay(netPay)
                .lopDays(lopDays)
                .wagesBase(wagesBase)
                .contributionPeriodGross(contributionPeriodGross)
                .build();
        payslip = payslipRepo.save(payslip);

        // 6. Persist PayslipItems
        for (TenantSalaryStructure item : orderedStructure) {
            BigDecimal amount = calculatedValues.getOrDefault(item.getComponentCode(), BigDecimal.ZERO);
            String expression = getFormulaExpression(item, overrides);

            Map<String, Object> auditVars = new HashMap<>();
            // Only add variables that were relevant for this component for cleaner log
            auditVars.put("WAGES_BASE", wagesBase);
            auditVars.put("GROSS", grossEarnings);
            auditVars.put("CTC", activeCtc);
            auditVars.put("value", amount);

            String resolvedVarsJson = "";
            try {
                resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
            } catch (Exception ignored) {
            }

            PayslipItem payslipItem = PayslipItem.builder()
                    .tenant(tenantConfig.getTenant())
                    .payslipId(payslip.getId())
                    .componentCode(item.getComponentCode())
                    .componentName(getComponentName(item.getComponentCode()))
                    .type(getComponentType(item.getComponentCode()))
                    .amount(amount)
                    .expressionUsed(expression)
                    .resolvedVariables(resolvedVarsJson)
                    .build();
            payslipItemRepo.save(payslipItem);
        }

        // Add overtime item if overtime is enabled and hours > 0
        if (tenantConfig.isEnableOvertime() && overtimeHours.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> auditVars = new HashMap<>();
            auditVars.put("overtimeHours", overtimeHours);
            auditVars.put("overtimeRatePerHour", overtimeRate);
            auditVars.put("value", overtimePay);

            String resolvedVarsJson = "";
            try {
                resolvedVarsJson = objectMapper.writeValueAsString(auditVars);
            } catch (Exception ignored) {
            }

            PayslipItem otItem = PayslipItem.builder()
                    .tenant(tenantConfig.getTenant())
                    .payslipId(payslip.getId())
                    .componentCode("OVERTIME")
                    .componentName("Overtime Payment")
                    .type("ALLOWANCE")
                    .amount(overtimePay)
                    .expressionUsed(overtimeHours + " hrs * " + overtimeRate + "/hr")
                    .resolvedVariables(resolvedVarsJson)
                    .build();
            payslipItemRepo.save(otItem);
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
        int startYear = (payrunMonth < 4) ? payrunYear - 1 : payrunYear;

        if (startMonth == payrunMonth && startYear == payrunYear) {
            return currentGross;
        }

        // Query historical payslip at start of contribution period
        Optional<Payrun> startPayrun = payrunRepo.findByTenantAndMonthAndYear(tenantId, startMonth, startYear);
        if (startPayrun.isPresent()) {
            Optional<Payslip> startingPayslip = payslipRepo.findByPayrunIdAndEmployeeId(startPayrun.get().getId(),
                    employeeId);
            if (startingPayslip.isPresent()) {
                return startingPayslip.get().getGrossEarnings();
            }
        }

        // If no payslip is found (e.g. new joiner in May), use current gross
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
                return "ALLOWANCE";
        }
    }

    private String getComponentName(String componentCode) {
        switch (componentCode.toUpperCase()) {
            case "BASIC":
                return "Basic Salary";
            case "HRA":
                return "House Rent Allowance";
            case "LTA":
                return "Leave Travel Allowance";
            case "CONVEYANCE":
                return "Conveyance Allowance";
            case "SPECIAL_ALLOWANCE":
                return "Special Allowance";
            case "EPF_EE":
                return "EPF Employee Contribution";
            case "EPF_ER":
                return "EPF Employer Contribution";
            case "EPS_ER":
                return "EPS Pension Share";
            case "EDLI":
                return "EDLI Insurance Premium";
            case "ESI_EE":
                return "ESI Employee Contribution";
            case "ESI_ER":
                return "ESI Employer Contribution";
            case "PT_DEDUCTION":
            case "PT":
                return "Professional Tax";
            case "TDS":
                return "Tax Deducted at Source";
            default:
                return componentCode;
        }
    }

    private boolean isLopApplicable(String componentCode) {
        // LOP applies to earnings like Basic, HRA, and Special Allowance.
        // It does not apply to deductions.
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
        LocalDate monthEnd = java.time.YearMonth.of(year, month).atEndOfMonth();

        // Fetch all approved unpaid leaves overlapping this month
        List<LeaveRequest> unpaidLeaves = leaveRequestRepo
                .findAllApprovedLeavesInDateRange(tenantId, monthStart, monthEnd).stream()
                .filter(l -> l.getLeaveType() == LeaveType.UNPAID)
                .collect(Collectors.toList());

        // Fetch Tenant Leave Settings
        com.sonixhr.entity.leave.TenantLeaveSettings settings = tenantLeaveSettingsRepo.findById(tenantId).orElse(null);

        for (LeaveRequest leave : unpaidLeaves) {
            Employee employee = leave.getEmployee();
            Long employeeId = employee.getId();

            // Calculate overlap dates in this month
            LocalDate overlapStart = leave.getStartDate().isBefore(monthStart) ? monthStart : leave.getStartDate();
            LocalDate overlapEnd = leave.getEndDate().isAfter(monthEnd) ? monthEnd : leave.getEndDate();

            BigDecimal leaveDays;
            if (Boolean.TRUE.equals(leave.getIsHalfDay())) {
                leaveDays = BigDecimal.valueOf(0.5);
            } else {
                double days = 0;
                LocalDate date = overlapStart;

                // Get holiday dates in the overlap range
                Set<LocalDate> holidayDates = new HashSet<>();
                if (settings != null && (settings.getIncludeNationalHolidays() || settings.getIncludeStateHolidays())) {
                    List<com.sonixhr.entity.leave.PublicHoliday> holidays = publicHolidayRepo
                            .findByTenantIdAndHolidayDateBetween(tenantId, overlapStart, overlapEnd);
                    for (com.sonixhr.entity.leave.PublicHoliday h : holidays) {
                        boolean isNational = "NATIONAL".equalsIgnoreCase(h.getType())
                                && settings.getIncludeNationalHolidays();
                        boolean isState = settings.getIncludeStateHolidays() &&
                                settings.getState() != null &&
                                settings.getState().name().equalsIgnoreCase(h.getRegion());
                        if (isNational || isState) {
                            holidayDates.add(h.getHolidayDate());
                        }
                    }
                }

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
