package com.sonixhr.controller.tenant;

import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.service.tenant.TenantPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tenant/permissions")
@RequiredArgsConstructor
public class TenantPermissionController {

    private final TenantPermissionService permissionService;

    @GetMapping("/groups")
    public ResponseEntity<List<PermissionGroupDTO>> getGroupedPermissions() {
        log.info("REST request to get grouped tenant permissions");
        List<PermissionGroupDTO> permissions = permissionService.getGroupedPermissions();
        return ResponseEntity.ok(permissions);
    }

    @GetMapping
    public ResponseEntity<List<com.sonixhr.entity.tenant.TenantPermission>> getAllPermissions() {
        log.info("REST request to get all tenant permissions");
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }
}