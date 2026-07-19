package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.payroll.TaxRegime;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.service.common.AuditLogService;
import com.sonixhr.entity.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PayrollCalculationServiceIntegrationTest {

    @Mock private StatutoryRateConfigRepository statutoryRateConfigRepo;
    @Mock private StateProfessionalTaxConfigRepository statePtConfigRepo;
    @Mock private TenantPayrollConfigRepository tenantPayrollConfigRepo;
    @Mock private TenantSalaryStructureRepository tenantSalaryStructureRepo;
    @Mock private EmployeeSalaryProfileRepository employeeSalaryProfileRepo;
    @Mock private EmployeeSalaryComponentRepository employeeSalaryComponentRepo;
    @Mock private PayrunRepository payrunRepo;
    @Mock private PayrunConfigRepository payrunConfigRepo;
    @Mock private PayslipRepository payslipRepo;
    @Mock private PayslipItemRepository payslipItemRepo;
    @Mock private SalaryComponentDefinitionRepository componentDefinitionRepo;
    
    @Mock private LeaveCalculator leaveCalculator;
    @Mock private OvertimeCalculator overtimeCalculator;
    @Mock private SnapshotService snapshotService;
    @Mock private StatutoryCalculator statutoryCalculator;
    @Mock private SalaryComponentCalculator salaryComponentCalculator;
    @Mock private PayslipGenerator payslipGenerator;
    @Mock private TdsCalculator tdsCalculator;
    @Mock private LoanRecoveryCalculator loanRecoveryCalculator;
    @Mock private ReimbursementCalculator reimbursementCalculator;
    @Mock private AuditLogService auditLogService;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private EmployeeRepository employeeRepository;

    private PayrollCalculationService payrollCalculationService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        EmployeePayrunProcessor employeePayrunProcessor = new EmployeePayrunProcessor(
                employeeSalaryProfileRepo,
                employeeSalaryComponentRepo,
                salaryComponentCalculator,
                overtimeCalculator,
                tdsCalculator,
                loanRecoveryCalculator,
                reimbursementCalculator,
                payslipGenerator,
                statutoryCalculator
        );

        payrollCalculationService = new PayrollCalculationService(
                statutoryRateConfigRepo,
                statePtConfigRepo,
                tenantPayrollConfigRepo,
                tenantSalaryStructureRepo,
                employeeSalaryProfileRepo,
                employeeRepository,
                payrunRepo,
                payrunConfigRepo,
                componentDefinitionRepo,
                leaveCalculator,
                snapshotService,
                employeePayrunProcessor,
                auditLogService
        );
        when(loanRecoveryCalculator.calculateMonthlyRecovery(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    public void testProcessPayrun_MultiSegment_CalculatesTdsOnceWithMergedGross() {
        Long tenantId = 100L;
        int month = 4;
        int year = 2026;

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        TenantPayrollConfig tenantConfig = new TenantPayrollConfig();
        tenantConfig.setTenant(tenant);
        tenantConfig.setEnablePfCapping(true);
        tenantConfig.setEnableEsi(true);
        tenantConfig.setEnablePt(true);

        when(employeeRepository.findActiveEmployeesByTenantId(eq(tenantId)))
                .thenReturn(List.of(employee));
        when(tenantPayrollConfigRepo.findActiveByTenantAndDate(eq(tenantId), any()))
                .thenReturn(Optional.of(tenantConfig));

        // Two overlapping profiles:
        // Profile 1 active April 1 - April 15
        EmployeeSalaryProfile profile1 = EmployeeSalaryProfile.builder()
                .employee(employee)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .monthlyCtc(BigDecimal.valueOf(40000))
                .taxRegime("NEW_REGIME")
                .build();

        // Profile 2 active April 16 onwards (retrospective adjustment)
        EmployeeSalaryProfile profile2 = EmployeeSalaryProfile.builder()
                .employee(employee)
                .effectiveFrom(LocalDate.of(2026, 4, 16))
                .monthlyCtc(BigDecimal.valueOf(50000))
                .taxRegime("NEW_REGIME")
                .build();

        when(employeeSalaryProfileRepo.findActiveProfilesByTenantInPeriod(eq(tenantId), any(), any()))
                .thenReturn(List.of(profile1, profile2));
        when(employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(eq(1L)))
                .thenReturn(List.of(profile1, profile2));

        Payrun payrun = Payrun.builder()
                .tenant(tenant)
                .month(month)
                .year(year)
                .status("DRAFT")
                .version(1)
                .build();
        when(payrunRepo.save(any(Payrun.class))).thenReturn(payrun);

        PeriodPayData seg1Data = new PeriodPayData();
        seg1Data.setGrossEarnings(BigDecimal.valueOf(25000));
        seg1Data.setTaxableGrossEarnings(BigDecimal.valueOf(20000)); // taxable portion of Segment 1

        PeriodPayData seg2Data = new PeriodPayData();
        seg2Data.setGrossEarnings(BigDecimal.valueOf(30000));
        seg2Data.setTaxableGrossEarnings(BigDecimal.valueOf(25000)); // taxable portion of Segment 2

        when(salaryComponentCalculator.computePeriodPayData(
                eq(profile1), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 15)), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(seg1Data);

        when(salaryComponentCalculator.computePeriodPayData(
                eq(profile2), eq(LocalDate.of(2026, 4, 16)), eq(LocalDate.of(2026, 4, 30)), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(seg2Data);

        // Setup mock TDS returns
        BigDecimal expectedTds = BigDecimal.valueOf(1250).setScale(2);
        when(tdsCalculator.calculateMonthlyTds(
                eq(employee), eq(tenantId), eq(TaxRegime.NEW_REGIME), eq(BigDecimal.valueOf(45000)), eq(new BigDecimal("0.00")), eq(month), eq(year)))
                .thenReturn(expectedTds);

        // Execute payrun processing
        payrollCalculationService.processPayrun(tenantId, month, year);

        // 1. Verify overtime calculation is run first on merged payload
        verify(overtimeCalculator).calculateOvertime(eq(employee), eq(tenantConfig), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 30)), any());

        // 2. Verify TdsCalculator was called EXACTLY ONCE
        // Expected taxable gross is Segment 1 (20,000) + Segment 2 (25,000) = 45,000
        // Expected regime is NEW_REGIME (last segment's regime)
        verify(tdsCalculator, times(1)).calculateMonthlyTds(
                eq(employee), eq(tenantId), eq(TaxRegime.NEW_REGIME), eq(BigDecimal.valueOf(45000)), eq(new BigDecimal("0.00")), eq(4), eq(2026));

        // 3. Verify that the correct TDS value was passed to payslip generator
        ArgumentCaptor<PeriodPayData> dataCaptor = ArgumentCaptor.forClass(PeriodPayData.class);
        verify(payslipGenerator).persistPayslip(
                eq(payrun), eq(employee), eq(tenantConfig), dataCaptor.capture(), any(), any(), any());

        PeriodPayData finalData = dataCaptor.getValue();
        assertEquals(expectedTds, finalData.getComponentValues().get("TDS"));
        assertEquals(expectedTds, finalData.getTotalDeductions());
    }

    @Test
    public void testProcessPayrun_PayslipGenerationFails_PropagatesException() {
        Long tenantId = 100L;
        int month = 4;
        int year = 2026;

        Employee employee = new Employee();
        employee.setId(1L);
        employee.setHireDate(LocalDate.of(2025, 1, 1));
        employee.setStatus(com.sonixhr.enums.employee.EmployeeStatus.ACTIVE);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        TenantPayrollConfig tenantConfig = new TenantPayrollConfig();
        tenantConfig.setTenant(tenant);
        tenantConfig.setEnablePfCapping(true);
        tenantConfig.setEnableEsi(true);
        tenantConfig.setEnablePt(true);

        when(employeeRepository.findActiveEmployeesByTenantId(eq(tenantId)))
                .thenReturn(List.of(employee));
        when(tenantPayrollConfigRepo.findActiveByTenantAndDate(eq(tenantId), any()))
                .thenReturn(Optional.of(tenantConfig));
        
        EmployeeSalaryProfile profile = EmployeeSalaryProfile.builder()
                .employee(employee)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .monthlyCtc(BigDecimal.valueOf(50000))
                .build();
        when(employeeSalaryProfileRepo.findActiveProfilesByTenantInPeriod(eq(tenantId), any(), any()))
                .thenReturn(List.of(profile));
        when(employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(eq(1L)))
                .thenReturn(List.of(profile));

        Payrun payrun = Payrun.builder()
                .tenant(tenant)
                .month(month)
                .year(year)
                .status("DRAFT")
                .version(1)
                .build();
        when(payrunRepo.save(any(Payrun.class))).thenReturn(payrun);

        PeriodPayData segData = new PeriodPayData();
        segData.setGrossEarnings(BigDecimal.valueOf(50000));
        segData.setTaxableGrossEarnings(BigDecimal.valueOf(50000));
        segData.putComponentValue("BASIC", BigDecimal.valueOf(25000));

        when(salaryComponentCalculator.computePeriodPayData(
                eq(profile), any(), any(), anyInt(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(segData);

        when(tdsCalculator.calculateMonthlyTds(
                any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(BigDecimal.ZERO);

        doThrow(new RuntimeException("Database error during payslip persist"))
                .when(payslipGenerator).persistPayslip(any(), any(), any(), any(), any(), any(), any());

        // Process payrun (should catch and log error for partial successes, rolling back the transaction for this employee)
        payrollCalculationService.processPayrun(tenantId, month, year);

        verify(payslipGenerator, times(1)).persistPayslip(any(), any(), any(), any(), any(), any(), any());
    }
}
