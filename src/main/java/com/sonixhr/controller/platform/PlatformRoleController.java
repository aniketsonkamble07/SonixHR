package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.dto.platform.PlatformRoleResponse;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.PlatformRoleService;
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
@RequestMapping("/api/platform/roles")
@RequiredArgsConstructor
public class PlatformRoleController {

    private final PlatformRoleService roleService;

    private Long getCurrentTenantId(PlatformUser currentUser) {
        Long tenantId = currentUser.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("User not associated with any tenant");
        }
        return tenantId;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_PLATFORM_ROLE')")
    public ResponseEntity<PlatformRoleResponse> createRole(
            @Valid @RequestBody PlatformRoleCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        Long tenantId = getCurrentTenantId(currentAdmin);
        log.info("Platform admin {} creating role: {} for tenant: {}",
                currentAdmin.getEmail(), request.getName(), tenantId);

        PlatformRole role = roleService.createRole(request, tenantId, currentAdmin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(role));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> assignPermissions(
            @PathVariable Long roleId,
            @RequestBody Set<Long> permissionIds,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        Long tenantId = getCurrentTenantId(currentAdmin);
        log.info("Platform admin {} assigning permissions to role: {} for tenant: {}",
                currentAdmin.getEmail(), roleId, tenantId);

        PlatformRole updated = roleService.updateRolePermissions(roleId, permissionIds, tenantId);
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<List<PlatformRoleResponse>> getAllRoles(
            @AuthenticationPrincipal PlatformUser currentUser) {

        Long tenantId = getCurrentTenantId(currentUser);
        log.debug("Getting all platform roles for tenant: {}", tenantId);

        List<PlatformRole> roles = roleService.getAllRolesForTenant(tenantId);
        return ResponseEntity.ok(roles.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> getRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal PlatformUser currentUser) {

        Long tenantId = getCurrentTenantId(currentUser);
        log.debug("Getting platform role with id: {} for tenant: {}", roleId, tenantId);

        PlatformRole role = roleService.getRoleByIdAndTenant(roleId, tenantId);
        return ResponseEntity.ok(toResponse(role));
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody PlatformRoleCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        Long tenantId = getCurrentTenantId(currentAdmin);
        log.info("Platform admin {} updating role: {} for tenant: {}",
                currentAdmin.getEmail(), roleId, tenantId);

        PlatformRole updated = roleService.updateRole(roleId, request, tenantId);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        Long tenantId = getCurrentTenantId(currentAdmin);
        log.info("Platform admin {} deleting role: {} for tenant: {}",
                currentAdmin.getEmail(), roleId, tenantId);

        roleService.deleteRole(roleId, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roleId}/users")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<List<PlatformUser>> getUsersByRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal PlatformUser currentUser) {

        Long tenantId = getCurrentTenantId(currentUser);
        log.debug("Getting users for role: {} in tenant: {}", roleId, tenantId);

        List<PlatformUser> users = roleService.getUsersByRole(roleId, tenantId);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/{roleId}/users/{userId}")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<Void> assignRoleToUser(
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        Long tenantId = getCurrentTenantId(currentAdmin);
        log.info("Platform admin {} assigning role {} to user {} in tenant {}",
                currentAdmin.getEmail(), roleId, userId, tenantId);

        roleService.assignRoleToUser(roleId, userId, tenantId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roleId}/users/{userId}")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<Void> removeRoleFromUser(
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        Long tenantId = getCurrentTenantId(currentAdmin);
        log.info("Platform admin {} removing role {} from user {} in tenant {}",
                currentAdmin.getEmail(), roleId, userId, tenantId);

        roleService.removeRoleFromUser(roleId, userId, tenantId);
        return ResponseEntity.noContent().build();
    }

    private PlatformRoleResponse toResponse(PlatformRole role) {
        if (role == null) {
            return null;
        }

        return PlatformRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isSystemRole(role.isSystemRole())
                .permissions(role.getPermissions() != null ?
                        role.getPermissions().stream()
                                .map(p -> PlatformRoleResponse.PermissionInfo.builder()
                                        .id(p.getId())
                                        .name(p.getPermission() != null ? p.getPermission().name() : null)
                                        .description(p.getDescription())
                                        .build())
                                .collect(Collectors.toList()) :
                        List.of())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}