package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.IndianState;
import com.sonixhr.enums.payroll.LoanStatus;
import com.sonixhr.enums.payroll.TaxRegime;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.service.leave.LeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FnfSettlementService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    private final TenantPayrollConfigRepository tenantPayrollConfigRepo;
    private final TenantSalaryStructureRepository tenantSalaryStructureRepo;
    private final StatutoryRateConfigRepository statutoryRateConfigRepo;
    private final StateProfessionalTaxConfigRepository statePtConfigRepo;
    private final SalaryComponentDefinitionRepository componentDefinitionRepo;
    private final SalaryComponentCalculator salaryComponentCalculator;
    private final GratuityCalculator gratuityCalculator;
    private final LeaveEncashmentCalculator leaveEncashmentCalculator;
    private final TdsCalculator tdsCalculator;
    private final LoanAdvanceRepository loanAdvanceRepository;
    private final PayslipItemRepository payslipItemRepo;
    private final FnfSettlementRepository fnfSettlementRepository;
    private final FnfSettlementItemRepository fnfSettlementItemRepository;
    private final LeaveService leaveService;

    @Transactional
    public FnfSettlement processFnfSettlement(Long employeeId, Long tenantId, LocalDate terminationDate) {
        // Idempotency check: prevent duplicate non-cancelled settlements
        Optional<FnfSettlement> existingOpt = fnfSettlementRepository.findByEmployeeIdAndTenantId(employeeId, tenantId);
        if (existingOpt.isPresent()) {
            FnfSettlement existing = existingOpt.get();
            if (!"CANCELLED".equalsIgnoreCase(existing.getStatus())) {
                throw new BusinessException("DUPLICATE_SETTLEMENT", "An active F&F settlement already exists for this employee.");
            }
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        LocalDate monthStart = LocalDate.of(terminationDate.getYear(), terminationDate.getMonthValue(), 1);
        LocalDate monthEnd = YearMonth.of(terminationDate.getYear(), terminationDate.getMonthValue()).atEndOfMonth();
        int totalDaysInMonth = monthEnd.getDayOfMonth();

        // Fetch structures and configs (with fallback to earliest config if none found for date)
        TenantPayrollConfig tenantConfig = tenantPayrollConfigRepo.findActiveByTenantAndDate(tenantId, monthStart)
                .orElseGet(() -> tenantPayrollConfigRepo.findActiveByTenant(tenantId).stream()
                        .min(Comparator.comparing(TenantPayrollConfig::getEffectiveFrom))
                        .orElseThrow(() -> new IllegalArgumentException("Tenant payroll configuration not found")));

        List<TenantSalaryStructure> salaryStructure = tenantSalaryStructureRepo.findActiveByTenantAndDate(tenantId, monthStart);
        if (salaryStructure.isEmpty()) {
            salaryStructure = tenantSalaryStructureRepo.findByTenantPayrollConfigId(tenantConfig.getId());
        }
        List<TenantSalaryStructure> orderedStructure = new ArrayList<>(salaryStructure);
        orderedStructure.sort(Comparator.comparingInt(TenantSalaryStructure::getEvaluationOrder));

        List<StatutoryRateConfig> statutoryRates = statutoryRateConfigRepo.findActiveByDate(monthStart);
        List<StateProfessionalTaxConfig> ptSlabs = statePtConfigRepo.findAll();
        Map<IndianState, List<StateProfessionalTaxConfig>> ptSlabsByState = ptSlabs.stream()
                .filter(s -> s.getStateCode() != null)
                .collect(Collectors.groupingBy(StateProfessionalTaxConfig::getStateCode));

        List<SalaryComponentDefinition> componentDefs = componentDefinitionRepo.findAllowedByTenantAndDate(tenantId, monthStart);
        Map<String, String> customComponentTypes = fetchCustomComponentTypes(componentDefs);
        Map<String, String> customComponentNames = fetchCustomComponentNames(componentDefs);
        Map<String, String> customComponentFormulas = componentDefs.stream()
                .filter(d -> d.getFormulaExpression() != null)
                .collect(Collectors.toMap(
                        d -> d.getComponentCode().toUpperCase(),
                        d -> d.getFormulaExpression(),
                        (a, b) -> a
                ));

        EmployeeSalaryProfile activeProfile = resolveActiveProfile(tenantId, employeeId, monthStart, monthEnd);

        // Calculate unprorated basic to use as lastDrawnBasic
        PeriodPayData fullMonthData = salaryComponentCalculator.computePeriodPayData(
                activeProfile, monthStart, monthEnd, totalDaysInMonth,
                tenantConfig, orderedStructure, statutoryRates, ptSlabsByState, BigDecimal.ZERO,
                terminationDate.getMonthValue(), terminationDate.getYear(),
                customComponentTypes, customComponentNames, customComponentFormulas);
        BigDecimal lastDrawnBasic = fullMonthData.getComponentValues().getOrDefault("BASIC", BigDecimal.ZERO);

        // 1. Gratuity
        GratuityCalculator.GratuityResult gratuityResult = gratuityCalculator.calculateGratuity(employee, lastDrawnBasic, terminationDate);

        // 2. Leave Encashment
        int earnedLeaveDays = fetchEarnedLeaveDays(employeeId, tenantId);

        long fullYears = ChronoUnit.YEARS.between(employee.getHireDate(), terminationDate);
        long remainingMonths = ChronoUnit.MONTHS.between(
                employee.getHireDate().plusYears(fullYears), terminationDate);
        int yearsOfService = (int) (fullYears + (remainingMonths >= 6 ? 1 : 0));

        BigDecimal perDayRate = lastDrawnBasic.divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_EVEN);
        BigDecimal avgBasic = getAvgMonthlySalaryLast10Months(employeeId, tenantId, lastDrawnBasic, terminationDate);

        LeaveEncashmentCalculator.LeaveEncashmentResult leaveEncashmentResult = leaveEncashmentCalculator.calculateEncashment(
                employee, lastDrawnBasic, perDayRate, earnedLeaveDays, yearsOfService, avgBasic);

        // 3. Prorate final month's salary up to terminationDate
        PeriodPayData proratedFinalMonthSalary = salaryComponentCalculator.computePeriodPayData(
                activeProfile, monthStart, terminationDate, totalDaysInMonth,
                tenantConfig, orderedStructure, statutoryRates, ptSlabsByState, BigDecimal.ZERO,
                terminationDate.getMonthValue(), terminationDate.getYear(),
                customComponentTypes, customComponentNames, customComponentFormulas);

        // 4. Sum outstanding loan balances and close them
        BigDecimal totalLoanOutstanding = recoverOutstandingLoans(employeeId, tenantId);

        // 5. Combine taxable excess from gratuity + leave encashment into this month's non-recurring taxable gross
        BigDecimal fnfNonRecurringTaxable = gratuityResult.getTaxableAmount()
                .add(leaveEncashmentResult.getTaxableAmount());
        BigDecimal totalTaxableGross = proratedFinalMonthSalary.getTaxableGrossEarnings().add(fnfNonRecurringTaxable);

        TaxRegime regime = TaxRegime.NEW_REGIME;
        if (activeProfile.getTaxRegime() != null && activeProfile.getTaxRegime().toUpperCase().contains("OLD")) {
            regime = TaxRegime.OLD_REGIME;
        }

        BigDecimal fnfTds = tdsCalculator.calculateMonthlyTds(
                employee, tenantId, regime,
                totalTaxableGross,
                fnfNonRecurringTaxable,
                terminationDate.getMonthValue(), terminationDate.getYear());

        // 6. Net Settlement
        BigDecimal grossProrated = proratedFinalMonthSalary.getGrossEarnings();
        BigDecimal deductionsProrated = proratedFinalMonthSalary.getTotalDeductions();

        BigDecimal netSettlement = grossProrated
                .add(gratuityResult.getGratuityAmount())
                .add(leaveEncashmentResult.getActualAmount())
                .subtract(deductionsProrated)
                .subtract(totalLoanOutstanding)
                .subtract(fnfTds)
                .setScale(2, RoundingMode.HALF_EVEN);

        FnfSettlement settlement = FnfSettlement.builder()
                .tenant(activeProfile.getTenant())
                .employee(employee)
                .terminationDate(terminationDate)
                .lastDrawnBasic(lastDrawnBasic)
                .gratuityAmount(gratuityResult.getGratuityAmount())
                .gratuityExempt(gratuityResult.getExemptAmount())
                .gratuityTaxable(gratuityResult.getTaxableAmount())
                .encashmentAmount(leaveEncashmentResult.getActualAmount())
                .encashmentExempt(leaveEncashmentResult.getExemptAmount())
                .encashmentTaxable(leaveEncashmentResult.getTaxableAmount())
                .proratedSalary(grossProrated)
                .loanRecovery(totalLoanOutstanding)
                .totalTds(fnfTds)
                .netSettlement(netSettlement)
                .status("DRAFT")
                .processedAt(LocalDateTime.now())
                .build();

        settlement = fnfSettlementRepository.save(settlement);

        List<FnfSettlementItem> items = buildSettlementItems(
                settlement, grossProrated, monthStart, terminationDate,
                gratuityResult, leaveEncashmentResult, totalLoanOutstanding, fnfTds,
                proratedFinalMonthSalary
        );

        fnfSettlementItemRepository.saveAll(items);
        settlement.setItems(items);

        return settlement;
    }

    private Map<String, String> fetchCustomComponentTypes(List<SalaryComponentDefinition> componentDefs) {
        return componentDefs.stream()
                .collect(Collectors.toMap(d -> d.getComponentCode().toUpperCase(), d -> d.getComponentType().toUpperCase(), (a, b) -> a));
    }

    private Map<String, String> fetchCustomComponentNames(List<SalaryComponentDefinition> componentDefs) {
        return componentDefs.stream()
                .collect(Collectors.toMap(d -> d.getComponentCode().toUpperCase(), d -> d.getComponentName(), (a, b) -> a));
    }

    private EmployeeSalaryProfile resolveActiveProfile(Long tenantId, Long employeeId, LocalDate monthStart, LocalDate monthEnd) {
        List<EmployeeSalaryProfile> profiles = employeeSalaryProfileRepo.findActiveProfilesByTenantInPeriod(tenantId, monthStart, monthEnd)
                .stream()
                .filter(p -> p.getEmployee().getId().equals(employeeId))
                .sorted(Comparator.comparing(EmployeeSalaryProfile::getEffectiveFrom))
                .collect(Collectors.toList());

        if (profiles.isEmpty()) {
            List<EmployeeSalaryProfile> empProfiles = employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(employeeId);
            if (!empProfiles.isEmpty()) {
                return empProfiles.get(0);
            } else {
                throw new IllegalArgumentException("No salary profile active in termination month");
            }
        }
        return profiles.get(profiles.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private int fetchEarnedLeaveDays(Long employeeId, Long tenantId) {
        try {
            Map<String, Object> balanceMap = leaveService.getLeaveBalanceWithTenantSettings(employeeId, tenantId);
            if (balanceMap != null && balanceMap.containsKey("EARNED")) {
                Map<String, Object> earnedDetails = (Map<String, Object>) balanceMap.get("EARNED");
                if (earnedDetails != null && earnedDetails.containsKey("available")) {
                    return ((Number) earnedDetails.get("available")).intValue();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve leave balance for employee ID: {}", employeeId, e);
        }
        return 0;
    }

    private BigDecimal recoverOutstandingLoans(Long employeeId, Long tenantId) {
        List<Object[]> loanRecoveryData = loanAdvanceRepository.findActiveLoansWithRecoverySum(employeeId, tenantId);
        BigDecimal totalLoanOutstanding = BigDecimal.ZERO;
        for (Object[] row : loanRecoveryData) {
            LoanAdvance loan = (LoanAdvance) row[0];
            BigDecimal recoveredSoFar = (BigDecimal) row[1];
            if (recoveredSoFar == null) {
                recoveredSoFar = BigDecimal.ZERO;
            }
            BigDecimal outstanding = loan.getPrincipalAmount().subtract(recoveredSoFar).max(BigDecimal.ZERO);
            totalLoanOutstanding = totalLoanOutstanding.add(outstanding);
            loan.setStatus(LoanStatus.CLOSED);
            loanAdvanceRepository.save(loan);
        }
        return totalLoanOutstanding;
    }

    private List<FnfSettlementItem> buildSettlementItems(
            FnfSettlement settlement,
            BigDecimal grossProrated,
            LocalDate monthStart,
            LocalDate terminationDate,
            GratuityCalculator.GratuityResult gratuityResult,
            LeaveEncashmentCalculator.LeaveEncashmentResult leaveEncashmentResult,
            BigDecimal totalLoanOutstanding,
            BigDecimal fnfTds,
            PeriodPayData proratedFinalMonthSalary) {
        List<FnfSettlementItem> items = new ArrayList<>();
        items.add(FnfSettlementItem.builder()
                .tenant(settlement.getTenant())
                .fnfSettlement(settlement)
                .componentCode("PRORATED_SALARY")
                .componentName("Prorated Final Month Salary")
                .type("ALLOWANCE")
                .amount(grossProrated)
                .description("Prorated salary from " + monthStart + " to " + terminationDate)
                .build());

        if (gratuityResult.isEligible()) {
            items.add(FnfSettlementItem.builder()
                    .tenant(settlement.getTenant())
                    .fnfSettlement(settlement)
                    .componentCode("GRATUITY")
                    .componentName("Gratuity Payment")
                    .type("ALLOWANCE")
                    .amount(gratuityResult.getGratuityAmount())
                    .description("Taxable: " + gratuityResult.getTaxableAmount() + ", Exempt: " + gratuityResult.getExemptAmount())
                    .build());
        }

        if (leaveEncashmentResult.getActualAmount().compareTo(BigDecimal.ZERO) > 0) {
            items.add(FnfSettlementItem.builder()
                    .tenant(settlement.getTenant())
                    .fnfSettlement(settlement)
                    .componentCode("LEAVE_ENCASHMENT")
                    .componentName("Leave Encashment")
                    .type("ALLOWANCE")
                    .amount(leaveEncashmentResult.getActualAmount())
                    .description("Taxable: " + leaveEncashmentResult.getTaxableAmount() + ", Exempt: " + leaveEncashmentResult.getExemptAmount())
                    .build());
        }

        if (totalLoanOutstanding.compareTo(BigDecimal.ZERO) > 0) {
            items.add(FnfSettlementItem.builder()
                    .tenant(settlement.getTenant())
                    .fnfSettlement(settlement)
                    .componentCode("LOAN_RECOVERY")
                    .componentName("Loan Outstanding Recovery")
                    .type("DEDUCTION")
                    .amount(totalLoanOutstanding)
                    .description("Full settlement of outstanding active loans")
                    .build());
        }

        if (fnfTds.compareTo(BigDecimal.ZERO) > 0) {
            items.add(FnfSettlementItem.builder()
                    .tenant(settlement.getTenant())
                    .fnfSettlement(settlement)
                    .componentCode("FNF_TDS")
                    .componentName("F&F Tax Deducted at Source")
                    .type("DEDUCTION")
                    .amount(fnfTds)
                    .description("TDS on prorated salary plus taxable F&F components")
                    .build());
        }

        // Add regular components from prorated final month salary for transparency
        for (Map.Entry<String, BigDecimal> compEntry : proratedFinalMonthSalary.getComponentValues().entrySet()) {
            if (compEntry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                items.add(FnfSettlementItem.builder()
                        .tenant(settlement.getTenant())
                        .fnfSettlement(settlement)
                        .componentCode("REG_" + compEntry.getKey())
                        .componentName("Regular component: " + compEntry.getKey())
                        .type(compEntry.getKey().contains("EPF") || compEntry.getKey().contains("PT") || compEntry.getKey().contains("TDS") ? "DEDUCTION" : "ALLOWANCE")
                        .amount(compEntry.getValue())
                        .description("Prorated monthly component amount")
                        .build());
            }
        }
        return items;
    }


    BigDecimal getAvgMonthlySalaryLast10Months(Long employeeId, Long tenantId, 
            BigDecimal currentBasic, LocalDate terminationDate) {
        
        // Calculate FY start
        int month = terminationDate.getMonthValue();
        int year = terminationDate.getYear();
        int fyStartYear = (month >= 4) ? year : year - 1;
        LocalDate fyStart = LocalDate.of(fyStartYear, 4, 1);
        
        // Query last 10 months of basic salary from payslips
        List<BigDecimal> last10Basics = payslipItemRepo.findLastBasicSalaries(
                tenantId, employeeId, fyStart, terminationDate, PageRequest.of(0, 10));
        
        if (last10Basics.isEmpty()) {
            log.warn("No payslip history found for employee {}, using current basic: {}", 
                    employeeId, currentBasic);
            return currentBasic;
        }
        
        // Calculate average of available months (could be less than 10)
        BigDecimal sum = last10Basics.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int count = last10Basics.size();
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_EVEN);
    }
}
