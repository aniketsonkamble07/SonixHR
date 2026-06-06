package com.sonixhr.controller.tenant;

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
        Long tenantId = currentEmployee.getTenant().getId();
        if (tenantId == null) {
            throw new IllegalStateException("Employee not associated with any tenant");
        }
        return tenantId;
    }

    @PostMapping
    public ResponseEntity<TenantRoleResponse> createRole(
            @Valid @RequestBody TenantRoleCreateRequest request,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} creating role: {} for tenant: {}",
                currentEmployee.getEmail(), request.getName(), tenantId);

        TenantRole role = roleService.createRole(request, tenantId, currentEmployee.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(role));
    }

    @PutMapping("/{roleId}/permissions")
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

    @GetMapping
    public ResponseEntity<List<TenantRoleResponse>> getAllRoles(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting all roles for tenant: {}", tenantId);

        List<TenantRole> roles = roleService.getAllRolesForTenant(tenantId);
        return ResponseEntity.ok(roles.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/default")
    public ResponseEntity<List<TenantRoleResponse>> getDefaultRoles(
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting default roles for tenant: {}", tenantId);

        List<TenantRole> roles = roleService.getDefaultRolesForTenant(tenantId);
        return ResponseEntity.ok(roles.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<TenantRoleResponse> getRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting role with id: {} for tenant: {}", roleId, tenantId);

        TenantRole role = roleService.getRoleByIdAndTenant(roleId, tenantId);
        return ResponseEntity.ok(toResponse(role));
    }

    @PutMapping("/{roleId}")
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

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} deleting role: {} for tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        roleService.deleteRole(roleId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roleId}/users")
    public ResponseEntity<List<Employee>> getUsersByRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.debug("Getting users for role: {} in tenant: {}", roleId, tenantId);

        List<Employee> users = roleService.getUsersByRole(roleId, tenantId);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/{roleId}/users/{userId}")
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

    @DeleteMapping("/{roleId}/users/{userId}")
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

    @PostMapping("/{roleId}/default")
    public ResponseEntity<TenantRoleResponse> setDefaultRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal Employee currentEmployee) {

        Long tenantId = getCurrentTenantId(currentEmployee);
        log.info("Employee {} setting default role: {} for tenant: {}",
                currentEmployee.getEmail(), roleId, tenantId);

        TenantRole role = roleService.setDefaultRole(roleId, tenantId);
        return ResponseEntity.ok(toResponse(role));
    }

    private TenantRoleResponse toResponse(TenantRole role) {
        if (role == null) {
            return null;
        }

        return TenantRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isDefault(role.getIsDefault())
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
}