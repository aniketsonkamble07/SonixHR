package com.sonixhr.controller.payroll;

import com.sonixhr.entity.payroll.FnfSettlement;
import com.sonixhr.service.payroll.FnfSettlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payroll/fnf")
@PreAuthorize("isAuthenticated()")
public class FnfController {

    @Autowired
    private FnfSettlementService fnfSettlementService;

    @PostMapping("/process")
    public FnfSettlement processFnf(
            @RequestParam("employeeId") Long employeeId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("terminationDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminationDate) {
        return fnfSettlementService.processFnfSettlement(employeeId, tenantId, terminationDate);
    }
}
