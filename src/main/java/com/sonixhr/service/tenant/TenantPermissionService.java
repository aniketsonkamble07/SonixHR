package com.sonixhr.service.tenant;

import com.sonixhr.dto.PermissionDTO;
import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.enums.TenantPermissionEnum;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
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
public class TenantPermissionService {

    private final TenantPermissionRepository permissionRepository;

    /**
     * Initialize all permissions from enum (run once on startup)
     */
    @jakarta.annotation.PostConstruct
    public void initializePermissions() {
        log.info("Initializing tenant permissions...");

        for (TenantPermissionEnum enumPermission : TenantPermissionEnum.values()) {
            if (permissionRepository.findByPermission(enumPermission).isEmpty()) {
                TenantPermission permission = TenantPermission.builder()
                        .permission(enumPermission)
                        .description(enumPermission.getDescription())
                        .category(enumPermission.getCategory())
                        .displayOrder(enumPermission.getOrder())
                        .build();
                permissionRepository.save(permission);
                log.info("Added permission: {}", enumPermission.name());
            }
        }
        log.info("Tenant permissions initialization completed.");
    }

    /**
     * Get all permissions grouped by category (with caching)
     */
    @Cacheable(value = "permissions", key = "'grouped'")
    public List<PermissionGroupDTO> getGroupedPermissions() {
        log.debug("Fetching grouped permissions from database");

        List<TenantPermission> allPermissions = permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc();

        Map<String, List<PermissionGroupDTO.PermissionInfo>> groupedPermissions = new LinkedHashMap<>();

        for (TenantPermission permission : allPermissions) {
            PermissionGroupDTO.PermissionInfo info = PermissionGroupDTO.PermissionInfo.builder()
                    .id(permission.getId())
                    .name(permission.getPermission().name())
                    .description(permission.getDescription())
                    .category(permission.getCategory())
                    .displayOrder(permission.getDisplayOrder())
                    .selected(false)  // ✅ Initialize as false
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
     * Get permissions for a specific role with selected status ✅ FIXED
     */
    public List<PermissionGroupDTO> getPermissionsWithRoleSelection(Long roleId, Set<Long> selectedPermissionIds) {
        log.debug("Getting permissions with role selection for roleId: {}", roleId);

        List<PermissionGroupDTO> groups = getGroupedPermissions();

        // Handle null selectedPermissionIds
        Set<Long> selectedIds = selectedPermissionIds != null ? selectedPermissionIds : Collections.emptySet();

        for (PermissionGroupDTO group : groups) {
            for (PermissionGroupDTO.PermissionInfo permission : group.getPermissions()) {
                // ✅ Now properly sets the selected flag
                permission.setSelected(selectedIds.contains(permission.getId()));
            }
        }

        return groups;
    }

    /**
     * Get permissions by category ✅ NEW
     */
    public List<PermissionGroupDTO.PermissionInfo> getPermissionsByCategory(String category) {
        log.debug("Getting permissions by category: {}", category);

        List<TenantPermission> permissions = permissionRepository.findByCategoryOrderByDisplayOrderAsc(category);

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
    private PermissionDTO convertToDTO(TenantPermission permission) {
        if (permission == null) {
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
    public List<PermissionDTO> getAllPermissionDTOs() {
        log.debug("Getting all permission DTOs");

        return permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    /**
     * Get permission by ID ✅ NEW
     */
    public PermissionDTO getPermissionById(Long id) {
        log.debug("Getting permission by id: {}", id);

        TenantPermission permission = permissionRepository.findById(id)
                .orElse(null);

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
     * Search permissions by name or description ✅ NEW
     */
    public List<PermissionDTO> searchPermissions(String query) {
        log.debug("Searching permissions with query: {}", query);

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<TenantPermission> permissions = permissionRepository.searchPermissions(query);

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
     * Get all permission IDs
     */
    public List<Long> getAllPermissionIds() {
        return permissionRepository.findAll().stream()
                .map(TenantPermission::getId)
                .collect(Collectors.toList());
    }

    /**
     * Get all permissions with pagination ✅ NEW
     */
    public Page<PermissionDTO> getAllPermissions(Pageable pageable) {
        log.debug("Getting all permissions with pagination: {}", pageable);

        Page<TenantPermission> permissionPage = permissionRepository.findAll(pageable);

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
     * Clear permission cache ✅ NEW
     */
    @CacheEvict(value = "permissions", allEntries = true)
    public void clearCache() {
        log.info("Permission cache cleared");
    }
}