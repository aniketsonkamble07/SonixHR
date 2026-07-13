package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.*;
import com.sonixhr.enums.payroll.TaxRegime;
import com.sonixhr.enums.payroll.DeclarationStatus;
import com.sonixhr.repository.payroll.*;
import com.sonixhr.repository.tenant.TenantRepository;
import com.sonixhr.repository.platform.PlatformMigrationFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TdsCalculatorTest {

    @Mock
    private TaxRegimeSlabConfigRepository taxSlabConfigRepo;
    @Mock
    private TaxDeclarationRepository taxDeclarationRepo;
    @Mock
    private TaxDeclarationSectionRuleRepository sectionRuleRepo;
    @Mock
    private PayslipRepository payslipRepo;
    @Mock
    private PayslipItemRepository payslipItemRepo;

    @InjectMocks
    private TdsCalculator tdsCalculator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCalculateMonthlyTds_NewRegime() {
        Employee employee = new Employee();
        employee.setId(1L);

        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(300000.0), BigDecimal.ZERO),
                new TaxSlabRow(BigDecimal.valueOf(300000.0), BigDecimal.valueOf(700000.0), BigDecimal.valueOf(5.0)),
                new TaxSlabRow(BigDecimal.valueOf(700000.0), BigDecimal.valueOf(1000000.0), BigDecimal.valueOf(10.0)),
                new TaxSlabRow(BigDecimal.valueOf(1000000.0), BigDecimal.valueOf(1200000.0), BigDecimal.valueOf(15.0)),
                new TaxSlabRow(BigDecimal.valueOf(1200000.0), BigDecimal.valueOf(1500000.0), BigDecimal.valueOf(20.0)),
                new TaxSlabRow(BigDecimal.valueOf(1500000.0), null, BigDecimal.valueOf(30.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.NEW_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.valueOf(75000.0))
                .rebateLimit(BigDecimal.valueOf(700000.0))
                .rebateMaxAmount(BigDecimal.valueOf(25000.0))
                .cessPercent(BigDecimal.valueOf(4.0))
                .build();

        when(taxSlabConfigRepo.findByFinancialYearAndRegime("2026-27", TaxRegime.NEW_REGIME))
                .thenReturn(Optional.of(config));

        // April 2026 (Month 4): 12 months remaining
        BigDecimal monthlyTaxableGross = BigDecimal.valueOf(100000.0);

        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, 1L, TaxRegime.NEW_REGIME, monthlyTaxableGross, 4, 2026);

        assertNotNull(tds);
        // Annual projected gross = 1,200,000
        // Less standard deduction of 75,000 = 1,125,000 taxable income
        // Base tax:
        // 0 to 300,000 -> 0
        // 300,000 to 700,000 -> 20,000
        // 700,000 to 1,000,000 -> 30,000
        // 1,000,000 to 1,125,000 -> 18,750
        // Base tax = 68,750
        // Cess (4%) = 2,750
        // Total Tax = 71,500
        // Monthly TDS = 71,500 / 12 = 5,958.33
        assertEquals(new BigDecimal("5958.33"), tds);
    }

    @Test
    public void testCalculateMonthlyTds_ZeroTaxForIncomeBelowRebateLimit() {
        Employee employee = new Employee();
        employee.setId(1L);

        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(400000.0), BigDecimal.ZERO),
                new TaxSlabRow(BigDecimal.valueOf(400000.0), BigDecimal.valueOf(800000.0), BigDecimal.valueOf(5.0)),
                new TaxSlabRow(BigDecimal.valueOf(800000.0), BigDecimal.valueOf(1200000.0), BigDecimal.valueOf(10.0)),
                new TaxSlabRow(BigDecimal.valueOf(1200000.0), null, BigDecimal.valueOf(15.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.NEW_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.valueOf(75000.0))
                .rebateLimit(BigDecimal.valueOf(1200000.0))
                .rebateMaxAmount(BigDecimal.valueOf(60000.0))
                .cessPercent(BigDecimal.valueOf(4.0))
                .build();

        when(taxSlabConfigRepo.findByFinancialYearAndRegime("2026-27", TaxRegime.NEW_REGIME))
                .thenReturn(Optional.of(config));

        // Let's assume a month has current taxable gross of 75,000 (annual projected = 900,000)
        // remaining months = 12
        BigDecimal monthlyTaxableGross = BigDecimal.valueOf(75000.0);

        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, 1L, TaxRegime.NEW_REGIME, monthlyTaxableGross, 4, 2026);

        assertNotNull(tds);
        // Annual projected gross = 900,000
        // Less standard deduction of 75,000 = 825,000 taxable income
        // Since 825,000 <= 1,200,000 (rebate limit), base tax (22,500) is fully offset by rebate.
        // Tax is exactly 0.00
        assertEquals(BigDecimal.ZERO.setScale(2), tds);
    }

    @Test
    public void testCalculateMonthlyTds_MarginalReliefZone() {
        Employee employee = new Employee();
        employee.setId(1L);

        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(400000.0), BigDecimal.ZERO),
                new TaxSlabRow(BigDecimal.valueOf(400000.0), BigDecimal.valueOf(800000.0), BigDecimal.valueOf(5.0)),
                new TaxSlabRow(BigDecimal.valueOf(800000.0), BigDecimal.valueOf(1200000.0), BigDecimal.valueOf(10.0)),
                new TaxSlabRow(BigDecimal.valueOf(1200000.0), null, BigDecimal.valueOf(15.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.NEW_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.valueOf(75000.0))
                .rebateLimit(BigDecimal.valueOf(1200000.0))
                .rebateMaxAmount(BigDecimal.valueOf(60000.0))
                .cessPercent(BigDecimal.valueOf(4.0))
                .build();

        when(taxSlabConfigRepo.findByFinancialYearAndRegime("2026-27", TaxRegime.NEW_REGIME))
                .thenReturn(Optional.of(config));

        // Let's assume a month has current taxable gross of 106,666.67 (annual projected = 1,280,000)
        // Less standard deduction of 75,000 = 1,205,000 taxable income
        // Base tax on 1,205,000 is:
        // 400k to 800k (5%) = 20,000
        // 800k to 1,200k (10%) = 40,000
        // 1,200k to 1,205k (15%) = 750
        // Base tax before rebate/relief = 60,750
        // Excess income above rebate limit = 1,205,000 - 1,200,000 = 5,000
        // Since tax (60,750) > excess (5,000), marginal relief caps the base tax to the excess (5,000)
        // Plus 4% cess on 5,000 = 200. Total annual tax = 5,200
        // Divided by 12 remaining months = 433.33
        BigDecimal monthlyTaxableGross = BigDecimal.valueOf(106666.66);

        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, 1L, TaxRegime.NEW_REGIME, monthlyTaxableGross, 4, 2026);

        assertNotNull(tds);
        assertEquals(new BigDecimal("433.33"), tds);
    }

    @Test
    public void testCalculateMonthlyTds_OldRegimeWithDeductions() {
        Employee employee = new Employee();
        employee.setId(2L);

        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(250000.0), BigDecimal.ZERO),
                new TaxSlabRow(BigDecimal.valueOf(250000.0), BigDecimal.valueOf(500000.0), BigDecimal.valueOf(5.0)),
                new TaxSlabRow(BigDecimal.valueOf(500000.0), BigDecimal.valueOf(1000000.0), BigDecimal.valueOf(20.0)),
                new TaxSlabRow(BigDecimal.valueOf(1000000.0), null, BigDecimal.valueOf(30.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.OLD_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.valueOf(50000.0))
                .rebateLimit(BigDecimal.valueOf(500000.0))
                .rebateMaxAmount(BigDecimal.valueOf(12500.0))
                .cessPercent(BigDecimal.valueOf(4.0))
                .build();

        when(taxSlabConfigRepo.findByFinancialYearAndRegime("2026-27", TaxRegime.OLD_REGIME))
                .thenReturn(Optional.of(config));

        TaxDeclarationLineItem item80c = TaxDeclarationLineItem.builder()
                .section("80C")
                .declaredAmount(BigDecimal.valueOf(180000.0))
                .approvedAmount(BigDecimal.valueOf(180000.0))
                .build();

        TaxDeclaration declaration = TaxDeclaration.builder()
                .employeeId(2L)
                .financialYear("2026-27")
                .status(DeclarationStatus.VERIFIED)
                .lineItems(List.of(item80c))
                .build();

        when(taxDeclarationRepo.findByEmployeeIdAndFinancialYearAndStatus(2L, "2026-27", DeclarationStatus.VERIFIED))
                .thenReturn(Optional.of(declaration));

        when(sectionRuleRepo.findCap("80C", TaxRegime.OLD_REGIME, "2026-27"))
                .thenReturn(Optional.of(BigDecimal.valueOf(150000.0)));

        BigDecimal monthlyTaxableGross = BigDecimal.valueOf(120000.0);

        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, 1L, TaxRegime.OLD_REGIME, monthlyTaxableGross, 4, 2026);

        assertNotNull(tds);
        assertEquals(new BigDecimal("15990.00"), tds);
    }

    @Test
    public void testCalculateMonthlyTds_YtdValuesFetchedCorrectly() {
        Employee employee = new Employee();
        employee.setId(3L);

        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(300000.0), BigDecimal.ZERO),
                new TaxSlabRow(BigDecimal.valueOf(300000.0), null, BigDecimal.valueOf(5.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.NEW_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.ZERO)
                .rebateLimit(BigDecimal.ZERO)
                .rebateMaxAmount(BigDecimal.ZERO)
                .cessPercent(BigDecimal.ZERO)
                .build();

        when(taxSlabConfigRepo.findByFinancialYearAndRegime("2026-27", TaxRegime.NEW_REGIME))
                .thenReturn(Optional.of(config));

        int startVal = 2026 * 12 + 4;
        int endVal = 2026 * 12 + 9;
        when(payslipRepo.sumTaxableGrossForEmployeeInFinancialYear(1L, 3L, startVal, endVal))
                .thenReturn(BigDecimal.valueOf(450000.0));
        when(payslipItemRepo.sumTdsForEmployeeInFinancialYear(1L, 3L, startVal, endVal))
                .thenReturn(BigDecimal.valueOf(7500.0));

        BigDecimal monthlyTaxableGross = BigDecimal.valueOf(75000.0);
        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, 1L, TaxRegime.NEW_REGIME, monthlyTaxableGross, 10, 2026);

        assertNotNull(tds);
        assertEquals(new BigDecimal("3750.00"), tds);
    }

    @Test
    public void testCalculateTaxOnNonRecurringIncome() {
        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, BigDecimal.valueOf(300000.0), BigDecimal.ZERO),
                new TaxSlabRow(BigDecimal.valueOf(300000.0), null, BigDecimal.valueOf(10.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.NEW_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.ZERO)
                .rebateLimit(BigDecimal.ZERO)
                .rebateMaxAmount(BigDecimal.ZERO)
                .cessPercent(BigDecimal.ZERO)
                .build();

        BigDecimal nonRecurring = BigDecimal.valueOf(50000.0);
        BigDecimal projectedTotal = BigDecimal.valueOf(400000.0);

        BigDecimal marginalTax = tdsCalculator.calculateTaxOnNonRecurringIncome(nonRecurring, projectedTotal, config);

        assertNotNull(marginalTax);
        // Base income = 400,000 - 50,000 = 350,000.
        // Tax on 400,000 = (400,000 - 300,000) * 10% = 10,000.
        // Tax on 350,000 = (350,000 - 300,000) * 10% = 5,000.
        // Marginal tax = 10,000 - 5,000 = 5,000.
        assertEquals(new BigDecimal("5000.00"), marginalTax);
    }

    @Test
    public void testCalculateMonthlyTds_StatutorySectionLimitsFallback() {
        Employee employee = new Employee();
        employee.setId(2L);

        List<TaxSlabRow> slabs = List.of(
                new TaxSlabRow(BigDecimal.ZERO, null, BigDecimal.valueOf(10.0))
        );

        TaxRegimeSlabConfig config = TaxRegimeSlabConfig.builder()
                .financialYear("2026-27")
                .regime(TaxRegime.OLD_REGIME)
                .slabs(slabs)
                .standardDeduction(BigDecimal.ZERO)
                .rebateLimit(BigDecimal.ZERO)
                .rebateMaxAmount(BigDecimal.ZERO)
                .cessPercent(BigDecimal.ZERO)
                .build();

        when(taxSlabConfigRepo.findByFinancialYearAndRegime("2026-27", TaxRegime.OLD_REGIME))
                .thenReturn(Optional.of(config));

        TaxDeclarationLineItem item80c = TaxDeclarationLineItem.builder()
                .section("80C")
                .declaredAmount(BigDecimal.valueOf(200000.0))
                .approvedAmount(BigDecimal.valueOf(200000.0))
                .build();

        TaxDeclaration declaration = TaxDeclaration.builder()
                .employeeId(2L)
                .financialYear("2026-27")
                .status(DeclarationStatus.VERIFIED)
                .lineItems(List.of(item80c))
                .build();

        when(taxDeclarationRepo.findByEmployeeIdAndFinancialYearAndStatus(2L, "2026-27", DeclarationStatus.VERIFIED))
                .thenReturn(Optional.of(declaration));

        // Let the rule repo return empty cap, so we fallback to statutory limit of 150000.0
        when(sectionRuleRepo.findCap("80C", TaxRegime.OLD_REGIME, "2026-27"))
                .thenReturn(Optional.empty());

        BigDecimal monthlyTaxableGross = BigDecimal.valueOf(50000.0);

        BigDecimal tds = tdsCalculator.calculateMonthlyTds(
                employee, 1L, TaxRegime.OLD_REGIME, monthlyTaxableGross, 4, 2026);

        // Projected gross = 50,000 * 12 = 600,000.
        // Fallback cap applied: min(200,000, 150,000) = 150,000.
        // Taxable income = 600,000 - 150,000 = 450,000.
        // Slab tax = 450,000 * 10% = 45,000.
        // Monthly TDS = 45,000 / 12 = 3750.
        assertNotNull(tds);
        assertEquals(new BigDecimal("3750.00"), tds);
    }
}
