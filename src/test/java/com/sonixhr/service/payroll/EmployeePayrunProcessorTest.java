package com.sonixhr.service.payroll;

import com.sonixhr.entity.payroll.EmployeeSalaryProfile;
import com.sonixhr.repository.payroll.EmployeeSalaryProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class EmployeePayrunProcessorTest {

    @Mock
    private EmployeeSalaryProfileRepository employeeSalaryProfileRepo;

    @Mock
    private com.sonixhr.repository.payroll.EmployeeSalaryComponentRepository employeeSalaryComponentRepo;

    @InjectMocks
    private EmployeePayrunProcessor processor;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCalculateMonthlyBonus_Eligible() {
        BigDecimal basicSalary = BigDecimal.valueOf(15000); // <= 21,000 -> Eligible
        BigDecimal grossSalary = BigDecimal.valueOf(25000);
        int monthsWorkedInFY = 12;

        BigDecimal bonus = ReflectionTestUtils.invokeMethod(processor, "calculateMonthlyBonus",
                basicSalary, grossSalary, monthsWorkedInFY, null);

        // Computation base capped at ₹7,000:
        // 7,000 * 8.33% = 583.10
        assertEquals(new BigDecimal("583.10"), bonus);
    }

    @Test
    public void testCalculateMonthlyBonus_NotEligible() {
        BigDecimal basicSalary = BigDecimal.valueOf(22000); // > 21,000 -> Disqualified
        BigDecimal grossSalary = BigDecimal.valueOf(35000);
        int monthsWorkedInFY = 12;

        BigDecimal bonus = ReflectionTestUtils.invokeMethod(processor, "calculateMonthlyBonus",
                basicSalary, grossSalary, monthsWorkedInFY, null);

        assertEquals(BigDecimal.ZERO, bonus);
    }

    @Test
    public void testCalculateArrears_NotPaidYet() {
        Long employeeId = 1L;
        Long tenantId = 1L;
        LocalDate monthStart = LocalDate.of(2026, 6, 1);

        EmployeeSalaryProfile previous = EmployeeSalaryProfile.builder()
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .monthlyCtc(BigDecimal.valueOf(50000))
                .build();

        EmployeeSalaryProfile latest = EmployeeSalaryProfile.builder()
                .effectiveFrom(LocalDate.of(2026, 4, 1)) // 2 months retroactive (April & May)
                .monthlyCtc(BigDecimal.valueOf(55000))
                .arrearsPaid(false)
                .build();

        when(employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(employeeId))
                .thenReturn(List.of(previous, latest));

        BigDecimal arrears = ReflectionTestUtils.invokeMethod(processor, "calculateArrears",
                employeeId, tenantId, monthStart);

        // ctcDiff = 5000, monthsDiff = 2 -> Arrears = 10000
        assertEquals(new BigDecimal("10000.00"), arrears);
        verify(employeeSalaryProfileRepo, times(1)).save(latest);
    }

    @Test
    public void testCalculateArrears_AlreadyPaid() {
        Long employeeId = 1L;
        Long tenantId = 1L;
        LocalDate monthStart = LocalDate.of(2026, 6, 1);

        EmployeeSalaryProfile previous = EmployeeSalaryProfile.builder()
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .monthlyCtc(BigDecimal.valueOf(50000))
                .build();

        EmployeeSalaryProfile latest = EmployeeSalaryProfile.builder()
                .effectiveFrom(LocalDate.of(2026, 4, 1))
                .monthlyCtc(BigDecimal.valueOf(55000))
                .arrearsPaid(true) // Already marked as paid
                .build();

        when(employeeSalaryProfileRepo.findByEmployeeIdOrderByEffectiveFromAsc(employeeId))
                .thenReturn(List.of(previous, latest));

        BigDecimal arrears = ReflectionTestUtils.invokeMethod(processor, "calculateArrears",
                employeeId, tenantId, monthStart);

        assertEquals(BigDecimal.ZERO, arrears);
        verify(employeeSalaryProfileRepo, never()).save(any());
    }
}
