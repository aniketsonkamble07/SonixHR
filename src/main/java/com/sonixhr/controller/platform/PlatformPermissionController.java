package com.sonixhr.controller.platform;

import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.service.platform.PlatformPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/platform/permissions")
@RequiredArgsConstructor
public class PlatformPermissionController {

    private final PlatformPermissionService permissionService;

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<PermissionGroupDTO>> getGroupedPermissions() {
        log.info("REST request to get grouped platform permissions");
        List<PermissionGroupDTO> permissions = permissionService.getGroupedPermissions();
        return ResponseEntity.ok(permissions);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES') or hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<List<PlatformPermission>> getAllPermissions() {
        log.info("REST request to get all platform permissions");
        List<PlatformPermission> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(permissions);
    }
}