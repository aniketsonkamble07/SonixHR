package com.sonixhr.controller.employee;

import com.sonixhr.dto.PermissionDTO;
import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.service.employee.EmployeePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Permission Management", description = "APIs for managing tenant permissions")
public class EmployeePermissionController {

    private final EmployeePermissionService permissionService;

    /**
     * Get all permissions grouped by category
     */
    @Operation(summary = "Get grouped permissions", description = "Returns all permissions organized by category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Permissions found"),
            @ApiResponse(responseCode = "204", description = "No permissions found")
    })
    @GetMapping("/groups")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_SELF', 'EMPLOYEE_VIEW_TEAM', 'EMPLOYEE_VIEW_ALL', 'ROLE_VIEW_PERMISSIONS')")
    public ResponseEntity<List<PermissionGroupDTO>> getGroupedPermissions() {
        log.debug("REST request to get grouped permissions");

        List<PermissionGroupDTO> permissions = permissionService.getGroupedPermissions();

        if (permissions == null || permissions.isEmpty()) {
            log.debug("No permissions found");  // ✅ Changed to debug
            return ResponseEntity.noContent().build();
        }

        log.debug("Retrieved {} permission groups", permissions.size());  // ✅ Changed to debug
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))  // ✅ Added cache
                .body(permissions);
    }

    /**
     * Get all permissions (paginated)
     */
    @Operation(summary = "Get all permissions", description = "Returns paginated list of all permissions")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW_PERMISSIONS', 'ROLE_EDIT_PERMISSIONS')")
    public ResponseEntity<Page<PermissionDTO>> getAllPermissions(
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 50, sort = "category,displayOrder") Pageable pageable) {
        log.debug("REST request to get all permissions with pagination: {}", pageable);

        Page<PermissionDTO> permissions = permissionService.getAllPermissions(pageable);

        log.debug("Retrieved {} permissions out of total {}",
                permissions.getNumberOfElements(), permissions.getTotalElements());  // ✅ Changed to debug
        return ResponseEntity.ok(permissions);
    }

    /**
     * Get permissions by category
     */
    @Operation(summary = "Get permissions by category", description = "Returns permissions for a specific category")
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_VIEW_ALL', 'ROLE_VIEW_PERMISSIONS')")
    public ResponseEntity<List<PermissionGroupDTO.PermissionInfo>> getPermissionsByCategory(
            @Parameter(description = "Permission category name", required = true)
            @PathVariable @NotBlank(message = "Category cannot be blank") String category) {  // ✅ Added validation
        log.debug("REST request to get permissions for category: {}", category);

        List<PermissionGroupDTO.PermissionInfo> permissions = permissionService.getPermissionsByCategory(category);

        if (permissions == null || permissions.isEmpty()) {
            log.debug("No permissions found for category: {}", category);  // ✅ Changed to debug
            return ResponseEntity.noContent().build();
        }

        log.debug("Found {} permissions for category: {}", permissions.size(), category);  // ✅ Added log
        return ResponseEntity.ok(permissions);
    }

    /**
     * Get permission by ID
     */
    @Operation(summary = "Get permission by ID", description = "Returns a single permission by its ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW_PERMISSIONS', 'ROLE_EDIT_PERMISSIONS')")
    public ResponseEntity<PermissionDTO> getPermissionById(
            @Parameter(description = "Permission ID", required = true)
            @PathVariable @Positive(message = "ID must be positive") Long id) {  // ✅ Added validation
        log.debug("REST request to get permission by id: {}", id);

        PermissionDTO permission = permissionService.getPermissionById(id);

        if (permission == null) {
            log.warn("Permission not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(permission);
    }

    /**
     * Search permissions
     */
    @Operation(summary = "Search permissions", description = "Search permissions by name or description")
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('ROLE_VIEW_PERMISSIONS', 'ROLE_EDIT_PERMISSIONS')")
    public ResponseEntity<List<PermissionDTO>> searchPermissions(
            @Parameter(description = "Search query", required = true)
            @RequestParam @NotBlank(message = "Search query cannot be blank") String query) {  // ✅ Added validation
        log.debug("REST request to search permissions with query: {}", query);

        List<PermissionDTO> permissions = permissionService.searchPermissions(query);

        if (permissions == null || permissions.isEmpty()) {
            log.debug("No permissions found for query: {}", query);
            return ResponseEntity.noContent().build();
        }

        log.debug("Found {} permissions for query: {}", permissions.size(), query);
        return ResponseEntity.ok(permissions);
    }
}