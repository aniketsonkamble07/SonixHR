package com.sonixhr.controller.tenant;

import com.sonixhr.dto.attendance.ShiftConfigurationDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationRequestDTO;
import com.sonixhr.dto.attendance.ShiftConfigurationSummaryDTO;
import com.sonixhr.security.SecurityUtils;
import com.sonixhr.security.TenantContext;
import com.sonixhr.service.attendance.ShiftConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/tenant/shift-configurations", "/api/shift-configurations"})
@RequiredArgsConstructor
@Tag(name = "Shift Configuration", description = "APIs for managing shift configurations")
@SecurityRequirement(name = "bearerAuth")
public class ShiftConfigurationController {

    private final ShiftConfigurationService shiftConfigurationService;
    private final SecurityUtils securityUtils;

    // =====================================================
    // CRUD OPERATIONS - ADMIN ONLY
    // =====================================================

    @PostMapping
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_CREATE')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Create a new shift configuration",
            description = "Creates a new shift configuration for the tenant. Requires SHIFT_CREATE permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Shift configuration created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "409", description = "Shift configuration already exists or duplicate code")
    })
    public ResponseEntity<ShiftConfigurationDTO> createShift(
            @Valid @RequestBody ShiftConfigurationRequestDTO request) {
        log.info("REST request to create shift configuration for tenant: {}", TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        Long employeeId = securityUtils.getCurrentEmployeeId();

        ShiftConfigurationDTO response = shiftConfigurationService.createShiftConfiguration(
                request, tenantId, employeeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_UPDATE')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Update an existing shift configuration",
            description = "Updates an existing shift configuration. Requires SHIFT_UPDATE permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shift configuration updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found")
    })
    public ResponseEntity<ShiftConfigurationDTO> updateShift(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody ShiftConfigurationRequestDTO request) {
        log.info("REST request to update shift configuration: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        Long employeeId = securityUtils.getCurrentEmployeeId();

        ShiftConfigurationDTO response = shiftConfigurationService.updateShiftConfiguration(
                id, request, tenantId, employeeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_VIEW_ALL') or @securityUtils.hasPermission('SHIFT_VIEW')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Get all shift configurations",
            description = "Retrieves all shift configurations for the current tenant. Requires SHIFT_VIEW_ALL or SHIFT_VIEW permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shift configurations retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<ShiftConfigurationSummaryDTO>> getAllShifts() {
        log.info("REST request to get all shift configurations for tenant: {}", TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        List<ShiftConfigurationSummaryDTO> response =
                shiftConfigurationService.getAllShiftConfigurationsSummary(tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_VIEW')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Get all active shift configurations",
            description = "Retrieves all active shift configurations for the current tenant. Requires SHIFT_VIEW permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active shift configurations retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<List<ShiftConfigurationSummaryDTO>> getAllActiveShifts() {
        log.info("REST request to get all active shift configurations for tenant: {}", TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        List<ShiftConfigurationSummaryDTO> response =
                shiftConfigurationService.getAllActiveShiftsSummary(tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_VIEW') or @securityUtils.hasPermission('SHIFT_VIEW_ALL')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Get shift configuration by ID",
            description = "Retrieves a specific shift configuration by ID. Requires SHIFT_VIEW or SHIFT_VIEW_ALL permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shift configuration retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found")
    })
    public ResponseEntity<ShiftConfigurationDTO> getShiftById(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to get shift configuration by id: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        ShiftConfigurationDTO response =
                shiftConfigurationService.getShiftConfigurationById(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/code/{shiftCode}")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_VIEW') or @securityUtils.hasPermission('SHIFT_VIEW_ALL')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Get shift configuration by code",
            description = "Retrieves a specific shift configuration by code. Requires SHIFT_VIEW or SHIFT_VIEW_ALL permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shift configuration retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found")
    })
    public ResponseEntity<ShiftConfigurationDTO> getShiftByCode(
            @Parameter(description = "Shift configuration code", required = true)
            @PathVariable String shiftCode) {
        log.info("REST request to get shift configuration by code: {} for tenant: {}",
                shiftCode, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        ShiftConfigurationDTO response =
                shiftConfigurationService.getShiftByCode(shiftCode, tenantId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_DELETE')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Soft delete shift configuration",
            description = "Soft deletes a shift configuration (marks as inactive and deleted). Requires SHIFT_DELETE permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Shift configuration deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found"),
            @ApiResponse(responseCode = "409", description = "Cannot delete default shift")
    })
    public ResponseEntity<Void> softDeleteShift(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to soft delete shift configuration: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        Long employeeId = securityUtils.getCurrentEmployeeId();

        shiftConfigurationService.softDeleteShiftConfiguration(id, tenantId, employeeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_HARD_DELETE')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Hard delete shift configuration",
            description = "Permanently deletes a shift configuration. Requires SHIFT_HARD_DELETE permission.",
            hidden = true)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Shift configuration permanently deleted"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found"),
            @ApiResponse(responseCode = "409", description = "Cannot delete default shift")
    })
    public ResponseEntity<Void> hardDeleteShift(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to hard delete shift configuration: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        shiftConfigurationService.hardDeleteShiftConfiguration(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    // =====================================================
    // ACTIVATION / DEACTIVATION - ADMIN ONLY
    // =====================================================

    @PostMapping("/{id}/activate")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_UPDATE') or @securityUtils.hasPermission('SHIFT_ADMIN')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Activate shift configuration",
            description = "Activates a shift configuration. Requires SHIFT_UPDATE or SHIFT_ADMIN permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shift configuration activated successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found")
    })
    public ResponseEntity<Void> activateShift(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to activate shift configuration: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        Long employeeId = securityUtils.getCurrentEmployeeId();

        shiftConfigurationService.activateShift(id, tenantId, employeeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_UPDATE') or @securityUtils.hasPermission('SHIFT_ADMIN')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Deactivate shift configuration",
            description = "Deactivates a shift configuration. Requires SHIFT_UPDATE or SHIFT_ADMIN permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Shift configuration deactivated successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found"),
            @ApiResponse(responseCode = "409", description = "Cannot deactivate default shift")
    })
    public ResponseEntity<Void> deactivateShift(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to deactivate shift configuration: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        Long employeeId = securityUtils.getCurrentEmployeeId();

        shiftConfigurationService.deactivateShift(id, tenantId, employeeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("@securityUtils.hasPermission('SHIFT_UPDATE') or @securityUtils.hasPermission('SHIFT_ADMIN')")  // ✅ Using SecurityUtils bean
    @Operation(summary = "Set default shift configuration",
            description = "Sets a shift configuration as the default for the tenant. Requires SHIFT_UPDATE or SHIFT_ADMIN permission.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Default shift set successfully"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Shift configuration not found")
    })
    public ResponseEntity<Void> setDefaultShift(
            @Parameter(description = "Shift configuration ID", required = true)
            @PathVariable Long id) {
        log.info("REST request to set default shift configuration: {} for tenant: {}",
                id, TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        Long employeeId = securityUtils.getCurrentEmployeeId();

        shiftConfigurationService.setDefaultShift(id, tenantId, employeeId);
        return ResponseEntity.ok().build();
    }

    // =====================================================
    // GET OPERATIONS WITH DATE - VIEW PERMISSION
    // =====================================================

    @GetMapping("/effective")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get effective shift for a date",
            description = "Retrieves the effective shift configuration for a specific date. Accessible to authenticated users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Effective shift retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No effective shift found")
    })
    public ResponseEntity<ShiftConfigurationDTO> getEffectiveShift(
            @Parameter(description = "Date to check (format: yyyy-MM-dd)", example = "2026-01-15")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        Long tenantId = securityUtils.getCurrentTenantId();

        if (date == null) {
            date = LocalDate.now();
        }

        log.info("REST request to get effective shift for date: {} and tenant: {}", date, tenantId);

        ShiftConfigurationDTO response =
                shiftConfigurationService.getEffectiveShiftOnDate(tenantId, date);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/default")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get default shift configuration",
            description = "Retrieves the default shift configuration for the current tenant. Accessible to authenticated users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Default shift retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No default shift found")
    })
    public ResponseEntity<ShiftConfigurationDTO> getDefaultShift() {
        log.info("REST request to get default shift for tenant: {}", TenantContext.getCurrentTenant());

        Long tenantId = securityUtils.getCurrentTenantId();
        ShiftConfigurationDTO response =
                shiftConfigurationService.getDefaultShift(tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-shift")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current employee's shift",
            description = "Retrieves the shift configuration for the currently authenticated employee. Accessible to authenticated users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Employee's shift retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "No shift found")
    })
    public ResponseEntity<ShiftConfigurationDTO> getMyShift() {
        Long employeeId = securityUtils.getCurrentEmployeeId();
        Long tenantId = securityUtils.getCurrentTenantId();

        log.info("REST request to get shift for employee: {} and tenant: {}", employeeId, tenantId);

        ShiftConfigurationDTO response =
                shiftConfigurationService.getShiftConfiguration(tenantId);
        return ResponseEntity.ok(response);
    }

    // =====================================================
    // UTILITY ENDPOINTS - EMPLOYEE ACCESS
    // =====================================================

    @GetMapping("/is-weekoff")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if date is a week off",
            description = "Checks if a specific date is a week off based on the shift configuration. Accessible to authenticated users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Week off status retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Boolean> isWeekOff(
            @Parameter(description = "Date to check (format: yyyy-MM-dd)", example = "2026-01-17")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        Long tenantId = securityUtils.getCurrentTenantId();

        if (date == null) {
            date = LocalDate.now();
        }

        log.info("REST request to check week off for date: {} and tenant: {}", date, tenantId);

        Boolean response = shiftConfigurationService.isWeekOff(tenantId, date);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expected-hours")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get expected working hours",
            description = "Calculates the expected working hours for a specific date. Accessible to authenticated users.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expected hours retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Double> getExpectedWorkingHours(
            @Parameter(description = "Date to calculate (format: yyyy-MM-dd)", example = "2026-01-15")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        Long tenantId = securityUtils.getCurrentTenantId();

        if (date == null) {
            date = LocalDate.now();
        }

        log.info("REST request to get expected working hours for date: {} and tenant: {}", date, tenantId);

        Double response = shiftConfigurationService.getExpectedWorkingHours(tenantId, date);
        return ResponseEntity.ok(response);
    }
    // Note: Micro-calculation endpoints for future real-time biometric/RFID hardware
    // have been preserved in BiometricShiftControllerExtension.java.bak
}