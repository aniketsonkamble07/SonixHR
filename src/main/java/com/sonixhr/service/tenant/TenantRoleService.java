package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.dto.tenant.TenantRoleSummaryResponse;
import com.sonixhr.dto.tenant.TenantRoleLookupResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.security.TenantDynamicRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class TenantRoleService {

    private final TenantRoleRepository roleRepository;
    private final TenantPermissionRepository permissionRepository;
    private final EmployeeRepository employeeRepository;
    private final TenantDynamicRoleService dynamicRoleService;

    @Value("${app.tenant.role.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.tenant.role.cache.ttl-minutes:30}")
    private long cacheTtlMinutes;

    // Local caches
    private final Map<String, TenantRole> roleCache = new ConcurrentHashMap<>();
    private final Map<Long, List<TenantRole>> tenantRolesCache = new ConcurrentHashMap<>();
    private final Map<Long, List<Employee>> usersByRoleCache = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> userRoleAssignmentCache = new ConcurrentHashMap<>();

    // =====================================================
    // CREATE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public TenantRole createRole(TenantRoleCreateRequest request, Long tenantId, Long createdBy) {
        long startTime = System.nanoTime();
        log.info("Creating tenant role: {} for tenant: {}", request.getName(), tenantId);

        if (roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role already exists: " + request.getName() + " for this tenant");
        }

        Set<TenantPermission> permissions = fetchPermissions(request.getPermissionIds());

        boolean isFirstRole = roleRepository.countByTenantId(tenantId) == 0;
        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : isFirstRole;

        // Determine category and priority from request or derive from name
        String category = determineCategory(request);
        Integer priority = determinePriority(request, category);

        TenantRole role = TenantRole.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .category(category)
                .priority(priority)
                .isDefault(isDefault)
                .active(true)
                .permissions(permissions)
                .createdBy(createdBy)
                .build();

        TenantRole savedRole = roleRepository.save(role);

        // Update caches
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, savedRole.getId());
            roleCache.put(cacheKey, savedRole);
            tenantRolesCache.remove(tenantId);
        }

        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        log.info("Tenant role created: {} for tenant {} in {}ms", savedRole.getName(), tenantId, duration);

        return savedRole;
    }

    private Set<TenantPermission> fetchPermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }

        List<TenantPermission> permissions = permissionRepository.findAllById(permissionIds);

        if (permissions.size() != permissionIds.size()) {
            Set<Long> foundIds = permissions.stream()
                    .map(TenantPermission::getId)
                    .collect(Collectors.toSet());
            Set<Long> missingIds = permissionIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toSet());
            throw new BusinessException("Invalid permission IDs: " + missingIds);
        }

        return permissions.stream()
                .filter(TenantPermission::isActive)
                .collect(Collectors.toSet());
    }

    private String determineCategory(TenantRoleCreateRequest request) {
        if (request.getCategory() != null && !request.getCategory().isEmpty()) {
            return request.getCategory();
        }

        String name = request.getName().toUpperCase();

        // Dynamic category detection based on name patterns (configurable)
        if (name.contains("ADMIN")) return "ADMINISTRATION";
        if (name.contains("MANAGER")) return "MANAGEMENT";
        if (name.contains("HR")) return "HUMAN_RESOURCES";
        if (name.contains("LEAD")) return "LEADERSHIP";
        if (name.contains("EMPLOYEE")) return "EMPLOYMENT";

        return "CUSTOM";
    }

    private Integer determinePriority(TenantRoleCreateRequest request, String category) {
        if (request.getPriority() != null) {
            return request.getPriority();
        }

        // Dynamic priority based on category
        switch (category) {
            case "ADMINISTRATION": return 100;
            case "MANAGEMENT": return 80;
            case "LEADERSHIP": return 70;
            case "HUMAN_RESOURCES": return 60;
            case "EMPLOYMENT": return 40;
            default: return 50;
        }
    }

    private String buildCacheKey(Long tenantId, Long roleId) {
        return tenantId + ":" + roleId;
    }

    // =====================================================
    // UPDATE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public TenantRole updateRole(Long roleId, TenantRoleCreateRequest request, Long tenantId) {
        log.info("Updating tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (!role.getName().equals(request.getName()) &&
                roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new BusinessException("Role name already exists: " + request.getName());
        }

        boolean changed = false;

        if (request.getName() != null && !request.getName().equals(role.getName())) {
            role.setName(request.getName());
            changed = true;
        }

        if (request.getDescription() != null && !request.getDescription().equals(role.getDescription())) {
            role.setDescription(request.getDescription());
            changed = true;
        }

        if (request.getCategory() != null && !request.getCategory().equals(role.getCategory())) {
            role.setCategory(request.getCategory());
            changed = true;
        }

        if (request.getPriority() != null && !request.getPriority().equals(role.getPriority())) {
            role.setPriority(request.getPriority());
            changed = true;
        }

        // Update permissions if provided
        if (request.getPermissionIds() != null) {
            Set<TenantPermission> permissions = fetchPermissions(request.getPermissionIds());
            role.setPermissions(permissions);
            changed = true;
            log.info("Updated permissions for role: {}", roleId);
        }

        // Handle default role flag
        if (request.getIsDefault() != null) {
            changed = handleDefaultRoleFlag(role, request.getIsDefault(), tenantId, roleId) || changed;
        }

        if (changed) {
            TenantRole updatedRole = roleRepository.save(role);

            // Invalidate caches
            invalidateRoleCaches(tenantId, roleId);

            // Invalidate affected users
            invalidateUsersWithRole(roleId, tenantId);

            log.info("Tenant role updated: {}", roleId);
            return updatedRole;
        }

        return role;
    }

    private boolean handleDefaultRoleFlag(TenantRole role, Boolean isDefault, Long tenantId, Long roleId) {
        if (isDefault && !role.isDefault()) {
            // Remove default flag from all other roles
            List<TenantRole> defaultRoles = roleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
            for (TenantRole defaultRole : defaultRoles) {
                if (!defaultRole.getId().equals(roleId)) {
                    defaultRole.removeDefaultFlag();
                    roleRepository.save(defaultRole);
                }
            }
            role.setAsDefault();
            return true;
        } else if (!isDefault && role.isDefault()) {
            long defaultCount = roleRepository.countByTenantIdAndIsDefaultTrue(tenantId);
            if (defaultCount <= 1) {
                throw new BusinessException("Cannot remove default flag. At least one role must be default.");
            }
            role.removeDefaultFlag();
            return true;
        }
        return false;
    }

    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public TenantRole updateRolePermissions(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Updating permissions for role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        // Calculate diff to minimize operations
        Set<Long> currentIds = role.getPermissions().stream()
                .map(TenantPermission::getId)
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(permissionIds);
        toAdd.removeAll(currentIds);

        Set<Long> toRemove = new HashSet<>(currentIds);
        toRemove.removeAll(permissionIds);

        if (permissionIds == null || permissionIds.isEmpty()) {
            role.setPermissions(new HashSet<>());
        } else if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            Set<TenantPermission> permissions = fetchPermissions(permissionIds);
            role.setPermissions(permissions);
        }

        TenantRole updatedRole = roleRepository.save(role);

        // Invalidate caches
        invalidateRoleCaches(tenantId, roleId);
        invalidateUsersWithRole(roleId, tenantId);

        log.info("Permissions updated for role: {} (added: {}, removed: {})",
                roleId, toAdd.size(), toRemove.size());

        return updatedRole;
    }

    @Transactional
    public TenantRole addPermissionsToRole(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Adding permissions to role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        Set<TenantPermission> permissions = fetchPermissions(permissionIds);
        role.getPermissions().addAll(permissions);

        TenantRole updatedRole = roleRepository.save(role);

        // Invalidate caches
        invalidateRoleCaches(tenantId, roleId);
        invalidateUsersWithRole(roleId, tenantId);

        return updatedRole;
    }

    @Transactional
    public TenantRole removePermissionsFromRole(Long roleId, Set<Long> permissionIds, Long tenantId) {
        log.info("Removing permissions from role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        role.getPermissions().removeIf(p -> permissionIds.contains(p.getId()));

        TenantRole updatedRole = roleRepository.save(role);

        // Invalidate caches
        invalidateRoleCaches(tenantId, roleId);
        invalidateUsersWithRole(roleId, tenantId);

        return updatedRole;
    }

    private void invalidateRoleCaches(Long tenantId, Long roleId) {
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            roleCache.remove(cacheKey);
            tenantRolesCache.remove(tenantId);
        }
    }

    private void invalidateUsersWithRole(Long roleId, Long tenantId) {
        List<Employee> users = employeeRepository.findByRolesIdAndTenantId(roleId, tenantId);
        for (Employee user : users) {
            dynamicRoleService.invalidateEmployeeAuthorityCache(user.getEmail(), tenantId);
            userRoleAssignmentCache.remove(user.getId());
        }
        usersByRoleCache.remove(roleId);
        log.debug("Invalidated cache for {} users with role: {}", users.size(), roleId);
    }

    // =====================================================
    // GET METHODS (Optimized with caching)
    // =====================================================

    public TenantRole getRoleByIdAndTenant(Long roleId, Long tenantId) {
        log.debug("Fetching tenant role from DB: {} for tenant: {}", roleId, tenantId);

        // Check local cache first
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            TenantRole cached = roleCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        TenantRole role = roleRepository.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role not found with id: " + roleId + " for tenant: " + tenantId));

        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            roleCache.put(cacheKey, role);
        }

        return role;
    }

    @Cacheable(value = "tenantRoles", key = "#roleId + ':' + #tenantId", unless = "#result == null")
    public TenantRoleResponse getRoleResponseByIdAndTenant(Long roleId, Long tenantId) {
        log.debug("Fetching tenant role DTO: {} for tenant: {}", roleId, tenantId);
        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);
        long count = employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId);
        return toResponse(role, (int) count);
    }

    public TenantRole getRoleByIdWithPermissions(Long roleId, Long tenantId) {
        log.debug("Fetching tenant role with permissions: {} for tenant: {}", roleId, tenantId);

        return roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role not found with id: " + roleId + " for tenant: " + tenantId));
    }

    @Cacheable(value = "tenantRolesList", key = "#tenantId", unless = "#result == null || #result.isEmpty()")
    public List<TenantRoleSummaryResponse> getAllRolesForTenant(Long tenantId) {
        log.debug("Fetching all roles for tenant: {}", tenantId);

        // Check local cache
        if (cacheEnabled) {
            List<TenantRole> cached = tenantRolesCache.get(tenantId);
            if (cached != null) {
                Map<Long, Integer> roleCounts = getRoleEmployeeCounts(tenantId);
                return cached.stream()
                        .map(role -> toSummaryResponse(role, roleCounts.getOrDefault(role.getId(), 0)))
                        .collect(Collectors.toList());
            }
        }

        List<TenantRole> roles = roleRepository.findAllByTenantId(tenantId);

        if (cacheEnabled) {
            tenantRolesCache.put(tenantId, roles);
            // Also cache individual roles
            for (TenantRole role : roles) {
                String cacheKey = buildCacheKey(tenantId, role.getId());
                roleCache.put(cacheKey, role);
            }
        }

        Map<Long, Integer> roleCounts = getRoleEmployeeCounts(tenantId);
        return roles.stream()
                .map(role -> toSummaryResponse(role, roleCounts.getOrDefault(role.getId(), 0)))
                .collect(Collectors.toList());
    }

    public TenantRoleSummaryResponse toSummaryResponse(TenantRole role, Integer employeeCount) {
        if (role == null) {
            return null;
        }

        return TenantRoleSummaryResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isDefault(role.isDefault())
                .employeeCount(employeeCount)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    public TenantRoleLookupResponse toLookupResponse(TenantRole role) {
        if (role == null) {
            return null;
        }

        return TenantRoleLookupResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .isDefault(role.isDefault())
                .build();
    }

    @Cacheable(value = "tenantRolesLookup", key = "#tenantId", unless = "#result == null || #result.isEmpty()")
    public List<TenantRoleLookupResponse> getRoleLookupForTenant(Long tenantId) {
        log.debug("Fetching role lookup for tenant: {}", tenantId);

        // Check local cache
        if (cacheEnabled) {
            List<TenantRole> cached = tenantRolesCache.get(tenantId);
            if (cached != null) {
                return cached.stream()
                        .map(this::toLookupResponse)
                        .collect(Collectors.toList());
            }
        }

        List<TenantRole> roles = roleRepository.findAllByTenantId(tenantId);

        if (cacheEnabled) {
            tenantRolesCache.put(tenantId, roles);
            // Also cache individual roles
            for (TenantRole role : roles) {
                String cacheKey = buildCacheKey(tenantId, role.getId());
                roleCache.put(cacheKey, role);
            }
        }

        return roles.stream()
                .map(this::toLookupResponse)
                .collect(Collectors.toList());
    }

    public TenantRoleSummaryResponse toSummaryResponse(TenantRole role) {
        if (role == null) {
            return null;
        }
        int count = 0;
        if (role.getTenantId() != null && role.getId() != null) {
            count = (int) employeeRepository.countUsersByRoleIdAndTenantId(role.getId(), role.getTenantId());
        }
        return toSummaryResponse(role, count);
    }

    private Map<Long, Integer> getRoleEmployeeCounts(Long tenantId) {
        List<Object[]> counts = employeeRepository.countEmployeesForRolesByTenantId(tenantId);
        return counts.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Long) row[1]).intValue(),
                        (v1, v2) -> v1
                ));
    }

    public TenantRoleResponse toResponse(TenantRole role, Integer employeeCount) {
        if (role == null) {
            return null;
        }

        return TenantRoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .isDefault(role.isDefault())
                .permissions(role.getPermissions() != null ?
                        role.getPermissions().stream()
                                .map(p -> TenantRoleResponse.PermissionInfo.builder()
                                        .id(p.getId())
                                        .name(p.getPermissionName())
                                        .description(p.getEffectiveDescription())
                                        .category(p.getEffectiveCategory())
                                        .build())
                                .collect(Collectors.toList()) :
                        List.of())
                .employeeCount(employeeCount)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    public TenantRoleResponse toResponse(TenantRole role) {
        if (role == null) {
            return null;
        }
        int count = 0;
        if (role.getTenantId() != null && role.getId() != null) {
            count = (int) employeeRepository.countUsersByRoleIdAndTenantId(role.getId(), role.getTenantId());
        }
        return toResponse(role, count);
    }

    /**
     * Get roles with pagination for better performance
     */
    public org.springframework.data.domain.Page<TenantRole> getAllRolesForTenantPaginated(
            Long tenantId, org.springframework.data.domain.Pageable pageable) {
        return roleRepository.findAllByTenantId(tenantId, pageable);
    }

    /**
     * Get roles by category
     */
    public List<TenantRole> getRolesByCategory(Long tenantId, String category) {
        log.debug("Fetching roles by category: {} for tenant: {}", category, tenantId);
        return roleRepository.findByTenantIdAndCategory(tenantId, category);
    }

    /**
     * Get active roles only
     */
    public List<TenantRole> getActiveRolesForTenant(Long tenantId) {
        log.debug("Fetching active roles for tenant: {}", tenantId);
        return roleRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    public List<TenantRole> getDefaultRolesForTenant(Long tenantId) {
        log.debug("Fetching default roles for tenant: {}", tenantId);
        return roleRepository.findByTenantIdAndIsDefaultTrue(tenantId);
    }

    // =====================================================
    // DELETE METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public void deleteRole(Long roleId, Long tenantId) {
        log.info("Deleting tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isDefault()) {
            throw new BusinessException("Cannot delete the default role. Set another role as default first.");
        }

        long userCount = employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role that is assigned to " + userCount +
                    " user(s). Remove the role from users first.");
        }

        long totalRoles = roleRepository.countByTenantId(tenantId);
        if (totalRoles <= 1) {
            throw new BusinessException("Cannot delete the last role in tenant. At least one role must exist.");
        }

        // Soft delete - deactivate instead of hard delete
        role.setActive(false);
        roleRepository.save(role);

        // Remove from caches
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            roleCache.remove(cacheKey);
            tenantRolesCache.remove(tenantId);
        }

        log.info("Role deactivated successfully: {}", roleId);
    }

    /**
     * Hard delete role (use with caution)
     */
    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public void hardDeleteRole(Long roleId, Long tenantId) {
        log.warn("Hard deleting tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isDefault()) {
            throw new BusinessException("Cannot delete the default role.");
        }

        long userCount = employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId);
        if (userCount > 0) {
            throw new BusinessException("Cannot delete role assigned to users");
        }

        roleRepository.delete(role);

        // Remove from caches
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            roleCache.remove(cacheKey);
            tenantRolesCache.remove(tenantId);
        }

        log.info("Role hard deleted successfully: {}", roleId);
    }

    /**
     * Activate a deactivated role
     */
    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public TenantRole activateRole(Long roleId, Long tenantId) {
        log.info("Activating tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);
        role.setActive(true);

        TenantRole updatedRole = roleRepository.save(role);

        // Update cache
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            roleCache.put(cacheKey, updatedRole);
            tenantRolesCache.remove(tenantId);
        }

        return updatedRole;
    }

    // =====================================================
    // USER-ROLE ASSIGNMENT METHODS
    // =====================================================

    public List<Employee> getUsersByRole(Long roleId, Long tenantId) {
        log.debug("Getting users for role: {} in tenant: {}", roleId, tenantId);

        // Check cache
        if (cacheEnabled) {
            List<Employee> cached = usersByRoleCache.get(roleId);
            if (cached != null) {
                return cached;
            }
        }

        getRoleByIdAndTenant(roleId, tenantId);
        List<Employee> users = employeeRepository.findByRolesIdAndTenantId(roleId, tenantId);

        if (cacheEnabled) {
            usersByRoleCache.put(roleId, users);
        }

        return users;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId"),
        @CacheEvict(value = "tenantUsers", allEntries = true)
    })
    public void assignRoleToUser(Long roleId, Long userId, Long tenantId) {
        log.info("Assigning role {} to user {} in tenant {}", roleId, userId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (!role.isActive()) {
            throw new BusinessException("Cannot assign inactive role: " + role.getName());
        }

        Employee user = employeeRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId + " for tenant: " + tenantId));

        if (user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User already has role: " + role.getName());
        }

        user.getRoles().add(role);

        // Increment roles version to invalidate JWT cache
        user.incrementRolesVersion();
        user.clearAuthoritiesCache();

        employeeRepository.save(user);

        // Invalidate caches
        dynamicRoleService.invalidateEmployeeAuthorityCache(user.getEmail(), tenantId);
        usersByRoleCache.remove(roleId);
        userRoleAssignmentCache.remove(userId);

        log.info("Role '{}' assigned to user '{}' in tenant {}", role.getName(), user.getEmail(), tenantId);
    }

    /**
     * Assign multiple roles to user in batch
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", allEntries = true),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId"),
        @CacheEvict(value = "tenantUsers", allEntries = true)
    })
    public void assignRolesToUser(Set<Long> roleIds, Long userId, Long tenantId) {
        log.info("Assigning {} roles to user {} in tenant {}", roleIds.size(), userId, tenantId);

        Employee user = employeeRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId + " for tenant: " + tenantId));

        Set<TenantRole> rolesToAdd = new HashSet<>();
        Set<Long> assignedRoleIds = new HashSet<>();

        for (Long roleId : roleIds) {
            TenantRole role = getRoleByIdAndTenant(roleId, tenantId);
            if (!role.isActive()) {
                log.warn("Skipping inactive role: {}", role.getName());
                continue;
            }
            if (!user.getRoles().contains(role)) {
                rolesToAdd.add(role);
                assignedRoleIds.add(roleId);
            }
        }

        if (!rolesToAdd.isEmpty()) {
            user.getRoles().addAll(rolesToAdd);
            user.incrementRolesVersion();
            user.clearAuthoritiesCache();
            employeeRepository.save(user);

            // Invalidate caches
            dynamicRoleService.invalidateEmployeeAuthorityCache(user.getEmail(), tenantId);
            for (Long roleId : assignedRoleIds) {
                usersByRoleCache.remove(roleId);
            }
            userRoleAssignmentCache.remove(userId);

            log.info("{} roles assigned to user {} in tenant {}", rolesToAdd.size(), userId, tenantId);
        }
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId"),
        @CacheEvict(value = "tenantUsers", allEntries = true)
    })
    public void removeRoleFromUser(Long roleId, Long userId, Long tenantId) {
        log.info("Removing role {} from user {} in tenant {}", roleId, userId, tenantId);

        Employee user = employeeRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId + " for tenant: " + tenantId));

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (user.getRoles().size() <= 1 && user.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
            throw new BusinessException("User must have at least one role. Cannot remove the last role.");
        }

        boolean removed = user.getRoles().removeIf(r -> r.getId().equals(roleId));

        if (!removed) {
            throw new BusinessException("User does not have role: " + role.getName());
        }

        user.incrementRolesVersion();
        user.clearAuthoritiesCache();
        employeeRepository.save(user);

        // Invalidate caches
        dynamicRoleService.invalidateEmployeeAuthorityCache(user.getEmail(), tenantId);
        usersByRoleCache.remove(roleId);
        userRoleAssignmentCache.remove(userId);

        log.info("Role '{}' removed from user {} in tenant {}", role.getName(), userId, tenantId);
    }

    /**
     * Get user's current roles with caching
     */
    public Set<TenantRole> getUserRoles(Long userId, Long tenantId) {
        // Check cache
        if (cacheEnabled) {
            Set<Long> cachedRoleIds = userRoleAssignmentCache.get(userId);
            if (cachedRoleIds != null) {
                return cachedRoleIds.stream()
                        .map(roleId -> getRoleByIdAndTenant(roleId, tenantId))
                        .collect(Collectors.toSet());
            }
        }

        Employee user = employeeRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId + " for tenant: " + tenantId));

        Set<TenantRole> roles = user.getRoles();

        if (cacheEnabled) {
            Set<Long> roleIds = roles.stream().map(TenantRole::getId).collect(Collectors.toSet());
            userRoleAssignmentCache.put(userId, roleIds);
        }

        return roles;
    }

    /**
     * Check if user has a specific role
     */
    public boolean userHasRole(Long userId, Long tenantId, String roleName) {
        Set<TenantRole> roles = getUserRoles(userId, tenantId);
        return roles.stream().anyMatch(role -> role.getName().equalsIgnoreCase(roleName));
    }

    // =====================================================
    // ADMIN METHODS
    // =====================================================

    @Transactional
    @CacheEvict(value = {"tenantRoles", "tenantRolesList", "tenantRolesLookup"}, allEntries = true)
    public TenantRole setDefaultRole(Long roleId, Long tenantId) {
        log.info("Setting default role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        List<TenantRole> allRoles = roleRepository.findAllByTenantId(tenantId);
        for (TenantRole r : allRoles) {
            boolean isThisRole = r.getId().equals(roleId);
            if (r.isDefault() != isThisRole) {
                if (isThisRole) {
                    r.setAsDefault();
                } else {
                    r.removeDefaultFlag();
                }
                roleRepository.save(r);
            }
        }

        // Invalidate caches
        if (cacheEnabled) {
            tenantRolesCache.remove(tenantId);
        }

        return role;
    }

    // =====================================================
    // STATISTICS & HELPER METHODS
    // =====================================================

    public long getRoleCountForTenant(Long tenantId) {
        return roleRepository.countByTenantId(tenantId);
    }

    public boolean hasUsersAssigned(Long roleId, Long tenantId) {
        return employeeRepository.countUsersByRoleIdAndTenantId(roleId, tenantId) > 0;
    }

    /**
     * Get role statistics for tenant
     */
    public Map<String, Object> getRoleStatistics(Long tenantId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRoles", roleRepository.countByTenantId(tenantId));
        stats.put("activeRoles", roleRepository.countByTenantIdAndActiveTrue(tenantId));
        stats.put("defaultRoles", roleRepository.countByTenantIdAndIsDefaultTrue(tenantId));

        // Roles by category
        List<Object[]> rolesByCategory = roleRepository.countRolesByCategory(tenantId);
        Map<String, Long> categoryMap = new HashMap<>();
        for (Object[] row : rolesByCategory) {
            categoryMap.put((String) row[0], (Long) row[1]);
        }
        stats.put("rolesByCategory", categoryMap);

        // Cache stats
        stats.put("cachedRoles", roleCache.size());
        stats.put("cachedTenantRoles", tenantRolesCache.size());

        return stats;
    }

    /**
     * Clear all caches for a tenant
     */
    public void clearTenantCaches(Long tenantId) {
        // Clear role caches
        roleCache.keySet().removeIf(key -> key.startsWith(tenantId + ":"));
        tenantRolesCache.remove(tenantId);
        usersByRoleCache.clear();
        userRoleAssignmentCache.clear();

        log.info("Cleared all caches for tenant: {}", tenantId);
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        roleCache.clear();
        tenantRolesCache.clear();
        usersByRoleCache.clear();
        userRoleAssignmentCache.clear();
        log.info("Cleared all tenant role caches");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Integer> getCacheStats() {
        return Map.of(
                "roleCacheSize", roleCache.size(),
                "tenantRolesCacheSize", tenantRolesCache.size(),
                "usersByRoleCacheSize", usersByRoleCache.size(),
                "userRoleAssignmentCacheSize", userRoleAssignmentCache.size()
        );
    }
}