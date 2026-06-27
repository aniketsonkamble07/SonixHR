package com.sonixhr.service.platform;

// Trigger Linter Re-evaluation
import com.sonixhr.dto.PermissionGroupDTO;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.enums.PlatformPermissionEnum;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.platform.PlatformPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class PlatformPermissionService {

    private final PlatformPermissionRepository permissionRepository;

    @Value("${app.platform.permission.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.platform.permission.cache.ttl-minutes:60}")
    private long cacheTtlMinutes;

    // Local caches
    private final Map<Long, PlatformPermission> permissionCache = new ConcurrentHashMap<>();
    private final Map<String, List<PermissionGroupDTO>> groupedPermissionsCache = new ConcurrentHashMap<>();
    private final Map<String, List<PlatformPermission>> categoryPermissionsCache = new ConcurrentHashMap<>();

    // =====================================================
    // GET METHODS (Optimized with caching)
    // =====================================================

    @Cacheable(value = "platformPermissions", key = "'grouped'", unless = "#result == null || #result.isEmpty()")
    public List<PermissionGroupDTO> getGroupedPermissions() {
        log.debug("Getting grouped permissions from DB");

        if (cacheEnabled) {
            List<PermissionGroupDTO> cached = groupedPermissionsCache.get("grouped");
            if (cached != null) {
                return cached;
            }
        }

        List<PlatformPermission> allPermissions = permissionRepository.findAll();
        if (allPermissions == null || allPermissions.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<PermissionGroupDTO.PermissionInfo>> groupedByCategory = allPermissions.stream()
                .filter(p -> p.getPermissionName() != null && p.isActive())
                .collect(Collectors.groupingBy(
                        p -> p.getEffectiveCategory(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                p -> PermissionGroupDTO.PermissionInfo.builder()
                                        .id(p.getId())
                                        .name(p.getPermissionName())
                                        .description(p.getEffectiveDescription())
                                        .category(p.getEffectiveCategory())
                                        .displayOrder(p.getEffectiveDisplayOrder())
                                        .selected(false)
                                        .build(),
                                Collectors.toList()
                        )
                ));

        groupedByCategory.forEach((category, permissions) ->
                permissions.sort(Comparator.comparing(PermissionGroupDTO.PermissionInfo::getDisplayOrder))
        );

        List<PermissionGroupDTO> result = groupedByCategory.entrySet().stream()
                .map(entry -> PermissionGroupDTO.builder()
                        .groupName(entry.getKey())
                        .permissions(entry.getValue())
                        .build())
                .sorted(Comparator.comparing(PermissionGroupDTO::getGroupName))
                .collect(Collectors.toList());

        if (cacheEnabled) {
            groupedPermissionsCache.put("grouped", result);
        }

        return result;
    }

    @Cacheable(value = "platformPermissions", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<PlatformPermission> getAllPermissions() {
        log.debug("Getting all permissions from DB");

        List<PlatformPermission> permissions = permissionRepository.findAll();

        if (cacheEnabled && permissions != null) {
            for (PlatformPermission permission : permissions) {
                permissionCache.put(permission.getId(), permission);
            }
        }

        return permissions != null ? permissions : new ArrayList<>();
    }

    public List<PlatformPermission> getPermissionsByTenant(@NonNull Long tenantId) {
        log.debug("Getting permissions for tenant: {} (system-wide)", tenantId);
        return getAllPermissions();
    }

    @Cacheable(value = "platformPermissions", key = "#id", unless = "#result == null")
    public @NonNull PlatformPermission getPermissionById(@NonNull Long id) {
        log.debug("Getting permission by id: {}", id);

        if (cacheEnabled) {
            PlatformPermission cached = permissionCache.get(id);
            if (cached != null) {
                return cached;
            }
        }

        PlatformPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + id));

        if (cacheEnabled) {
            permissionCache.put(id, permission);
        }

        if (permission == null) {
            throw new ResourceNotFoundException("Permission not found with id: " + id);
        }

        return permission;
    }

    @Cacheable(value = "platformPermissions", key = "'category:' + #category", unless = "#result == null || #result.isEmpty()")
    public List<PlatformPermission> getPermissionsByCategory(String category) {
        log.debug("Getting permissions by category: {}", category);

        if (category == null || category.trim().isEmpty()) {
            return new ArrayList<>();
        }

        if (cacheEnabled) {
            List<PlatformPermission> cached = categoryPermissionsCache.get(category);
            if (cached != null) {
                return cached;
            }
        }

        List<PlatformPermission> permissions = permissionRepository.findByCategory(category);

        if (cacheEnabled) {
            categoryPermissionsCache.put(category, permissions);
        }

        return permissions;
    }

    public List<String> getAllCategories() {
        log.debug("Getting all permission categories");
        List<String> categories = permissionRepository.findAllCategories();
        return categories != null ? categories : new ArrayList<>();
    }

    // Use the repository's String-based method
    public List<PlatformPermission> getPermissionsByType(PlatformPermissionEnum type) {
        log.debug("Getting permissions by type: {}", type);
        if (type == null) {
            return new ArrayList<>();
        }

        Optional<PlatformPermission> permissionOpt = permissionRepository.findByPermission(type.name());
        return permissionOpt.map(Collections::singletonList)
                .orElseGet(ArrayList::new);
    }

    // Convert Set of enums to Set of Strings
    public List<PlatformPermission> getPermissionsByTypes(Set<PlatformPermissionEnum> types) {
        log.debug("Getting permissions by types: {}", types);
        if (types == null || types.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> permissionNames = types.stream()
                .map(PlatformPermissionEnum::name)
                .collect(Collectors.toSet());

        return permissionRepository.findByPermissionIn(permissionNames);
    }

    // Use the repository's String-based method
    public boolean permissionExists(PlatformPermissionEnum type) {
        log.debug("Checking if permission exists: {}", type);
        return type != null && permissionRepository.existsByPermission(type.name());
    }

    // =====================================================
    // CREATE/UPDATE/DELETE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public PlatformPermission createCustomPermission(PlatformPermissionEnum type, String description,
                                                     String category, Integer displayOrder) {
        log.info("Creating custom permission: {}", type);

        if (type == null) {
            throw new IllegalArgumentException("Permission type cannot be null");
        }

        if (permissionRepository.existsByPermission(type.name())) {
            throw new IllegalStateException("Permission already exists: " + type);
        }

        // Use the entity's builder with String permission
        PlatformPermission permission = PlatformPermission.builder()
                .permission(type.name())  // Store enum name as String
                .description(description != null ? description : type.getDescription())
                .category(category != null ? category : type.getCategory())
                .displayOrder(displayOrder != null ? displayOrder : type.getOrder())
                .active(true)
                .build();

        if (permission == null) {
            throw new IllegalStateException("Failed to build platform permission");
        }

        PlatformPermission saved = permissionRepository.save(permission);

        if (cacheEnabled) {
            permissionCache.put(saved.getId(), saved);
        }

        return saved;
    }

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public PlatformPermission updatePermissionDescription(@NonNull Long id, String description) {
        log.info("Updating permission description for id: {}", id);

        PlatformPermission permission = getPermissionById(id);
        permission.setDescription(description);

        PlatformPermission updated = permissionRepository.save(permission);

        if (cacheEnabled) {
            permissionCache.put(id, updated);
        }

        return updated;
    }

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public PlatformPermission updatePermissionCategory(@NonNull Long id, String category) {
        log.info("Updating permission category for id: {}", id);

        PlatformPermission permission = getPermissionById(id);
        permission.setCategory(category);

        PlatformPermission updated = permissionRepository.save(permission);

        if (cacheEnabled) {
            permissionCache.put(id, updated);
        }

        return updated;
    }

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public PlatformPermission updatePermissionDisplayOrder(@NonNull Long id, Integer displayOrder) {
        log.info("Updating permission display order for id: {}", id);

        PlatformPermission permission = getPermissionById(id);
        permission.setDisplayOrder(displayOrder);

        PlatformPermission updated = permissionRepository.save(permission);

        if (cacheEnabled) {
            permissionCache.put(id, updated);
        }

        return updated;
    }

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public void deletePermission(@NonNull Long id) {
        log.info("Deleting permission with id: {}", id);

        PlatformPermission permission = getPermissionById(id);

        if (permissionRepository.countRolesByPermissionId(id) > 0) {
            throw new IllegalStateException("Cannot delete permission that is assigned to roles");
        }

        permission.setActive(false);
        permissionRepository.save(permission);

        if (cacheEnabled) {
            permissionCache.remove(id);
        }

        log.info("Permission deactivated: {}", id);
    }

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public void hardDeletePermission(@NonNull Long id) {
        log.warn("Hard deleting permission with id: {}", id);

        PlatformPermission permission = getPermissionById(id);

        if (permissionRepository.countRolesByPermissionId(id) > 0) {
            throw new IllegalStateException("Cannot delete permission that is assigned to roles");
        }

        permissionRepository.delete(permission);

        if (cacheEnabled) {
            permissionCache.remove(id);
        }

        log.info("Permission hard deleted: {}", id);
    }

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public PlatformPermission activatePermission(@NonNull Long id) {
        log.info("Activating permission with id: {}", id);

        PlatformPermission permission = getPermissionById(id);
        permission.setActive(true);

        PlatformPermission updated = permissionRepository.save(permission);

        if (cacheEnabled) {
            permissionCache.put(id, updated);
        }

        return updated;
    }

    // =====================================================
    // SYNC METHODS (Optimized)
    // =====================================================

    @Transactional
    @CacheEvict(value = "platformPermissions", allEntries = true)
    public List<PlatformPermission> syncPermissionsWithEnum() {
        log.info("Syncing permissions with enum");

        long startTime = System.nanoTime();
        List<PlatformPermission> newPermissions = new ArrayList<>();

        for (PlatformPermissionEnum permEnum : PlatformPermissionEnum.values()) {
            if (!permissionRepository.existsByPermission(permEnum.name())) {
                PlatformPermission permission = PlatformPermission.builder()
                        .permission(permEnum.name())
                        .description(permEnum.getDescription())
                        .category(permEnum.getCategory())
                        .displayOrder(permEnum.getOrder())
                        .active(true)
                        .build();
                newPermissions.add(permission);
            }
        }

        if (!newPermissions.isEmpty()) {
            List<PlatformPermission> saved = permissionRepository.saveAll(newPermissions);

            if (cacheEnabled) {
                for (PlatformPermission permission : saved) {
                    permissionCache.put(permission.getId(), permission);
                }
                clearGroupedCache();
            }

            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.info("Synced {} new permissions in {}ms", saved.size(), duration);

            return saved;
        }

        log.debug("No new permissions to sync");
        return new ArrayList<>();
    }

    public Map<String, Object> getPermissionStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPermissions", permissionRepository.count());
        stats.put("activePermissions", permissionRepository.countByActiveTrue());
        stats.put("categoriesCount", getAllCategories().size());

        List<Object[]> permissionsByCategory = permissionRepository.countPermissionsByCategory();
        Map<String, Long> categoryMap = new HashMap<>();
        for (Object[] row : permissionsByCategory) {
            categoryMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("permissionsByCategory", categoryMap);

        stats.put("cachedPermissions", permissionCache.size());
        stats.put("cacheEnabled", cacheEnabled);

        return stats;
    }

    // =====================================================
    // CACHE MANAGEMENT
    // =====================================================

    public void clearAllCaches() {
        permissionCache.clear();
        groupedPermissionsCache.clear();
        categoryPermissionsCache.clear();
        log.info("Cleared all platform permission caches");
    }

    public void clearGroupedCache() {
        groupedPermissionsCache.clear();
        log.debug("Cleared grouped permissions cache");
    }

    public Map<String, Integer> getCacheStats() {
        return Map.of(
                "permissionCacheSize", permissionCache.size(),
                "groupedPermissionsCacheSize", groupedPermissionsCache.size(),
                "categoryPermissionsCacheSize", categoryPermissionsCache.size()
        );
    }
}