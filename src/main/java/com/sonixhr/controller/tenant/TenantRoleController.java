package com.sonixhr.controller.tenant;

import com.sonixhr.dto.employee.EmployeeResponse;
import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantRole;
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

        TenantRole role = roleService.createRole(request, tenantId, currentEmployee.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(role));
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

        TenantRole updated = roleService.updateRolePermissions(roleId, permissionIds, tenantId);
        return ResponseEntity.ok(toResponse(updated));
    }

    /**
     * Get all roles for the tenant
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<TenantRoleResponse>> getAllRoles(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting all roles for tenant: {}", tenantId);

        List<TenantRole> roles = roleService.getAllRolesForTenant(tenantId);
        return ResponseEntity.ok(roles.stream().map(this::toResponse).collect(Collectors.toList()));
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
        return ResponseEntity.ok(roles.stream().map(this::toResponse).collect(Collectors.toList()));
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

        TenantRole role = roleService.getRoleByIdAndTenant(roleId, tenantId);
        return ResponseEntity.ok(toResponse(role));
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

        TenantRole updated = roleService.updateRole(roleId, request, tenantId);
        return ResponseEntity.ok(toResponse(updated));
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
     * Get users assigned to a role
     */
    @GetMapping("/{roleId}/users")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW', 'EMPLOYEE_VIEW_ALL')")
    public ResponseEntity<List<EmployeeResponse>> getUsersByRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting users for role: {} in tenant: {}", roleId, tenantId);

        List<Employee> users = roleService.getUsersByRole(roleId, tenantId);

        // ✅ Convert to DTO
        List<EmployeeResponse> responses = users.stream()
                .map(this::toEmployeeResponse)
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

        TenantRole role = roleService.setDefaultRole(roleId, tenantId);
        return ResponseEntity.ok(toResponse(role));
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    private TenantRoleResponse toResponse(TenantRole role) {
        if (role == null) {
            return null;
        }

        return TenantRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isDefault(role.isDefault())
                .permissions(role.getPermissions() != null ?
                        role.getPermissions().stream()
                                .map(p -> TenantRoleResponse.PermissionInfo.builder()
                                        .id(p.getId())
                                        .name(p.getPermission().name())
                                        .description(p.getDescription())
                                        .category(p.getCategory())
                                        .build())
                                .collect(Collectors.toList()) :
                        List.of())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private EmployeeResponse toEmployeeResponse(Employee employee) {
        if (employee == null) {
            return null;
        }

        return EmployeeResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .fullName(employee.getFullName())
                .email(employee.getEmail())
                .position(employee.getPosition())
                .department(employee.getDepartment() != null ?
                        EmployeeResponse.DepartmentInfo.builder()
                                .id(employee.getDepartment().getId())
                                .name(employee.getDepartment().getName())
                                .build() : null)
                .status(employee.getStatus())
                .isActive(employee.isActive())
                .build();
    }
}