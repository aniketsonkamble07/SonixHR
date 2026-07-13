package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.LoanAdvance;
import com.sonixhr.repository.payroll.LoanAdvanceRepository;
import com.sonixhr.repository.payroll.PayslipItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRecoveryCalculator {

    private final LoanAdvanceRepository loanRepo;
    private final PayslipItemRepository payslipItemRepo;

    public BigDecimal calculateMonthlyRecovery(Employee employee, Long tenantId, PeriodPayData data) {
        List<LoanAdvance> activeLoans = loanRepo.findActiveByEmployeeIdAndTenantId(employee.getId(), tenantId);
        BigDecimal totalRecovery = BigDecimal.ZERO;

        for (LoanAdvance loan : activeLoans) {
            BigDecimal recoveredSoFar = payslipItemRepo.sumRecoveredForLoan(loan.getId().toString());
            if (recoveredSoFar == null) {
                recoveredSoFar = BigDecimal.ZERO;
            }

            BigDecimal outstanding = loan.getPrincipalAmount().subtract(recoveredSoFar).max(BigDecimal.ZERO);
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // already fully recovered
            }

            BigDecimal thisMonth = loan.getMonthlyInstallment().min(outstanding); // final installment may be smaller
            data.getLoanRecoveryBreakdown().put(loan.getId().toString(), thisMonth);
            totalRecovery = totalRecovery.add(thisMonth);
        }
        return totalRecovery;
    }
}
