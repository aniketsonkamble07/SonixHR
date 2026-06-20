package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.PlatformTenantResponseDTO;
import com.sonixhr.dto.platform.TenantPlanOverrideDTO;
import com.sonixhr.dto.tenant.TenantRegistrationRequest;
import com.sonixhr.dto.tenant.TenantUpdateRequest;
import com.sonixhr.service.platform.PlatformTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final PlatformTenantService tenantService;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_TENANTS')")
    public ResponseEntity<Page<PlatformTenantResponseDTO>> getAllTenants(
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("REST request to list tenants matching filters");
        Page<PlatformTenantResponseDTO> tenants = tenantService.getAllTenants(companyName, status, isActive, pageable);
        return ResponseEntity.ok(tenants);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_TENANT_DETAILS')")
    public ResponseEntity<PlatformTenantResponseDTO> getTenantById(@PathVariable Long id) {
        log.info("REST request to get tenant metadata for ID: {}", id);
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        PlatformTenantResponseDTO tenant = tenantService.getTenantById(id);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('SUSPEND_TENANT')")
    public ResponseEntity<PlatformTenantResponseDTO> suspendTenant(
            @PathVariable Long id,
            @RequestParam String reason) {
        log.info("REST request to suspend tenant: {} with reason: {}", id, reason);
        if (id == null || reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        PlatformTenantResponseDTO tenant = tenantService.suspendTenant(id, reason);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ACTIVATE_TENANT')")
    public ResponseEntity<PlatformTenantResponseDTO> activateTenant(@PathVariable Long id) {
        log.info("REST request to activate tenant: {}", id);
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        PlatformTenantResponseDTO tenant = tenantService.activateTenant(id);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}/plan-override")
    @PreAuthorize("hasAuthority('MANAGE_TENANT_PLANS')")
    public ResponseEntity<PlatformTenantResponseDTO> overrideTenantPlan(
            @PathVariable Long id,
            @RequestBody @Valid TenantPlanOverrideDTO dto) {
        log.info("REST request to override plan settings for tenant: {}", id);
        if (id == null || dto == null) {
            return ResponseEntity.badRequest().build();
        }
        PlatformTenantResponseDTO tenant = tenantService.overrideTenantPlan(id, dto);
        return ResponseEntity.ok(tenant);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_TENANT')")
    public ResponseEntity<PlatformTenantResponseDTO> createTenant(
            @RequestBody @Valid TenantRegistrationRequest request) {
        log.info("REST request to create organization: {}", request.getCompanyName());
        PlatformTenantResponseDTO tenant = tenantService.createTenant(request);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EDIT_TENANT')")
    public ResponseEntity<PlatformTenantResponseDTO> updateTenant(
            @PathVariable Long id,
            @RequestBody @Valid TenantUpdateRequest request) {
        log.info("REST request to edit organization: {}", id);
        if (id == null || request == null) {
            return ResponseEntity.badRequest().build();
        }
        PlatformTenantResponseDTO tenant = tenantService.updateTenant(id, request);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ACTIVATE_TENANT')")
    public ResponseEntity<PlatformTenantResponseDTO> deactivateTenant(@PathVariable Long id) {
        log.info("REST request to deactivate organization: {}", id);
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        PlatformTenantResponseDTO tenant = tenantService.deactivateTenant(id);
        return ResponseEntity.ok(tenant);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_TENANT')")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        log.info("REST request to delete organization: {}", id);
        if (id == null) {
            return ResponseEntity.badRequest().build();
        }
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }
}
