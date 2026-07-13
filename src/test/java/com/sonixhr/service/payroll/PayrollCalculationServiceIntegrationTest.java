package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.payroll.TaxRegime;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.attendance.ManualAttendanceRepository;
import com.sonixhr.service.common.AuditLogService;
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

    private PayrollCalculationService payrollCalculationService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        payrollCalculationService = new PayrollCalculationService(
                statutoryRateConfigRepo,
                statePtConfigRepo,
                tenantPayrollConfigRepo,
                tenantSalaryStructureRepo,
                employeeSalaryProfileRepo,
                payrunRepo,
                payrunConfigRepo,
                payslipRepo,
                payslipItemRepo,
                componentDefinitionRepo,
                leaveCalculator,
                overtimeCalculator,
                snapshotService,
                statutoryCalculator,
                salaryComponentCalculator,
                payslipGenerator,
                tdsCalculator,
                loanRecoveryCalculator,
                reimbursementCalculator,
                auditLogService
        );
        when(loanRecoveryCalculator.calculateMonthlyRecovery(any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    public void testProcessPayrun_MultiSegment_CalculatesTdsOnceWithMergedGross() {
        Long tenantId = 100L;
        int month = 4;
        int year = 2026;

        com.sonixhr.entity.tenant.Tenant tenant = new com.sonixhr.entity.tenant.Tenant();
        tenant.setId(tenantId);

        Payrun payrun = Payrun.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .month(month)
                .year(year)
                .status("DRAFT")
                .version(1)
                .build();

        TenantPayrollConfig tenantConfig = new TenantPayrollConfig();
        tenantConfig.setDefaultTaxRegime("NEW_REGIME");
        tenantConfig.setTenant(tenant);

        Employee employee = new Employee();
        employee.setId(1L);

        // Segment 1: April 1 to April 15 (OLD regime, ₹50,000 ctc)
        EmployeeSalaryProfile profile1 = EmployeeSalaryProfile.builder()
                .employee(employee)
                .taxRegime("OLD_REGIME")
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .monthlyCtc(BigDecimal.valueOf(50000))
                .build();

        // Segment 2: April 16 to April 30 (NEW regime, ₹60,000 ctc)
        EmployeeSalaryProfile profile2 = EmployeeSalaryProfile.builder()
                .employee(employee)
                .taxRegime("NEW_REGIME")
                .effectiveFrom(LocalDate.of(2026, 4, 16))
                .monthlyCtc(BigDecimal.valueOf(60000))
                .build();

        List<EmployeeSalaryProfile> profiles = Arrays.asList(profile1, profile2);

        // Setup mock configurations and lookups
        when(payrunRepo.findByTenantAndMonthAndYear(tenantId, month, year)).thenReturn(Optional.empty());
        when(payrunRepo.findLatestVersionNumber(tenantId, month, year)).thenReturn(0);
        when(payrunRepo.save(any(Payrun.class))).thenReturn(payrun);
        
        when(tenantPayrollConfigRepo.findActiveByTenantAndDate(eq(tenantId), any(LocalDate.class)))
                .thenReturn(Optional.of(tenantConfig));
        
        List<TenantSalaryStructure> structure = new ArrayList<>();
        structure.add(TenantSalaryStructure.builder()
                .componentCode("BASIC")
                .evaluationOrder(1)
                .isTaxable(true)
                .build());
        when(tenantSalaryStructureRepo.findActiveByTenantAndDate(eq(tenantId), any(LocalDate.class)))
                .thenReturn(structure);
                
        when(employeeSalaryProfileRepo.findActiveProfilesByTenantInPeriod(eq(tenantId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(profiles);

        // Setup segment pay mocks
        PeriodPayData seg1Data = new PeriodPayData();
        seg1Data.setGrossEarnings(BigDecimal.valueOf(25000));
        seg1Data.setTaxableGrossEarnings(BigDecimal.valueOf(20000)); // taxable portion of Segment 1

        PeriodPayData seg2Data = new PeriodPayData();
        seg2Data.setGrossEarnings(BigDecimal.valueOf(30000));
        seg2Data.setTaxableGrossEarnings(BigDecimal.valueOf(25000)); // taxable portion of Segment 2

        when(salaryComponentCalculator.computePeriodPayData(
                eq(profile1), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 15)), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(seg1Data);

        when(salaryComponentCalculator.computePeriodPayData(
                eq(profile2), eq(LocalDate.of(2026, 4, 16)), eq(LocalDate.of(2026, 4, 30)), anyInt(),
                any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(seg2Data);

        // Setup mock TDS returns
        BigDecimal expectedTds = BigDecimal.valueOf(1250).setScale(2);
        when(tdsCalculator.calculateMonthlyTds(
                eq(employee), eq(tenantId), eq(TaxRegime.NEW_REGIME), eq(BigDecimal.valueOf(45000)), eq(month), eq(year)))
                .thenReturn(expectedTds);

        // Execute payrun processing
        payrollCalculationService.processPayrun(tenantId, month, year);

        // 1. Verify overtime calculation is run first on merged payload
        verify(overtimeCalculator).calculateOvertime(eq(employee), eq(tenantConfig), eq(LocalDate.of(2026, 4, 1)), eq(LocalDate.of(2026, 4, 30)), any());

        // 2. Verify TdsCalculator was called EXACTLY ONCE
        // Expected taxable gross is Segment 1 (20,000) + Segment 2 (25,000) = 45,000
        // Expected regime is NEW_REGIME (last segment's regime)
        verify(tdsCalculator, times(1)).calculateMonthlyTds(
                eq(employee), eq(tenantId), eq(TaxRegime.NEW_REGIME), eq(BigDecimal.valueOf(45000)), eq(4), eq(2026));

        // 3. Verify that the correct TDS value was passed to payslip generator
        ArgumentCaptor<PeriodPayData> dataCaptor = ArgumentCaptor.forClass(PeriodPayData.class);
        verify(payslipGenerator).persistPayslip(
                eq(payrun), eq(employee), eq(tenantConfig), dataCaptor.capture(), any(), any(), any());

        PeriodPayData finalData = dataCaptor.getValue();
        assertEquals(expectedTds, finalData.getComponentValues().get("TDS"));
        assertEquals(expectedTds, finalData.getTotalDeductions());
    }
}
