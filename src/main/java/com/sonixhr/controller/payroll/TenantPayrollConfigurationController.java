package com.sonixhr.controller.payroll;

import com.sonixhr.dto.payroll.*;
import com.sonixhr.entity.payroll.EmployeeSalaryProfileHistory;
import com.sonixhr.service.payroll.TenantConfigurationService;
import org.springframework.lang.NonNull;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payroll/config")
@RequiredArgsConstructor
@Tag(name = "Payroll Configuration", description = "APIs for managing tenant payroll configurations, components, and employee salary profiles")
public class TenantPayrollConfigurationController {

    private final TenantConfigurationService configService;

    // ============================================================
    // TENANT GLOBAL CONFIGURATION ENDPOINTS
    // ============================================================

    @Operation(summary = "Create or update tenant global payroll configuration",
            description = "Creates a new version of the global payroll configuration for a tenant. Previous active config is versioned and closed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PutMapping("/tenants/{tenantId}/global")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<TenantPayrollConfigResponse> updateGlobalConfig(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Valid @RequestBody @NonNull TenantPayrollConfigRequest request) {
        log.info("REST request to update global config for tenant: {}", tenantId);
        TenantPayrollConfigResponse response = configService.createOrUpdateGlobalConfig(tenantId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get active tenant global payroll configuration",
            description = "Retrieves the currently active global payroll configuration for a tenant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Configuration retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant or configuration not found")
    })
    @GetMapping("/tenants/{tenantId}/global")
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public ResponseEntity<TenantPayrollConfigResponse> getGlobalConfig(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId) {
        log.info("REST request to get global config for tenant: {}", tenantId);
        TenantPayrollConfigResponse response = configService.getActiveGlobalConfig(tenantId);
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // COMPONENT DEFINITION ENDPOINTS
    // ============================================================

    @Operation(summary = "Create a new salary component definition",
            description = "Creates a new salary component definition for a tenant. Components can be allowances or deductions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Component created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or duplicate component"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PostMapping("/tenants/{tenantId}/components")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<SalaryComponentDefinitionResponse> createComponent(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Valid @RequestBody @NonNull SalaryComponentDefinitionRequest request) {
        log.info("REST request to create component for tenant: {}, component: {}",
                tenantId, request.getComponentCode());
        SalaryComponentDefinitionResponse response = configService.createComponentDefinition(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get all active components for a tenant",
            description = "Retrieves all active salary components configured for a tenant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Components retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/tenants/{tenantId}/components")
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public ResponseEntity<List<SalaryComponentDefinitionResponse>> getComponents(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId) {
        log.info("REST request to get all components for tenant: {}", tenantId);
        List<SalaryComponentDefinitionResponse> responses = configService.getComponentsByTenant(tenantId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get only allowed components for a tenant",
            description = "Retrieves components that are marked as allowed for employee use. These can be used in employee salary profiles.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Components retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/tenants/{tenantId}/components/allowed")
    @PreAuthorize("hasAuthority('SETTINGS_VIEW')")
    public ResponseEntity<List<SalaryComponentDefinitionResponse>> getAllowedComponents(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId) {
        log.info("REST request to get allowed components for tenant: {}", tenantId);
        List<SalaryComponentDefinitionResponse> responses = configService.getAllowedComponentsByTenant(tenantId);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Toggle component allowed status",
            description = "Enable or disable a component for employee use. When disabled, employees cannot use this component in their profiles.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Component status updated successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Component or tenant not found")
    })
    @PatchMapping("/tenants/{tenantId}/components/{componentId}/allowed")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<Void> toggleComponentAllowed(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Component ID", required = true)
            @PathVariable @NonNull UUID componentId,
            @Parameter(description = "Allowed status", required = true)
            @RequestParam boolean allowed) {
        log.info("REST request to toggle component {} allowed status to: {} for tenant: {}",
                componentId, allowed, tenantId);
        configService.toggleComponentAllowed(tenantId, componentId, allowed);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete a component definition",
            description = "Soft deletes a component definition. The component will be marked as inactive and cannot be used in future calculations.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Component deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot delete mandatory component"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Component or tenant not found")
    })
    @DeleteMapping("/tenants/{tenantId}/components/{componentId}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<Void> deleteComponent(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Component ID", required = true)
            @PathVariable @NonNull UUID componentId) {
        log.info("REST request to delete component: {} for tenant: {}", componentId, tenantId);
        configService.deleteComponent(tenantId, componentId);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // EMPLOYEE SALARY PROFILE ENDPOINTS
    // ============================================================

    @Operation(summary = "Create a new salary profile for an employee",
            description = "Creates a new salary profile for an employee with specified CTC and component overrides. Previous active profile is versioned and closed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Profile created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant or employee not found")
    })
    @PostMapping("/tenants/{tenantId}/employees/{employeeId}/profile")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeSalaryProfileResponse> createEmployeeProfile(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId,
            @Valid @RequestBody @NonNull EmployeeSalaryProfileRequest request) {
        log.info("REST request to create salary profile for employee: {} in tenant: {}",
                employeeId, tenantId);
        EmployeeSalaryProfileResponse response = configService.createEmployeeProfile(tenantId, employeeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get active salary profile for an employee",
            description = "Retrieves the currently active salary profile for a specific employee.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant, employee, or profile not found")
    })
    @GetMapping("/tenants/{tenantId}/employees/{employeeId}/profile")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL') or #employeeId == authentication.principal.id")
    public ResponseEntity<EmployeeSalaryProfileResponse> getEmployeeProfile(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId) {
        log.info("REST request to get active salary profile for employee: {} in tenant: {}",
                employeeId, tenantId);
        EmployeeSalaryProfileResponse response = configService.getActiveEmployeeProfile(tenantId, employeeId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all salary profiles for an employee",
            description = "Retrieves all historical salary profiles for a specific employee.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profiles retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant or employee not found")
    })
    @GetMapping("/tenants/{tenantId}/employees/{employeeId}/profiles/all")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<EmployeeSalaryProfileResponse>> getAllEmployeeProfiles(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId) {
        log.info("REST request to get all salary profiles for employee: {} in tenant: {}",
                employeeId, tenantId);
        List<EmployeeSalaryProfileResponse> responses = configService.getAllEmployeeProfiles(tenantId, employeeId);
        return ResponseEntity.ok(responses);
    }

    // ============================================================
    // EMPLOYEE PROMOTION ENDPOINTS
    // ============================================================

    @Operation(summary = "Process employee promotion",
            description = "Processes a promotion for an employee with new CTC and optional component overrides. Old profile is closed and a new version is created.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Promotion processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant, employee, or profile not found")
    })
    @PostMapping("/tenants/{tenantId}/employees/{employeeId}/promote")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeSalaryProfileResponse> promoteEmployee(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId,
            @Valid @RequestBody @NonNull PromotionRequest request) {
        log.info("REST request to promote employee: {} in tenant: {}", employeeId, tenantId);
        EmployeeSalaryProfileResponse response = configService.promoteEmployee(tenantId, employeeId, request);
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // EMPLOYEE COMPONENT OVERRIDE ENDPOINTS
    // ============================================================

    @Operation(summary = "Update employee component override",
            description = "Updates or creates a component override for an employee's salary profile. Allows customization of specific components for individual employees.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Component updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters or validation failure"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant, profile, or component not found")
    })
    @PutMapping("/tenants/{tenantId}/employees/{employeeId}/profile/{profileId}/components")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    public ResponseEntity<EmployeeComponentOverrideDTO> updateEmployeeComponent(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId,
            @Parameter(description = "Profile ID", required = true)
            @PathVariable @NonNull UUID profileId,
            @Valid @RequestBody @NonNull EmployeeComponentOverrideDTO request) {
        log.info("REST request to update employee component: {} for employee: {} in tenant: {}",
                request.getComponentCode(), employeeId, tenantId);
        EmployeeComponentOverrideDTO response = configService.updateEmployeeComponent(tenantId, profileId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Sync employee profile with global definitions",
            description = "Synchronizes an employee's salary profile with the latest global component definitions. Adds missing mandatory components.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile synced successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant or profile not found")
    })
    @PostMapping("/tenants/{tenantId}/employees/{employeeId}/profile/{profileId}/sync")
    @PreAuthorize("hasAuthority('EMPLOYEE_EDIT')")
    public ResponseEntity<Void> syncEmployeeProfileWithGlobal(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId,
            @Parameter(description = "Profile ID", required = true)
            @PathVariable @NonNull UUID profileId) {
        log.info("REST request to sync employee profile: {} with global definitions for tenant: {}",
                profileId, tenantId);
        configService.syncEmployeeProfileFromGlobal(tenantId, profileId);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // EMPLOYEE SALARY HISTORY ENDPOINTS
    // ============================================================

    @Operation(summary = "Get employee salary history",
            description = "Retrieves the complete salary history (audit trail) for an employee including all profile versions and changes.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant or employee not found")
    })
    @GetMapping("/tenants/{tenantId}/employees/{employeeId}/history")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<EmployeeSalaryProfileHistory>> getEmployeeSalaryHistory(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Parameter(description = "Employee ID", required = true)
            @PathVariable @NonNull Long employeeId) {
        log.info("REST request to get salary history for employee: {} in tenant: {}",
                employeeId, tenantId);
        List<EmployeeSalaryProfileHistory> history = configService.getEmployeeSalaryHistory(tenantId, employeeId);
        return ResponseEntity.ok(history);
    }

    // ============================================================
    // BULK OPERATIONS ENDPOINTS
    // ============================================================

    @Operation(summary = "Sync all employee profiles with global definitions",
            description = "Synchronizes all employee profiles with the latest global component definitions. Adds missing mandatory components for all employees.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "All profiles synced successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @PostMapping("/tenants/{tenantId}/employees/sync-all")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<Void> syncAllEmployeeProfiles(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId) {
        log.info("REST request to sync all employee profiles with global definitions for tenant: {}", tenantId);
        configService.syncAllEmployeeProfiles(tenantId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get all employee profiles for a tenant",
            description = "Retrieves all active employee salary profiles for all employees in a tenant.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profiles retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission"),
            @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    @GetMapping("/tenants/{tenantId}/employees/profiles")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<EmployeeSalaryProfileResponse>> getAllEmployeeProfilesForTenant(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId) {
        log.info("REST request to get all employee profiles for tenant: {}", tenantId);
        List<EmployeeSalaryProfileResponse> responses = configService.getAllEmployeeProfilesForTenant(tenantId);
        return ResponseEntity.ok(responses);
    }

    // ============================================================
    // VALIDATION ENDPOINTS
    // ============================================================

    @Operation(summary = "Validate formula expression",
            description = "Validates a formula expression syntax before saving it to a component definition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Formula is valid"),
            @ApiResponse(responseCode = "400", description = "Invalid formula syntax"),
            @ApiResponse(responseCode = "403", description = "User doesn't have permission")
    })
    @PostMapping("/tenants/{tenantId}/validate-formula")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<FormulaValidationResponse> validateFormula(
            @Parameter(description = "Tenant ID", required = true)
            @PathVariable @NonNull Long tenantId,
            @Valid @RequestBody @NonNull FormulaValidationRequest request) {
        log.info("REST request to validate formula: {} for tenant: {}", request.getFormula(), tenantId);
        boolean isValid = configService.validateFormulaSyntax(request.getFormula());
        FormulaValidationResponse response = FormulaValidationResponse.builder()
                .valid(isValid)
                .formula(request.getFormula())
                .message(isValid ? "Formula is valid" : "Invalid formula syntax")
                .build();
        return ResponseEntity.ok(response);
    }
}