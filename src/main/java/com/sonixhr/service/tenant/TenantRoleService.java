package com.sonixhr.service.tenant;

import com.sonixhr.dto.tenant.TenantRoleCreateRequest;
import com.sonixhr.dto.tenant.TenantRoleUpdateRequest;
import com.sonixhr.dto.tenant.TenantRoleResponse;
import com.sonixhr.dto.tenant.TenantRoleSummaryResponse;
import com.sonixhr.dto.tenant.TenantRoleLookupResponse;
import com.sonixhr.dto.tenant.TenantRoleDeletePreviewResponse;
import com.sonixhr.dto.tenant.TenantRoleDeleteResponse;
import com.sonixhr.dto.employee.EmployeeSummaryResponse;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.exceptions.ValidationException;
import com.sonixhr.repository.tenant.TenantPermissionRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.security.TenantDynamicRoleService;
import com.sonixhr.service.employee.EmployeeService;
import lombok.RequiredArgsConstructor; // Force re-index in IDE
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
    private final EmployeeService employeeService;
    private final com.sonixhr.service.common.AuditLogService auditLogService;
    private final com.sonixhr.service.platform.FeatureAccessService featureAccessService;

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
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", allEntries = true),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public TenantRoleResponse createRole(TenantRoleCreateRequest request, Long tenantId, Long createdBy) {
        long startTime = System.nanoTime();
        log.info("Creating tenant role: {} for tenant: {}", request.getName(), tenantId);

        if (!featureAccessService.hasFeature(tenantId, "CUSTOM_ROLES")) {
            throw new BusinessException("Custom role creation is not enabled for your subscription plan. Please upgrade your plan.");
        }

        if (roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new ValidationException("name", "Role name already exists for this tenant");
        }

        Set<TenantPermission> permissions = fetchPermissions(request.getPermissionIds());
        boolean isDefault = roleRepository.countByTenantId(tenantId) == 0;

        TenantRole role = TenantRole.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
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

        return toResponse(savedRole);
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

    private String buildCacheKey(Long tenantId, Long roleId) {
        return roleId + ":" + tenantId;
    }

    // =====================================================
    // UPDATE METHODS
    // =====================================================

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public TenantRoleResponse updateRole(Long roleId, TenantRoleUpdateRequest request, Long tenantId) {
        log.info("Updating tenant role: {} for tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (!role.getName().equals(request.getName()) &&
                roleRepository.existsByTenantIdAndName(tenantId, request.getName())) {
            throw new ValidationException("name", "Role name already exists for this tenant");
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


        // Update permissions if provided
        if (request.getPermissionIds() != null) {
            Set<TenantPermission> permissions = fetchPermissions(request.getPermissionIds());
            role.setPermissions(permissions);
            changed = true;
            log.info("Updated permissions for role: {}", roleId);
        }

        if (changed) {
            TenantRole updatedRole = roleRepository.save(role);

            // Invalidate caches
            invalidateRoleCaches(tenantId, roleId);

            // Invalidate affected users
            invalidateUsersWithRole(roleId, tenantId);

            log.info("Tenant role updated: {}", roleId);
            return toResponse(updatedRole);
        }

        return toResponse(role);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public TenantRoleResponse updateRolePermissions(Long roleId, Set<Long> permissionIds, Long tenantId) {
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

        return toResponse(updatedRole);
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

        TenantRole role = roleRepository.findByIdAndTenantIdWithPermissions(roleId, tenantId)
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
        if (!role.isActive()) {
            throw new ResourceNotFoundException("Role not found with id: " + roleId + " for tenant: " + tenantId);
        }
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

        List<TenantRole> roles = roleRepository.findAllByTenantIdAndActiveTrueWithPermissions(tenantId);

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

        List<TenantRole> roles = roleRepository.findAllByTenantIdAndActiveTrueWithPermissions(tenantId);

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
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public TenantRoleDeleteResponse deleteRole(Long roleId, Long tenantId, boolean confirm) {
        log.info("Processing delete request for tenant role: {} in tenant: {} with confirm={}", roleId, tenantId, confirm);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        if (role.isDefault()) {
            throw new BusinessException("Cannot delete the default role. Set another role as default first.");
        }

        long totalRoles = roleRepository.countByTenantId(tenantId);
        if (totalRoles <= 1) {
            throw new BusinessException("Cannot delete the last role in tenant. At least one role must exist.");
        }

        List<Employee> affectedEmployees = employeeRepository.findByRolesIdAndTenantId(roleId, tenantId);
        int employeeCount = affectedEmployees.size();

        if (employeeCount > 1) {
            throw new BusinessException("Cannot delete role. It is assigned to " + employeeCount + " employees. Please update it first.");
        }

        if (employeeCount == 1) {
            Employee employee = affectedEmployees.get(0);
            String employeeName = employee.getFirstName() + " " + employee.getLastName();

            if (!confirm) {
                return TenantRoleDeleteResponse.builder()
                        .deleted(false)
                        .requiresConfirmation(true)
                        .employeeName(employeeName)
                        .message("Role is assigned to " + employeeName + ". Please confirm deletion.")
                        .build();
            }

            // Remove role from employee
            employee.getRoles().remove(role);
            employee.incrementRolesVersion();
            employee.clearAuthoritiesCache();
            employeeRepository.save(employee);

            // Invalidate employee-related authority/role caches
            dynamicRoleService.invalidateEmployeeAuthorityCache(employee.getEmail(), tenantId);
            userRoleAssignmentCache.remove(employee.getId());
            usersByRoleCache.remove(roleId);
        }

        // Hard delete the role
        roleRepository.delete(role);

        // Remove from local caches
        if (cacheEnabled) {
            String cacheKey = buildCacheKey(tenantId, roleId);
            roleCache.remove(cacheKey);
            tenantRolesCache.remove(tenantId);
        }

        log.info("Role hard deleted successfully: {}", roleId);

        return TenantRoleDeleteResponse.builder()
                .deleted(true)
                .requiresConfirmation(false)
                .message("Role deleted successfully" + (employeeCount == 1 ? " and removed from employee " + affectedEmployees.get(0).getFirstName() + " " + affectedEmployees.get(0).getLastName() : ""))
                .build();
    }

    /**
     * Hard delete role (use with caution)
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
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
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
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

        auditLogService.log(
            user.getTenant(),
            "ROLE_ASSIGNED",
            "roles",
            null,
            role.getName(),
            "{\"userId\":" + userId + "}"
        );

        // Invalidate caches
        dynamicRoleService.invalidateEmployeeAuthorityCache(user.getEmail(), tenantId);
        usersByRoleCache.remove(roleId);
        userRoleAssignmentCache.remove(userId);
        if (cacheEnabled) {
            roleCache.remove(buildCacheKey(tenantId, roleId));
        }

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
                if (cacheEnabled) {
                    roleCache.remove(buildCacheKey(tenantId, roleId));
                }
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

        auditLogService.log(
            user.getTenant(),
            "ROLE_REMOVED",
            "roles",
            role.getName(),
            null,
            "{\"userId\":" + userId + "}"
        );

        // Invalidate caches
        dynamicRoleService.invalidateEmployeeAuthorityCache(user.getEmail(), tenantId);
        usersByRoleCache.remove(roleId);
        userRoleAssignmentCache.remove(userId);
        if (cacheEnabled) {
            roleCache.remove(buildCacheKey(tenantId, roleId));
        }

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
    @Caching(evict = {
        @CacheEvict(value = "tenantRoles", key = "#roleId + ':' + #tenantId"),
        @CacheEvict(value = "tenantRolesList", key = "#tenantId"),
        @CacheEvict(value = "tenantRolesLookup", key = "#tenantId")
    })
    public TenantRoleResponse setDefaultRole(Long roleId, Long tenantId) {
        log.info("Setting default role: {} for tenant: {}", roleId, tenantId);

        TenantRole defaultRole = null;
        List<TenantRole> allRoles = roleRepository.findAllByTenantIdWithPermissions(tenantId);
        for (TenantRole r : allRoles) {
            boolean isThisRole = r.getId().equals(roleId);
            if (isThisRole) {
                defaultRole = r;
            }
            if (r.isDefault() != isThisRole) {
                if (isThisRole) {
                    r.setAsDefault();
                } else {
                    r.removeDefaultFlag();
                }
                roleRepository.save(r);
                if (cacheEnabled) {
                    roleCache.remove(buildCacheKey(tenantId, r.getId()));
                }
            }
        }

        if (defaultRole == null) {
            throw new ResourceNotFoundException("Role not found with id: " + roleId + " for tenant: " + tenantId);
        }

        // Invalidate caches
        if (cacheEnabled) {
            tenantRolesCache.remove(tenantId);
        }

        return toResponse(defaultRole);
    }

    public TenantRoleDeletePreviewResponse getRoleDeletePreview(Long roleId, Long tenantId) {
        log.info("Generating role delete preview for role: {} in tenant: {}", roleId, tenantId);

        TenantRole role = getRoleByIdAndTenant(roleId, tenantId);

        List<Employee> affectedEmployees = employeeRepository.findByRolesIdAndTenantId(roleId, tenantId);

        // Find alternative active roles that are not this role
        List<TenantRole> otherRoles = roleRepository.findAllByTenantIdAndActiveTrueWithPermissions(tenantId);
        List<TenantRoleLookupResponse> reassignmentOptions = otherRoles.stream()
                .filter(r -> !r.getId().equals(roleId))
                .map(this::toLookupResponse)
                .collect(Collectors.toList());

        List<EmployeeSummaryResponse> employeeSummaries = affectedEmployees.stream()
                .map(employeeService::convertToSummaryResponse)
                .collect(Collectors.toList());

        boolean deletable = true;
        String validationMessage = null;

        if (!role.isActive()) {
            deletable = false;
            validationMessage = "Role is already inactive.";
        } else if (role.isDefault()) {
            deletable = false;
            validationMessage = "Cannot delete the default role. Set another role as default first.";
        } else if (reassignmentOptions.isEmpty()) {
            deletable = false;
            validationMessage = "Cannot delete the last active role. At least one active role must exist in the tenant.";
        }

        return TenantRoleDeletePreviewResponse.builder()
                .roleId(roleId)
                .roleName(role.getName())
                .affectedEmployeeCount(affectedEmployees.size())
                .affectedEmployees(employeeSummaries)
                .reassignmentOptions(reassignmentOptions)
                .deletable(deletable)
                .validationMessage(validationMessage)
                .build();
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