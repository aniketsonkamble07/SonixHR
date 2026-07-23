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
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TenantPermissionService {

    private final TenantPermissionRepository permissionRepository;

    @jakarta.annotation.PostConstruct
    public void initializePermissions() {
        log.info("Initializing tenant permissions...");

        for (TenantPermissionEnum enumPermission : TenantPermissionEnum.values()) {
            try {
                if (permissionRepository.findByPermission(enumPermission.name()).isEmpty()) {
                    TenantPermission permission = TenantPermission.builder()
                            .permission(enumPermission.name())
                            .description(enumPermission.getDescription())
                            .category(enumPermission.getCategory())
                            .displayOrder(enumPermission.getOrder())
                            .build();
                    permissionRepository.save(permission);
                    log.info("Added permission: {}", enumPermission.name());
                }
            } catch (Exception e) {
                log.error("Error adding permission: {}", enumPermission.name(), e);
            }
        }
        log.info("Tenant permissions initialization completed. Total: {}", permissionRepository.count());
    }

    // =====================================================
    // PERMISSION CHECK METHODS
    // =====================================================

    /**
     * Check if a permission is a billing permission
     */
    public boolean isBillingPermission(String permissionName) {
        if (permissionName == null) return false;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.isBillingPermission();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return false;
        }
    }

    /**
     * Check if a permission should be accessible to expired tenants
     */
    public boolean isExpiredTenantAccessible(String permissionName) {
        if (permissionName == null) return false;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.isExpiredTenantAccessible();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return false;
        }
    }

    /**
     * Check if a permission is a platform admin permission
     */
    public boolean isPlatformAdminPermission(String permissionName) {
        if (permissionName == null) return false;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.isPlatformAdminPermission();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return false;
        }
    }

    /**
     * Check if a permission is a view permission
     */
    public boolean isViewPermission(String permissionName) {
        if (permissionName == null) return false;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.isViewPermission();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return false;
        }
    }

    /**
     * Check if a permission is a write permission
     */
    public boolean isWritePermission(String permissionName) {
        if (permissionName == null) return false;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.isWritePermission();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return false;
        }
    }

    /**
     * Check if a permission is an export permission
     */
    public boolean isExportPermission(String permissionName) {
        if (permissionName == null) return false;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.isExportPermission();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return false;
        }
    }

    /**
     * Get the permission type for a permission
     */
    public String getPermissionType(String permissionName) {
        if (permissionName == null) return null;
        try {
            TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permissionName);
            return enumValue.getType().name();
        } catch (IllegalArgumentException e) {
            log.debug("Unknown permission: {}", permissionName);
            return null;
        }
    }

    /**
     * Check if user has any billing permission (allowed for expired tenants)
     */
    public boolean hasBillingPermission(Set<String> userPermissions) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        for (String permission : userPermissions) {
            try {
                TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permission);
                if (enumValue.isBillingPermission()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Ignore unknown permissions
            }
        }
        return false;
    }

    /**
     * Check if user has any platform admin permission
     */
    public boolean hasPlatformAdminPermission(Set<String> userPermissions) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        for (String permission : userPermissions) {
            try {
                TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permission);
                if (enumValue.isPlatformAdminPermission()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Ignore unknown permissions
            }
        }
        return false;
    }

    /**
     * Check if user has any view permission
     */
    public boolean hasViewPermission(Set<String> userPermissions) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        for (String permission : userPermissions) {
            try {
                TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permission);
                if (enumValue.isViewPermission()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Ignore unknown permissions
            }
        }
        return false;
    }

    /**
     * Check if user has any write permission
     */
    public boolean hasWritePermission(Set<String> userPermissions) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        for (String permission : userPermissions) {
            try {
                TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permission);
                if (enumValue.isWritePermission()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Ignore unknown permissions
            }
        }
        return false;
    }

    /**
     * Check if user has any export permission
     */
    public boolean hasExportPermission(Set<String> userPermissions) {
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        for (String permission : userPermissions) {
            try {
                TenantPermissionEnum enumValue = TenantPermissionEnum.valueOf(permission);
                if (enumValue.isExportPermission()) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Ignore unknown permissions
            }
        }
        return false;
    }

    // =====================================================
    // PERMISSION LIST METHODS
    // =====================================================

    /**
     * Get billing permissions only
     */
    public List<String> getBillingPermissions() {
        return Arrays.stream(TenantPermissionEnum.values())
                .filter(TenantPermissionEnum::isBillingPermission)
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toList());
    }

    /**
     * Get platform admin permissions
     */
    public List<String> getPlatformAdminPermissions() {
        return Arrays.stream(TenantPermissionEnum.values())
                .filter(TenantPermissionEnum::isPlatformAdminPermission)
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toList());
    }

    /**
     * Get view permissions
     */
    public List<String> getViewPermissions() {
        return Arrays.stream(TenantPermissionEnum.values())
                .filter(TenantPermissionEnum::isViewPermission)
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toList());
    }

    /**
     * Get write permissions
     */
    public List<String> getWritePermissions() {
        return Arrays.stream(TenantPermissionEnum.values())
                .filter(TenantPermissionEnum::isWritePermission)
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toList());
    }

    /**
     * Get export permissions
     */
    public List<String> getExportPermissions() {
        return Arrays.stream(TenantPermissionEnum.values())
                .filter(TenantPermissionEnum::isExportPermission)
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toList());
    }

    /**
     * Get all permission names as a set
     */
    public Set<String> getAllPermissionNames() {
        return Arrays.stream(TenantPermissionEnum.values())
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toSet());
    }

    /**
     * Get permission names by category (returns List<String>)
     */
    public List<String> getPermissionNamesByCategory(String category) {
        return Arrays.stream(TenantPermissionEnum.values())
                .filter(p -> p.getCategory().equals(category))
                .map(TenantPermissionEnum::name)
                .collect(Collectors.toList());
    }

    /**
     * Get all categories
     */
    public Set<String> getAllCategories() {
        return Arrays.stream(TenantPermissionEnum.values())
                .map(TenantPermissionEnum::getCategory)
                .collect(Collectors.toSet());
    }

    // =====================================================
    // EXISTING METHODS (UPDATED WITH METADATA)
    // =====================================================

    @Cacheable(value = "permissions", key = "'grouped'")
    public List<PermissionGroupDTO> getGroupedPermissions() {
        log.debug("Fetching grouped permissions from database");

        List<TenantPermission> allPermissions;
        try {
            allPermissions = permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc();
            log.debug("Found {} permissions with ordering", allPermissions.size());
        } catch (Exception e) {
            log.error("Error using ordered query, falling back to findAll()", e);
            allPermissions = permissionRepository.findAll();
            log.debug("Found {} permissions with findAll()", allPermissions.size());
        }

        if (allPermissions == null || allPermissions.isEmpty()) {
            log.warn("No permissions found in database!");
            return new ArrayList<>();
        }

        Map<String, List<PermissionGroupDTO.PermissionInfo>> groupedPermissions = new LinkedHashMap<>();
        int nullCategoryCount = 0;
        int nullPermissionCount = 0;

        for (TenantPermission permission : allPermissions) {
            try {
                String permissionName = permission.getPermission();
                if (permissionName == null) {
                    nullPermissionCount++;
                    log.warn("Permission with ID {} has null permission, skipping", permission.getId());
                    continue;
                }

                String category = permission.getCategory();
                if (category == null || category.trim().isEmpty()) {
                    category = "General";
                    nullCategoryCount++;
                    log.debug("Permission {} has null category, assigning to 'General'", permissionName);
                }

                // Get enum value for metadata
                TenantPermissionEnum enumValue = null;
                try {
                    enumValue = TenantPermissionEnum.valueOf(permissionName);
                } catch (IllegalArgumentException e) {
                    log.debug("Permission {} not found in enum", permissionName);
                }

                PermissionGroupDTO.PermissionInfo info = PermissionGroupDTO.PermissionInfo.builder()
                        .id(permission.getId())
                        .name(permissionName)
                        .description(permission.getDescription() != null ? permission.getDescription() : "")
                        .category(category)
                        .displayOrder(permission.getDisplayOrder() != null ? permission.getDisplayOrder() : 999)
                        .selected(false)
                        .billingPermission(isBillingPermission(permissionName))
                        .platformAdminPermission(isPlatformAdminPermission(permissionName))
                        .viewPermission(enumValue != null && enumValue.isViewPermission())
                        .writePermission(enumValue != null && enumValue.isWritePermission())
                        .exportPermission(enumValue != null && enumValue.isExportPermission())
                        .permissionType(enumValue != null ? enumValue.getType().name() : null)
                        .build();

                groupedPermissions.computeIfAbsent(category, k -> new ArrayList<>()).add(info);

            } catch (Exception e) {
                log.error("Error processing permission with ID: {}", permission.getId(), e);
            }
        }

        if (nullCategoryCount > 0) {
            log.warn("Found {} permissions with null category, assigned to 'General'", nullCategoryCount);
        }

        if (nullPermissionCount > 0) {
            log.warn("Found {} permissions with null permission, skipped", nullPermissionCount);
        }

        for (Map.Entry<String, List<PermissionGroupDTO.PermissionInfo>> entry : groupedPermissions.entrySet()) {
            entry.getValue().sort(Comparator.comparing(PermissionGroupDTO.PermissionInfo::getDisplayOrder));
        }

        List<PermissionGroupDTO> result = groupedPermissions.entrySet().stream()
                .map(entry -> PermissionGroupDTO.builder()
                        .groupName(entry.getKey())
                        .permissions(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(PermissionGroupDTO::getGroupName))
                .collect(Collectors.toList());

        log.debug("Returning {} permission groups", result.size());
        return result;
    }

    public List<PermissionGroupDTO> getPermissionsWithRoleSelection(@NonNull Long roleId, Set<Long> selectedPermissionIds) {
        log.debug("Getting permissions with role selection for roleId: {}", roleId);

        List<PermissionGroupDTO> groups = getGroupedPermissions();
        Set<Long> selectedIds = selectedPermissionIds != null ? selectedPermissionIds : Collections.emptySet();

        for (PermissionGroupDTO group : groups) {
            for (PermissionGroupDTO.PermissionInfo permission : group.getPermissions()) {
                permission.setSelected(selectedIds.contains(permission.getId()));
            }
        }

        return groups;
    }

    /**
     * Get permissions by category with full metadata (returns List<PermissionGroupDTO.PermissionInfo>)
     */
    public List<PermissionGroupDTO.PermissionInfo> getPermissionsByCategory(String category) {
        log.debug("Getting permissions by category: {}", category);

        if (category == null || category.trim().isEmpty()) {
            log.warn("Category is null or empty");
            return new ArrayList<>();
        }

        List<TenantPermission> permissions;
        try {
            permissions = permissionRepository.findByCategoryOrderByDisplayOrderAsc(category);
        } catch (Exception e) {
            log.error("Error fetching permissions for category: {}", category, e);
            permissions = permissionRepository.findByCategory(category);
        }

        if (permissions == null || permissions.isEmpty()) {
            log.warn("No permissions found for category: {}", category);
            return new ArrayList<>();
        }

        return permissions.stream()
                .map(p -> {
                    try {
                        String permissionName = p.getPermission();
                        TenantPermissionEnum enumValue = null;
                        try {
                            enumValue = TenantPermissionEnum.valueOf(permissionName);
                        } catch (IllegalArgumentException e) {
                            // Ignore
                        }

                        return PermissionGroupDTO.PermissionInfo.builder()
                                .id(p.getId())
                                .name(permissionName != null ? permissionName : "UNKNOWN")
                                .description(p.getDescription() != null ? p.getDescription() : "")
                                .category(p.getCategory() != null ? p.getCategory() : category)
                                .displayOrder(p.getDisplayOrder() != null ? p.getDisplayOrder() : 999)
                                .selected(false)
                                .billingPermission(isBillingPermission(permissionName))
                                .platformAdminPermission(isPlatformAdminPermission(permissionName))
                                .viewPermission(enumValue != null && enumValue.isViewPermission())
                                .writePermission(enumValue != null && enumValue.isWritePermission())
                                .exportPermission(enumValue != null && enumValue.isExportPermission())
                                .permissionType(enumValue != null ? enumValue.getType().name() : null)
                                .build();
                    } catch (Exception e) {
                        log.error("Error converting permission to DTO: {}", p.getId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private PermissionDTO convertToDTO(TenantPermission permission) {
        if (permission == null || permission.getPermission() == null) {
            return null;
        }

        String permissionName = permission.getPermission();
        TenantPermissionEnum enumValue = null;
        try {
            enumValue = TenantPermissionEnum.valueOf(permissionName);
        } catch (IllegalArgumentException e) {
            // Ignore
        }

        return PermissionDTO.builder()
                .id(permission.getId())
                .permission(permissionName)
                .description(permission.getDescription() != null ? permission.getDescription() : "")
                .category(permission.getCategory() != null ? permission.getCategory() : "General")
                .displayOrder(permission.getDisplayOrder() != null ? permission.getDisplayOrder() : 999)
                .selected(false)
                .billingPermission(isBillingPermission(permissionName))
                .platformAdminPermission(isPlatformAdminPermission(permissionName))
                .viewPermission(enumValue != null && enumValue.isViewPermission())
                .writePermission(enumValue != null && enumValue.isWritePermission())
                .exportPermission(enumValue != null && enumValue.isExportPermission())
                .permissionType(enumValue != null ? enumValue.getType().name() : null)
                .build();
    }

    public List<PermissionDTO> getAllPermissionDTOs() {
        log.debug("Getting all permission DTOs");

        List<TenantPermission> permissions = permissionRepository.findAllByOrderByCategoryAscDisplayOrderAsc();

        return permissions.stream()
                .map(this::convertToDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public PermissionDTO getPermissionById(@NonNull Long id) {
        log.debug("Getting permission by id: {}", id);

        Optional<TenantPermission> permissionOpt = permissionRepository.findById(id);

        if (permissionOpt.isEmpty()) {
            log.warn("Permission not found with id: {}", id);
            return null;
        }

        return convertToDTO(permissionOpt.get());
    }

    public List<PermissionDTO> searchPermissions(String query) {
        log.debug("Searching permissions with query: {}", query);

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<TenantPermission> permissions = permissionRepository.searchPermissions(query);

        return permissions.stream()
                .map(this::convertToDTO)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Long> getAllPermissionIds() {
        return permissionRepository.findAll().stream()
                .map(TenantPermission::getId)
                .collect(Collectors.toList());
    }

    public Page<PermissionDTO> getAllPermissions(@NonNull Pageable pageable) {
        log.debug("Getting all permissions with pagination: {}", pageable);

        Page<TenantPermission> permissionPage = permissionRepository.findAll(pageable);

        return permissionPage.map(this::convertToDTO);
    }

    @CacheEvict(value = "permissions", allEntries = true)
    public void clearCache() {
        log.info("Permission cache cleared");
    }
}