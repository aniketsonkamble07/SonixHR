package com.sonixhr.service.payroll;

import com.sonixhr.repository.payroll.PayslipItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FnfSettlementServiceTest {

    @Mock
    private PayslipItemRepository payslipItemRepo;

    @InjectMocks
    private FnfSettlementService fnfSettlementService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetAvgMonthlySalaryLast10Months_WithHistory() {
        Long employeeId = 1L;
        Long tenantId = 1L;
        LocalDate terminationDate = LocalDate.of(2026, 10, 15);
        BigDecimal currentBasic = BigDecimal.valueOf(50000);

        LocalDate expectedFyStart = LocalDate.of(2026, 4, 1);
        Pageable expectedPageable = PageRequest.of(0, 10);

        List<BigDecimal> history = List.of(
                BigDecimal.valueOf(48000),
                BigDecimal.valueOf(49000),
                BigDecimal.valueOf(50000)
        );

        when(payslipItemRepo.findLastBasicSalaries(tenantId, employeeId, expectedFyStart, terminationDate, expectedPageable))
                .thenReturn(history);

        BigDecimal avgBasic = fnfSettlementService.getAvgMonthlySalaryLast10Months(employeeId, tenantId, currentBasic, terminationDate);

        // (48000 + 49000 + 50000) / 3 = 49000.00
        assertEquals(BigDecimal.valueOf(49000).setScale(2), avgBasic);
    }

    @Test
    public void testGetAvgMonthlySalaryLast10Months_NoHistoryFallback() {
        Long employeeId = 1L;
        Long tenantId = 1L;
        LocalDate terminationDate = LocalDate.of(2026, 10, 15);
        BigDecimal currentBasic = BigDecimal.valueOf(50000);

        LocalDate expectedFyStart = LocalDate.of(2026, 4, 1);
        Pageable expectedPageable = PageRequest.of(0, 10);

        when(payslipItemRepo.findLastBasicSalaries(tenantId, employeeId, expectedFyStart, terminationDate, expectedPageable))
                .thenReturn(new ArrayList<>());

        BigDecimal avgBasic = fnfSettlementService.getAvgMonthlySalaryLast10Months(employeeId, tenantId, currentBasic, terminationDate);

        // Fallback to currentBasic
        assertEquals(currentBasic, avgBasic);
    }
}
