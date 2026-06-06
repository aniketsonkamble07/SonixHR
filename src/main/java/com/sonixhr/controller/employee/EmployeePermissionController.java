package com.sonixhr.controller.employee;

import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.service.employee.EmployeePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/employee/permissions")
@RequiredArgsConstructor
public class EmployeePermissionController {

    private final EmployeePermissionService permissionService;

    @GetMapping("/groups")
    @PreAuthorize("hasAuthority('VIEW_EMPLOYEES') or hasAuthority('MANAGE_EMPLOYEE_STATUS')")
    public ResponseEntity<List<PermissionGroupDTO>> getGroupedPermissions() {
        log.info("REST request to get grouped employee permissions");
        List<PermissionGroupDTO> permissions = permissionService.getGroupedPermissions();
        return ResponseEntity.ok(permissions);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_EMPLOYEES') or hasAuthority('MANAGE_EMPLOYEE_STATUS')")
    public ResponseEntity<List<com.sonixhr.entity.employee.EmployeePermission>> getAllPermissions() {
        log.info("REST request to get all employee permissions");
        return ResponseEntity.ok(permissionService.getAllPermissions());
    }
}