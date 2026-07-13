package com.sonixhr.controller.payroll;

import com.sonixhr.entity.payroll.ReimbursementClaim;
import com.sonixhr.enums.payroll.ReimbursementCategory;
import com.sonixhr.service.payroll.ReimbursementClaimService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll/reimbursements")
@PreAuthorize("isAuthenticated()")
public class ReimbursementClaimController {

    @Autowired
    private ReimbursementClaimService claimService;

    @PostMapping("/submit")
    public ReimbursementClaim submit(
            @RequestParam("employeeId") Long employeeId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("category") ReimbursementCategory category,
            @RequestParam("month") Integer month,
            @RequestParam("year") Integer year,
            @RequestParam(value = "attachmentUrl", required = false) String attachmentUrl) {
        return claimService.submitClaim(employeeId, tenantId, amount, category, month, year, attachmentUrl);
    }

    @PostMapping("/{claimId}/approve")
    public ReimbursementClaim approve(
            @PathVariable("claimId") UUID claimId,
            @RequestParam("tenantId") Long tenantId) {
        return claimService.approveClaim(claimId, tenantId);
    }

    @PostMapping("/{claimId}/reject")
    public ReimbursementClaim reject(
            @PathVariable("claimId") UUID claimId,
            @RequestParam("tenantId") Long tenantId) {
        return claimService.rejectClaim(claimId, tenantId);
    }
}
