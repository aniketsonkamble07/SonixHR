package com.sonixhr.controller.platform;

import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.dto.PermissionDTO;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.service.platform.PlatformPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
    public ResponseEntity<List<PermissionDTO>> getAllPermissions() {
        log.info("REST request to get all platform permissions");
        List<PermissionDTO> permissions = permissionService.getAllPermissions().stream()
                .map(PlatformPermission::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(permissions);
    }
}