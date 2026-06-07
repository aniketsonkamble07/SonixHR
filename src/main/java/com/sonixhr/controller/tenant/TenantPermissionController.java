package com.sonixhr.controller.tenant;

import com.sonixhr.dto.PermissionDTO;
import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.service.tenant.TenantPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenant/permissions")
@RequiredArgsConstructor
public class TenantPermissionController {

    private final TenantPermissionService permissionService;

    /**
     * Get all permissions grouped by category
     * Access: Users who can view employee information or manage roles
     */
    @GetMapping("/groups")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_SELF', 'EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL', 'ROLE_VIEW_PERMISSIONS')")
    public ResponseEntity<List<PermissionGroupDTO>> getGroupedPermissions() {
        log.debug("REST request to get grouped tenant permissions");
        List<PermissionGroupDTO> permissions = permissionService.getGroupedPermissions();

        if (permissions == null || permissions.isEmpty()) {
            log.warn("No permissions found");
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(permissions);
    }

    /**
     * Get all permissions (DTO version - safe)
     * Access: Admin and role managers only
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW_PERMISSIONS', 'ROLE_EDIT_PERMISSIONS')")
    public ResponseEntity<List<PermissionDTO>> getAllPermissions() {
        log.debug("REST request to get all tenant permissions");
        List<PermissionDTO> permissions = permissionService.getAllPermissionDTOs();

        if (permissions == null || permissions.isEmpty()) {
            log.warn("No permissions found");
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(permissions);
    }

    /**
     * Get permissions by category
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'ROLE_VIEW_PERMISSIONS')")
    public ResponseEntity<List<PermissionGroupDTO.PermissionInfo>> getPermissionsByCategory(
            @PathVariable String category) {
        log.debug("REST request to get permissions for category: {}", category);

        List<PermissionGroupDTO.PermissionInfo> permissions = permissionService.getPermissionsByCategory(category);

        if (permissions == null || permissions.isEmpty()) {
            log.debug("No permissions found for category: {}", category);
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(permissions);
    }

    /**
     * Get permission by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW_PERMISSIONS', 'ROLE_EDIT_PERMISSIONS')")
    public ResponseEntity<PermissionDTO> getPermissionById(@PathVariable Long id) {
        log.debug("REST request to get permission by id: {}", id);

        PermissionDTO permission = permissionService.getPermissionById(id);

        if (permission == null) {
            log.warn("Permission not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(permission);
    }
}