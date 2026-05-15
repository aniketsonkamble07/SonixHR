package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.dto.platform.PlatformRoleResponse;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.service.platform.PlatformRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/platform/roles")
@RequiredArgsConstructor
public class PlatformRoleController {

    private final PlatformRoleService roleService;

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> createRole(
            @Valid @RequestBody PlatformRoleCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {
        PlatformRole role = roleService.createRole(request, currentAdmin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(role));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> assignPermissions(
            @PathVariable Long roleId,
            @RequestBody Set<Long> permissionIds) {
        PlatformRole updated = roleService.updateRolePermissions(roleId, permissionIds);
        return ResponseEntity.ok(toResponse(updated));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<List<PlatformRoleResponse>> getAllRoles() {
        List<PlatformRole> roles = roleService.getAllRolesWithPermissions();
        return ResponseEntity.ok(roles.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> getRole(@PathVariable Long roleId) {
        PlatformRole role = roleService.getRoleById(roleId);
        return ResponseEntity.ok(toResponse(role));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('MANAGE_PLATFORM_ROLES')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    private PlatformRoleResponse toResponse(PlatformRole role) {
        return PlatformRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions().stream()
                        .map(p -> PlatformRoleResponse.PermissionInfo.builder()
                                .id(p.getId())
                                .name(p.getName().name())
                                .description(p.getDescription())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}