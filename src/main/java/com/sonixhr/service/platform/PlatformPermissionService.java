package com.sonixhr.service.platform;

import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformPermissionService {

    private final PlatformPermissionRepository permissionRepository;

    /**
     * Get grouped permissions - matches controller's getGroupedPermissions()
     */
    public List<PermissionGroupDTO> getGroupedPermissions() {
        log.debug("Getting grouped permissions");

        List<PlatformPermission> allPermissions = permissionRepository.findAll();
        if (allPermissions == null || allPermissions.isEmpty()) {
            return new ArrayList<>();
        }

        // Group permissions by category
        Map<String, List<PermissionGroupDTO.PermissionInfo>> groupedByCategory = allPermissions.stream()
                .filter(p -> p.getPermission() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getCategory() != null ? p.getCategory() : "General",
                        Collectors.mapping(
                                p -> PermissionGroupDTO.PermissionInfo.builder()
                                        .id(p.getId())
                                        .name(p.getPermission().name())
                                        .description(p.getDescription() != null ? p.getDescription() : p.getPermission().getDescription())
                                        .category(p.getCategory())
                                        .displayOrder(p.getDisplayOrder() != null ? p.getDisplayOrder() : 999)
                                        .build(),
                                Collectors.toList()
                        )
                ));

        // Sort permissions within each group by displayOrder
        groupedByCategory.forEach((category, permissions) ->
                permissions.sort(Comparator.comparing(PermissionGroupDTO.PermissionInfo::getDisplayOrder))
        );

        // Convert to DTO list sorted by category name
        return groupedByCategory.entrySet().stream()
                .map(entry -> PermissionGroupDTO.builder()
                        .groupName(entry.getKey())
                        .permissions(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(PermissionGroupDTO::getGroupName))
                .collect(Collectors.toList());
    }

    /**
     * Get all permissions - matches controller's getAllPermissions()
     */
    public List<PlatformPermission> getAllPermissions() {
        log.debug("Getting all permissions");
        List<PlatformPermission> permissions = permissionRepository.findAll();
        return permissions != null ? permissions : new ArrayList<>();
    }

    // Additional methods that might be useful (optional)

    public List<PlatformPermission> getPermissionsByTenant(Long tenantId) {
        log.debug("Getting permissions for tenant: {}", tenantId);
        if (tenantId == null) {
            return new ArrayList<>();
        }
        return permissionRepository.findByTenantIdOrTenantIdIsNull(tenantId);
    }

    public PlatformPermission getPermissionById(Long id) {
        log.debug("Getting permission by id: {}", id);
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + id));
    }

    public List<PlatformPermission> getPermissionsByCategory(String category) {
        log.debug("Getting permissions by category: {}", category);
        if (category == null || category.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return permissionRepository.findByCategory(category);
    }

    public List<String> getAllCategories() {
        log.debug("Getting all permission categories");
        List<String> categories = permissionRepository.findAllCategories();
        return categories != null ? categories : new ArrayList<>();
    }

    public List<PlatformPermission> getPermissionsByType(PlatformPermissionEnum type) {
        log.debug("Getting permissions by type: {}", type);
        if (type == null) {
            return new ArrayList<>();
        }

        Optional<PlatformPermission> permissionOpt = permissionRepository.findByPermission(type);
        return permissionOpt.map(Collections::singletonList)
                .orElseGet(ArrayList::new);
    }

    public List<PlatformPermission> getPermissionsByTypes(Set<PlatformPermissionEnum> types) {
        log.debug("Getting permissions by types: {}", types);
        if (types == null || types.isEmpty()) {
            return new ArrayList<>();
        }
        return permissionRepository.findByPermissionIn(types);
    }

    public boolean permissionExists(PlatformPermissionEnum type) {
        log.debug("Checking if permission exists: {}", type);
        return type != null && permissionRepository.existsByPermission(type);
    }

    @Transactional
    public List<PlatformPermission> initializePermissionsForTenant(Long tenantId) {
        log.info("Initializing all permissions for tenant: {}", tenantId);

        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        List<PlatformPermission> permissions = new ArrayList<>();

        for (PlatformPermissionEnum permEnum : PlatformPermissionEnum.values()) {
            if (!permissionRepository.existsByTenantIdAndPermission(tenantId, permEnum)) {
                PlatformPermission permission = PlatformPermission.builder()
                        .tenantId(tenantId)
                        .permission(permEnum)
                        .description(permEnum.getDescription())
                        .category(permEnum.getCategory())
                        .displayOrder(permEnum.getOrder())
                        .build();
                permissions.add(permission);
            }
        }

        if (!permissions.isEmpty()) {
            List<PlatformPermission> savedPermissions = permissionRepository.saveAll(permissions);
            log.info("Initialized {} permissions for tenant: {}", savedPermissions.size(), tenantId);
            return savedPermissions;
        }

        return new ArrayList<>();
    }

    @Transactional
    public PlatformPermission createCustomPermission(PlatformPermissionEnum type, String description,
                                                     String category, Integer displayOrder, Long tenantId) {
        log.info("Creating custom permission: {} for tenant: {}", type, tenantId);

        if (type == null) {
            throw new IllegalArgumentException("Permission type cannot be null");
        }

        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        if (permissionRepository.existsByTenantIdAndPermission(tenantId, type)) {
            throw new IllegalStateException("Permission already exists: " + type);
        }

        PlatformPermission permission = PlatformPermission.builder()
                .tenantId(tenantId)
                .permission(type)
                .description(description != null ? description : type.getDescription())
                .category(category != null ? category : type.getCategory())
                .displayOrder(displayOrder != null ? displayOrder : type.getOrder())
                .build();

        return permissionRepository.save(permission);
    }

    @Transactional
    public PlatformPermission updatePermissionDescription(Long id, String description) {
        log.info("Updating permission description for id: {}", id);

        if (id == null) {
            throw new IllegalArgumentException("Permission ID cannot be null");
        }

        PlatformPermission permission = getPermissionById(id);
        permission.setDescription(description);
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(Long id, Long tenantId) {
        log.info("Deleting permission with id: {} for tenant: {}", id, tenantId);

        if (id == null) {
            throw new IllegalArgumentException("Permission ID cannot be null");
        }

        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        PlatformPermission permission = getPermissionById(id);

        if (permission.getTenantId() == null) {
            throw new SecurityException("Cannot delete global permissions");
        }

        if (!permission.getTenantId().equals(tenantId)) {
            throw new SecurityException("Permission does not belong to tenant");
        }

        if (permissionRepository.countRolesByPermissionId(id) > 0) {
            throw new IllegalStateException("Cannot delete permission that is assigned to roles");
        }

        permissionRepository.delete(permission);
    }

    @Transactional
    public List<PlatformPermission> syncPermissionsWithEnum(Long tenantId) {
        log.info("Syncing permissions with enum for tenant: {}", tenantId);

        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }

        List<PlatformPermission> newPermissions = new ArrayList<>();

        for (PlatformPermissionEnum permEnum : PlatformPermissionEnum.values()) {
            if (!permissionRepository.existsByTenantIdAndPermission(tenantId, permEnum)) {
                PlatformPermission permission = PlatformPermission.builder()
                        .tenantId(tenantId)
                        .permission(permEnum)
                        .description(permEnum.getDescription())
                        .category(permEnum.getCategory())
                        .displayOrder(permEnum.getOrder())
                        .build();
                newPermissions.add(permission);
            }
        }

        if (!newPermissions.isEmpty()) {
            return permissionRepository.saveAll(newPermissions);
        }

        return new ArrayList<>();
    }
}