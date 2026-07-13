package com.sonixhr.service.payroll;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.payroll.ReimbursementClaim;
import com.sonixhr.enums.payroll.ReimbursementCategory;
import com.sonixhr.enums.payroll.ReimbursementStatus;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.payroll.ReimbursementClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReimbursementClaimService {

    private final ReimbursementClaimRepository claimRepo;
    private final EmployeeRepository employeeRepo;

    @Transactional
    public ReimbursementClaim submitClaim(
            Long employeeId,
            Long tenantId,
            BigDecimal amount,
            ReimbursementCategory category,
            Integer month,
            Integer year,
            String attachmentUrl) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        if (!employee.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Access denied: Employee does not belong to this tenant");
        }

        ReimbursementClaim claim = ReimbursementClaim.builder()
                .tenant(employee.getTenant())
                .employee(employee)
                .claimAmount(amount)
                .category(category)
                .targetMonth(month)
                .targetYear(year)
                .attachmentUrl(attachmentUrl)
                .status(ReimbursementStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build();

        return claimRepo.save(claim);
    }

    @Transactional
    public ReimbursementClaim approveClaim(UUID claimId, Long tenantId) {
        ReimbursementClaim claim = claimRepo.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Reimbursement claim not found"));

        if (!claim.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Access denied: Reimbursement claim does not belong to this tenant");
        }

        if (claim.getStatus() != ReimbursementStatus.SUBMITTED) {
            throw new BusinessException("Invalid state transition: Claim is not in SUBMITTED status");
        }

        claim.setStatus(ReimbursementStatus.APPROVED);
        claim.setProcessedAt(LocalDateTime.now());
        return claimRepo.save(claim);
    }

    @Transactional
    public ReimbursementClaim rejectClaim(UUID claimId, Long tenantId) {
        ReimbursementClaim claim = claimRepo.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Reimbursement claim not found"));

        if (!claim.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("Access denied: Reimbursement claim does not belong to this tenant");
        }

        if (claim.getStatus() != ReimbursementStatus.SUBMITTED) {
            throw new BusinessException("Invalid state transition: Claim is not in SUBMITTED status");
        }

        claim.setStatus(ReimbursementStatus.REJECTED);
        claim.setProcessedAt(LocalDateTime.now());
        return claimRepo.save(claim);
    }
}
