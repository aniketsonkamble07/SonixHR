package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.ReimbursementClaim;
import com.sonixhr.enums.payroll.ReimbursementStatus;
import com.sonixhr.repository.payroll.ReimbursementClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReimbursementCalculator {

    private final ReimbursementClaimRepository reimbursementClaimRepo;

    public void calculateReimbursements(Employee employee, Long tenantId, int month, int year, PeriodPayData data) {
        List<ReimbursementClaim> claims = reimbursementClaimRepo.findApprovedByEmployeeAndMonth(
                tenantId, employee.getId(), month, year, ReimbursementStatus.APPROVED);

        BigDecimal total = BigDecimal.ZERO;
        for (ReimbursementClaim claim : claims) {
            String categoryCode = "REIMB_" + claim.getCategory().name();
            BigDecimal categorySum = data.getReimbursementBreakdown().getOrDefault(categoryCode, BigDecimal.ZERO);
            data.getReimbursementBreakdown().put(categoryCode, categorySum.add(claim.getClaimAmount()));
            total = total.add(claim.getClaimAmount());
        }
        data.setReimbursementTotal(total);
    }
}
