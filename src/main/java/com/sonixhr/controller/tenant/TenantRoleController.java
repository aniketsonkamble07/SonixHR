package com.sonixhr.controller.tenant;
 
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
// Force re-indexing of imports - triggered save to resolve IDE editor caching issues
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.dto.tenant.TenantRoleSummaryResponse;
import com.sonixhr.dto.tenant.TenantRoleLookupResponse;
import com.sonixhr.dto.tenant.TenantRoleDeletePreviewResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.service.employee.EmployeeService;
import com.sonixhr.service.tenant.TenantRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/tenant/roles")
@RequiredArgsConstructor
public class TenantRoleController {

    private final TenantRoleService roleService;
    private final EmployeeService employeeService;

    private Long getCurrentTenantId(Employee currentEmployee) {
        Long tenantId = currentEmployee.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Employee not associated with any tenant");
        }
        return tenantId;
    }

    /**
     * Create a new role (Admin only)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public ResponseEntity<TenantRoleResponse> createRole(
            @Valid @RequestBody TenantRoleCreateRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} creating role: {} for tenant: {}",
                currentEmployee.getEmail(), request.getName(), tenantId);

        TenantRoleResponse response = roleService.createRole(request, tenantId, currentEmployee.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update role permissions (Admin only)
     */
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_EDIT_PERMISSIONS')")
    public ResponseEntity<TenantRoleResponse> updateRolePermissions(
            @PathVariable Long roleId,
            @RequestBody Set<Long> permissionIds,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} updating permissions for role: {} in tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        TenantRoleResponse response = roleService.updateRolePermissions(roleId, permissionIds, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all roles for the tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<TenantRoleSummaryResponse>> getAllRoles(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting all roles for tenant: {}", tenantId);

        List<TenantRoleSummaryResponse> roles = roleService.getAllRolesForTenant(tenantId);
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<TenantRoleLookupResponse>> getRoleLookup(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting role lookup for tenant: {}", tenantId);

        List<TenantRoleLookupResponse> roles = roleService.getRoleLookupForTenant(tenantId);
        return ResponseEntity.ok(roles);
    }

    /**
     * Get default roles for the tenant
     */
    @GetMapping("/default")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<TenantRoleResponse>> getDefaultRoles(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting default roles for tenant: {}", tenantId);

        List<TenantRole> roles = roleService.getDefaultRolesForTenant(tenantId);
        return ResponseEntity.ok(roles.stream().map(roleService::toResponse).collect(Collectors.toList()));
    }

    /**
     * Get role by ID
     */
    @GetMapping("/{roleId}")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<TenantRoleResponse> getRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting role with id: {} for tenant: {}", roleId, tenantId);

        TenantRoleResponse role = roleService.getRoleResponseByIdAndTenant(roleId, tenantId);
        return ResponseEntity.ok(role);
    }

    /**
     * Update role details (Admin only)
     */
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_EDIT')")
    public ResponseEntity<TenantRoleResponse> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody TenantRoleCreateRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} updating role: {} for tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        TenantRoleResponse response = roleService.updateRole(roleId, request, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete role (Admin only)
     */
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} deleting role: {} for tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        roleService.deleteRole(roleId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get delete preview for a role (Admin only)
     */
    @GetMapping("/{roleId}/delete-preview")
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<TenantRoleDeletePreviewResponse> getRoleDeletePreview(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} requesting delete preview for role: {} in tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        TenantRoleDeletePreviewResponse response = roleService.getRoleDeletePreview(roleId, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get users assigned to a role
     */
    @GetMapping("/{roleId}/users")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<EmployeeSummaryResponse>> getUsersByRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting users for role: {} in tenant: {}", roleId, tenantId);

        List<Employee> users = roleService.getUsersByRole(roleId, tenantId);

        // ✅ Convert to DTO
        List<EmployeeSummaryResponse> responses = users.stream()
                .map(employeeService::convertToSummaryResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Assign role to user (Admin only)
     */
    @PostMapping("/{roleId}/users/{userId}")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public ResponseEntity<Void> assignRoleToUser(
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} assigning role {} to user {} in tenant {}",
                currentEmployee.getEmail(), roleId, userId, tenantId);

        roleService.assignRoleToUser(roleId, userId, tenantId);
        return ResponseEntity.ok().build();
    }

    /**
     * Remove role from user (Admin only)
     */
    @DeleteMapping("/{roleId}/users/{userId}")
    @PreAuthorize("hasAuthority('ROLE_REMOVE')")
    public ResponseEntity<Void> removeRoleFromUser(
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} removing role {} from user {} in tenant {}",
                currentEmployee.getEmail(), roleId, userId, tenantId);

        roleService.removeRoleFromUser(roleId, userId, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Set default role for the tenant (Admin only)
     */
    @PostMapping("/{roleId}/default")
    @PreAuthorize("hasAuthority('ROLE_SET_DEFAULT')")
    public ResponseEntity<TenantRoleResponse> setDefaultRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} setting default role: {} for tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        TenantRoleResponse response = roleService.setDefaultRole(roleId, tenantId);
        return ResponseEntity.ok(response);
    }

}