package com.sonixhr.controller.platform;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/permissions")
@RequiredArgsConstructor
public class PlatformPermissionController {

    private final PlatformPermissionRepository permissionRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PLATFORM_ROLES')")
    public ResponseEntity<List<PlatformPermission>> getAllPermissions() {
        return ResponseEntity.ok(permissionRepository.findAll());
    }
}