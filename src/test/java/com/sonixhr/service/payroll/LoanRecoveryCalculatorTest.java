package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.LoanAdvance;
import com.sonixhr.repository.payroll.LoanAdvanceRepository;
import com.sonixhr.repository.payroll.PayslipItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class LoanRecoveryCalculatorTest {

    @Mock
    private LoanAdvanceRepository loanRepo;

    @Mock
    private PayslipItemRepository payslipItemRepo;

    @InjectMocks
    private LoanRecoveryCalculator calculator;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testCalculateMonthlyRecovery_CappedAtOutstanding() {
        Employee employee = new Employee();
        employee.setId(1L);
        Long tenantId = 1L;

        UUID loanId = UUID.randomUUID();
        LoanAdvance loan = LoanAdvance.builder()
                .id(loanId)
                .principalAmount(BigDecimal.valueOf(10000))
                .monthlyInstallment(BigDecimal.valueOf(2000))
                .build();

        when(loanRepo.findActiveByEmployeeIdAndTenantId(1L, tenantId)).thenReturn(List.of(loan));
        // Recovered so far is 9000, so outstanding is 1000 (less than EMI of 2000)
        when(payslipItemRepo.sumRecoveredForLoan(loanId.toString())).thenReturn(BigDecimal.valueOf(9000));

        PeriodPayData data = new PeriodPayData();
        BigDecimal recovery = calculator.calculateMonthlyRecovery(employee, tenantId, data);

        assertEquals(BigDecimal.valueOf(1000), recovery);
        assertEquals(BigDecimal.valueOf(1000).setScale(2), data.getLoanRecoveryBreakdown().get(loanId.toString()));
    }
}
