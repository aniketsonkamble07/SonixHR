package com.sonixhr.service.employee;

import com.sonixhr.dto.PermissionDTO;
import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.entity.employee.EmployeePermission;

import com.sonixhr.enums.employee.EmployeePermissionEnum;
import com.sonixhr.repository.employee.EmployeePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeePermissionService {

    private final EmployeePermissionRepository permissionRepository;

    /**
     * Initialize all employee permissions (run once on startup)
     */
    @jakarta.annotation.PostConstruct
    public void initializePermissions() {
        log.info("Initializing employee permissions...");

        for (EmployeePermissionEnum enumPermission : EmployeePermissionEnum.values()) {
            if (permissionRepository.findByPermission(enumPermission).isEmpty()) {
                EmployeePermission permission = EmployeePermission.builder()
                        .permission(enumPermission)
                        .description(enumPermission.getDescription())
                        .category(enumPermission.getCategory())
                        .displayOrder(enumPermission.getOrder())
                        .build();
                permissionRepository.save(permission);
                log.info("Added permission: {}", enumPermission.name());
            }
        }
        log.info("Employee permissions initialization completed.");
    }

    /**
     * Get all permissions grouped by category
     */
    public List<PermissionGroupDTO> getGroupedPermissions() {
        List<EmployeePermission> allPermissions = permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc();

        Map<String, List<PermissionGroupDTO.PermissionInfo>> groupedPermissions = new LinkedHashMap<>();

        for (EmployeePermission permission : allPermissions) {
            PermissionGroupDTO.PermissionInfo info = PermissionGroupDTO.PermissionInfo.builder()
                    .id(permission.getId())
                    .name(permission.getPermission().name())
                    .description(permission.getDescription())
                    .category(permission.getCategory())
                    .displayOrder(permission.getDisplayOrder())
                    .build();

            groupedPermissions.computeIfAbsent(permission.getCategory(), k -> new ArrayList<>()).add(info);
        }

        List<PermissionGroupDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<PermissionGroupDTO.PermissionInfo>> entry : groupedPermissions.entrySet()) {
            result.add(PermissionGroupDTO.builder()
                    .groupName(entry.getKey())
                    .permissions(entry.getValue())
                    .build());
        }

        return result;
    }
    /**
     * Get permissions for a specific role with selected status
     */
    public List<PermissionGroupDTO> getPermissionsWithRoleSelection(Long roleId, Set<Long> selectedPermissionIds) {
        List<PermissionGroupDTO> groups = getGroupedPermissions();

        for (PermissionGroupDTO group : groups) {
            for (PermissionGroupDTO.PermissionInfo permission : group.getPermissions()) {
                // Note: You need to add 'selected' field to PermissionInfo first
                // permission.setSelected(selectedPermissionIds.contains(permission.getId()));
            }
        }

        return groups;
    }

    /**
     * Get all permission IDs
     */
    public List<Long> getAllPermissionIds() {
        return permissionRepository.findAll().stream()
                .map(EmployeePermission::getId)
                .collect(Collectors.toList());
    }

    /**
     * Get all permissions as entities
     */
    public List<EmployeePermission> getAllPermissions() {
        return permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc();
    }
}