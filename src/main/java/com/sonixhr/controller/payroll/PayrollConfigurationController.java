package com.sonixhr.controller.payroll;

import com.sonixhr.dto.payroll.TenantPayrollConfigRequest;
import com.sonixhr.dto.payroll.TenantPayrollConfigResponse;
import com.sonixhr.security.TenantContext;
import com.sonixhr.service.payroll.TenantPayrollConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/payroll/config")
@RequiredArgsConstructor
public class PayrollConfigurationController {

    private final TenantPayrollConfigService tenantPayrollConfigService;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public ResponseEntity<TenantPayrollConfigResponse> getTenantConfig() {
        Long tenantId = TenantContext.getCurrentTenantOrThrow();
        log.info("Fetching tenant config for tenant: {}", tenantId);
        return ResponseEntity.ok(tenantPayrollConfigService.getActiveConfig(tenantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<TenantPayrollConfigResponse> updateTenantConfig(
            @Valid @RequestBody TenantPayrollConfigRequest request) {
        Long tenantId = TenantContext.getCurrentTenantOrThrow();
        log.info("Updating tenant config for tenant: {}", tenantId);
        return ResponseEntity.ok(tenantPayrollConfigService.updateConfig(tenantId, request));
    }
}
