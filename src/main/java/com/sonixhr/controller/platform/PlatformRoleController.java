package com.sonixhr.controller.platform;

import com.sonixhr.dto.platform.PlatformRoleCreateRequest;
import com.sonixhr.dto.platform.PlatformRoleResponse;
import com.sonixhr.dto.platform.PlatformRoleLookupResponse;
import com.sonixhr.dto.platform.PlatformUserResponse;
import com.sonixhr.dto.platform.PlatformRoleDeletePreviewResponse;
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
 
@Slf4j
@RestController
@RequestMapping("/api/platform/roles")
@RequiredArgsConstructor
public class PlatformRoleController {
 
    private final PlatformRoleService roleService;

    // ✅ REMOVED - Platform users don't have tenantId
    // private Long getCurrentTenantId(PlatformUser currentUser) { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_PLATFORM_ROLE')")
    public ResponseEntity<PlatformRoleResponse> createRole(
            @Valid @RequestBody PlatformRoleCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} creating role: {}", currentAdmin.getEmail(), request.getName());

        // ✅ Platform roles are global, no tenantId needed
        PlatformRole role = roleService.createRole(request, currentAdmin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(roleService.toResponse(role));
    }

    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ROLE')")
    public ResponseEntity<PlatformRoleResponse> assignPermissions(
            @PathVariable Long roleId,
            @RequestBody Set<Long> permissionIds,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} assigning permissions to role: {}", currentAdmin.getEmail(), roleId);

        // ✅ Platform roles are global, no tenantId needed
        PlatformRole updated = roleService.updateRolePermissions(roleId, permissionIds);
        return ResponseEntity.ok(roleService.toResponse(updated));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<List<PlatformRoleResponse>> getAllRoles() {
        log.debug("Getting all platform roles");

        List<PlatformRoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<PlatformRoleLookupResponse>> getRoleLookup() {
        log.debug("Getting platform roles lookup");
        List<PlatformRoleLookupResponse> roles = roleService.getPlatformRoleLookup();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<PlatformRoleResponse> getRole(@PathVariable Long roleId) {
        log.debug("Getting platform role with id: {}", roleId);

        PlatformRoleResponse role = roleService.getRoleResponseById(roleId);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('EDIT_PLATFORM_ROLE')")
    public ResponseEntity<PlatformRoleResponse> updateRole(
            @PathVariable Long roleId,
            @Valid @RequestBody PlatformRoleCreateRequest request,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} updating role: {}", currentAdmin.getEmail(), roleId);

        PlatformRole updated = roleService.updateRole(roleId, request);
        return ResponseEntity.ok(roleService.toResponse(updated));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('DELETE_PLATFORM_ROLE')")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long roleId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} deleting role: {}", currentAdmin.getEmail(), roleId);

        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{roleId}/delete-preview")
    @PreAuthorize("hasAuthority('DELETE_PLATFORM_ROLE')")
    public ResponseEntity<PlatformRoleDeletePreviewResponse> getRoleDeletePreview(
            @PathVariable Long roleId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} requesting delete preview for role: {}", currentAdmin.getEmail(), roleId);

        PlatformRoleDeletePreviewResponse response = roleService.getRoleDeletePreview(roleId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roleId}/users")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<List<PlatformUserResponse>> getUsersByRole(@PathVariable Long roleId) {
        log.debug("Getting users for role: {}", roleId);

        // ✅ Return DTO instead of entity
        List<PlatformUserResponse> users = roleService.getUsersByRole(roleId);
        return ResponseEntity.ok(users);
    }

    @PostMapping("/{roleId}/users/{userId}")
    @PreAuthorize("hasAuthority('ASSIGN_PLATFORM_ROLE')")
    public ResponseEntity<Void> assignRoleToUser(
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} assigning role {} to user {}", currentAdmin.getEmail(), roleId, userId);

        roleService.assignRoleToUser(roleId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{roleId}/users/{userId}")
    @PreAuthorize("hasAuthority('ASSIGN_PLATFORM_ROLE')")
    public ResponseEntity<Void> removeRoleFromUser(
            @PathVariable Long roleId,
            @PathVariable Long userId,
            @AuthenticationPrincipal PlatformUser currentAdmin) {

        log.info("Platform admin {} removing role {} from user {}", currentAdmin.getEmail(), roleId, userId);

        roleService.removeRoleFromUser(roleId, userId);
        return ResponseEntity.noContent().build();
    }


}