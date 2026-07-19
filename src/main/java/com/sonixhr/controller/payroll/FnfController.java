package com.sonixhr.controller.payroll;

import com.sonixhr.entity.payroll.FnfSettlement;
import com.sonixhr.service.payroll.FnfSettlementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payroll/fnf")
@PreAuthorize("isAuthenticated()")
public class FnfController {

    private final FnfSettlementService fnfSettlementService;

    public FnfController(FnfSettlementService fnfSettlementService) {
        this.fnfSettlementService = fnfSettlementService;
    }

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.CREATED)
    public FnfSettlement processFnf(
            @RequestParam("employeeId") Long employeeId,
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("terminationDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminationDate) {
        return fnfSettlementService.processFnfSettlement(employeeId, tenantId, terminationDate);
    }
}
