package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.exceptions.PayrunLockedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings("null")
public class PayrollCalculationService {

    private final StatutoryRateConfigRepository statutoryRateConfigRepo;
    private final StateProfessionalTaxConfigRepository statePtConfigRepo;
    private final TenantPayrollConfigRepository tenantPayrollConfigRepo;
    private final TenantSalaryStructureRepository tenantSalaryStructureRepo;
    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    private final EmployeeRepository employeeRepository;
    private final PayrunRepository payrunRepo;
    private final PayrunConfigRepository payrunConfigRepo;
    private final SalaryComponentDefinitionRepository componentDefinitionRepo;
    
    private final LeaveCalculator leaveCalculator;
    private final SnapshotService snapshotService;
    private final EmployeePayrunProcessor employeePayrunProcessor;
    private final com.sonixhr.service.common.AuditLogService auditLogService;

    public PayrollCalculationService(
            StatutoryRateConfigRepository statutoryRateConfigRepo,
            StateProfessionalTaxConfigRepository statePtConfigRepo,
            TenantPayrollConfigRepository tenantPayrollConfigRepo,
            TenantSalaryStructureRepository tenantSalaryStructureRepo,
            EmployeeSalaryProfileRepository employeeSalaryProfileRepo,
            EmployeeRepository employeeRepository,
            PayrunRepository payrunRepo,
            PayrunConfigRepository payrunConfigRepo,
            SalaryComponentDefinitionRepository componentDefinitionRepo,
            LeaveCalculator leaveCalculator,
            SnapshotService snapshotService,
            EmployeePayrunProcessor employeePayrunProcessor,
            com.sonixhr.service.common.AuditLogService auditLogService) {
        this.statutoryRateConfigRepo = statutoryRateConfigRepo;
        this.statePtConfigRepo = statePtConfigRepo;
        this.tenantPayrollConfigRepo = tenantPayrollConfigRepo;
        this.tenantSalaryStructureRepo = tenantSalaryStructureRepo;
        this.employeeSalaryProfileRepo = employeeSalaryProfileRepo;
        this.employeeRepository = employeeRepository;
        this.payrunRepo = payrunRepo;
        this.payrunConfigRepo = payrunConfigRepo;
        this.componentDefinitionRepo = componentDefinitionRepo;
        this.leaveCalculator = leaveCalculator;
        this.snapshotService = snapshotService;
        this.employeePayrunProcessor = employeePayrunProcessor;
        this.auditLogService = auditLogService;
    }

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

        // 2. Fetch tenant payroll config (with fallback to earliest config if none found for date)
        TenantPayrollConfig tenantConfig = tenantPayrollConfigRepo.findActiveByTenantAndDate(tenantId, monthStart)
                .orElseGet(() -> tenantPayrollConfigRepo.findActiveByTenant(tenantId).stream()
                        .min(Comparator.comparing(TenantPayrollConfig::getEffectiveFrom))
                        .orElseThrow(() -> new IllegalArgumentException("Tenant payroll configuration not found")));

        // 3. Fetch active salary structure and statutory rates
        List<TenantSalaryStructure> salaryStructure = tenantSalaryStructureRepo.findActiveByTenantAndDate(tenantId, monthStart);
        if (salaryStructure.isEmpty()) {
            salaryStructure = tenantSalaryStructureRepo.findByTenantPayrollConfigId(tenantConfig.getId());
        }
        List<TenantSalaryStructure> orderedStructure = new ArrayList<>(salaryStructure);
        orderedStructure.sort(Comparator.comparingInt(TenantSalaryStructure::getEvaluationOrder));

        List<StatutoryRateConfig> statutoryRates = statutoryRateConfigRepo.findActiveByDate(monthStart);

        // Fetch state PT slabs
        List<StateProfessionalTaxConfig> ptSlabs = statePtConfigRepo.findAll();
        Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState = ptSlabs.stream()
                .filter(s -> s.getStateCode() != null)
                .collect(Collectors.groupingBy(StateProfessionalTaxConfig::getStateCode));

        // 4. Initialize Payrun record in DRAFT
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
        List<EmployeeSalaryProfile> allProfiles = new ArrayList<>(employeeSalaryProfileRepo
                .findActiveProfilesByTenantInPeriod(tenantId, monthStart, monthEnd));

        // Fallback for employees who don't have a profile in the period but have a future profile
        List<Employee> activeEmployees = employeeRepository.findActiveEmployeesByTenantId(tenantId);
        Set<Long> employeesWithProfile = allProfiles.stream()
                .map(p -> p.getEmployee().getId())
                .collect(Collectors.toSet());

        for (Employee emp : activeEmployees) {
            if (!employeesWithProfile.contains(emp.getId())) {
                List<EmployeeSalaryProfile> empProfiles = employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(emp.getId());
                if (!empProfiles.isEmpty()) {
                    allProfiles.add(empProfiles.get(0));
                }
            }
        }

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
                    if (i == 0 && segStart.isAfter(monthEnd)) {
                        segStart = monthStart;
                    }
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

                // Process employee in their own transaction to ensure partial success capability
                employeePayrunProcessor.processEmployee(
                        payrun, employee, segments, totalDaysInMonth, tenantConfig, orderedStructure,
                        statutoryRates, ptSlabsByState, empLopDays, month, year,
                        customComponentTypes, customComponentNames, monthStart, monthEnd
                );

            } catch (Exception e) {
                log.error("Failed to calculate payslip for employee ID: {}", employee.getId(), e);
                payrunErrors.add("Employee ID " + employee.getId() + ": " + e.getMessage());
            }
        }

        // If errors occurred, update Payrun status to PARTIAL_FAILURE instead of rolling back the entire transaction.
        if (!payrunErrors.isEmpty()) {
            log.warn("Payrun processed with warnings/errors: {}", payrunErrors);
            payrun.setStatus("PARTIAL_FAILURE");
            payrun = payrunRepo.save(payrun);
        }

        auditLogService.log(
            tenantConfig.getTenant(),
            "PAYROLL_PROCESSED",
            "payrunStatus",
            null,
            payrun.getStatus(),
            "{\"month\":" + month + ",\"year\":" + year + ",\"payrunId\":\"" + payrun.getId() + "\",\"errors\":\"" + String.join(", ", payrunErrors) + "\"}"
        );

        return payrun;
    }
}
