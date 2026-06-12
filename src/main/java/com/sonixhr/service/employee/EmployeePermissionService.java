package com.sonixhr.service.employee;

import com.sonixhr.dto.PermissionDTO;
import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.entity.employee.EmployeePermission;
import com.sonixhr.enums.employee.EmployeePermissionEnum;
import com.sonixhr.repository.employee.EmployeePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        boolean added = false;

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
                added = true;
            }
        }

        if (added) {
            clearCache();  // Clear cache if new permissions added
        }

        log.info("Employee permissions initialization completed.");
    }

    /**
     * Get all permissions grouped by category (with caching)
     */
    @Cacheable(value = "employeePermissions", key = "'grouped'")
    public List<PermissionGroupDTO> getGroupedPermissions() {
        log.debug("Fetching grouped permissions from database");

        List<EmployeePermission> allPermissions = permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc();

        Map<String, List<PermissionGroupDTO.PermissionInfo>> groupedPermissions = new LinkedHashMap<>();

        for (EmployeePermission permission : allPermissions) {
            PermissionGroupDTO.PermissionInfo info = PermissionGroupDTO.PermissionInfo.builder()
                    .id(permission.getId())
                    .name(permission.getPermission().name())
                    .description(permission.getDescription())
                    .category(permission.getCategory())
                    .displayOrder(permission.getDisplayOrder())
                    .selected(false)
                    .build();

            groupedPermissions.computeIfAbsent(permission.getCategory(), k -> new ArrayList<>()).add(info);
        }

        List<PermissionGroupDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<PermissionGroupDTO.PermissionInfo>> entry : groupedPermissions.entrySet()) {
            // Sort permissions by displayOrder within each group
            entry.getValue().sort(Comparator.comparing(PermissionGroupDTO.PermissionInfo::getDisplayOrder));

            result.add(PermissionGroupDTO.builder()
                    .groupName(entry.getKey())
                    .permissions(entry.getValue())
                    .build());
        }

        return result;
    }

    /**
     * Get all permissions with pagination
     */
    public Page<PermissionDTO> getAllPermissions(Pageable pageable) {
        log.debug("Getting all permissions with pagination: {}", pageable);

        Page<EmployeePermission> permissionPage = permissionRepository.findAll(pageable);

        return permissionPage.map(p -> PermissionDTO.builder()
                .id(p.getId())
                .permission(p.getPermission().name())
                .description(p.getDescription())
                .category(p.getCategory())
                .displayOrder(p.getDisplayOrder())
                .selected(false)
                .build());
    }

    /**
     * Get permissions by category
     */
    public List<PermissionGroupDTO.PermissionInfo> getPermissionsByCategory(String category) {
        log.debug("Getting permissions by category: {}", category);

        if (category == null || category.trim().isEmpty()) {
            log.warn("Category parameter is null or empty");
            return Collections.emptyList();
        }

        List<EmployeePermission> permissions = permissionRepository.findByCategoryOrderByDisplayOrderAsc(category);

        return permissions.stream()
                .map(p -> PermissionGroupDTO.PermissionInfo.builder()
                        .id(p.getId())
                        .name(p.getPermission().name())
                        .description(p.getDescription())
                        .category(p.getCategory())
                        .displayOrder(p.getDisplayOrder())
                        .selected(false)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get permission by ID
     */
    public PermissionDTO getPermissionById(Long id) {
        log.debug("Getting permission by id: {}", id);

        EmployeePermission permission = permissionRepository.findById(id).orElse(null);

        if (permission == null) {
            log.warn("Permission not found with id: {}", id);
            return null;
        }

        return PermissionDTO.builder()
                .id(permission.getId())
                .permission(permission.getPermission().name())
                .description(permission.getDescription())
                .category(permission.getCategory())
                .displayOrder(permission.getDisplayOrder())
                .selected(false)
                .build();
    }

    /**
     * Search permissions by name or description
     */
    public List<PermissionDTO> searchPermissions(String query) {
        log.debug("Searching permissions with query: {}", query);

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<EmployeePermission> permissions = permissionRepository.searchPermissions(query);

        return permissions.stream()
                .map(p -> PermissionDTO.builder()
                        .id(p.getId())
                        .permission(p.getPermission().name())
                        .description(p.getDescription())
                        .category(p.getCategory())
                        .displayOrder(p.getDisplayOrder())
                        .selected(false)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get permissions for a specific role with selected status
     */
    public List<PermissionGroupDTO> getPermissionsWithRoleSelection(Long roleId, Set<Long> selectedPermissionIds) {
        log.debug("Getting permissions with role selection for roleId: {}", roleId);

        List<PermissionGroupDTO> groups = getGroupedPermissions();

        // Handle null selectedPermissionIds
        Set<Long> selectedIds = selectedPermissionIds != null ? selectedPermissionIds : Collections.emptySet();

        for (PermissionGroupDTO group : groups) {
            for (PermissionGroupDTO.PermissionInfo permission : group.getPermissions()) {
                permission.setSelected(selectedIds.contains(permission.getId()));
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
     * Clear permission cache
     */
    @CacheEvict(value = "employeePermissions", allEntries = true)
    public void clearCache() {
        log.info("Employee permission cache cleared");
    }
}