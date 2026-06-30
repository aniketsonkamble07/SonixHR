package com.sonixhr.security;

import com.sonixhr.entity.employee.Employee;
import com.sonixhr.entity.tenant.TenantPermission;
import com.sonixhr.entity.tenant.TenantRole;
import com.sonixhr.repository.employee.EmployeeRepository;
import com.sonixhr.repository.tenant.TenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TenantDynamicRoleService {

    private final EmployeeRepository employeeRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.tenant.role.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.tenant.role.cache.ttl-minutes:30}")
    private long cacheTtlMinutes;

    private static final String REDIS_KEY_TENANT_USER_AUTHORITIES = "tenant:user:authorities:";

    /**
     * Load employee authorities dynamically from database (tenant-specific)
     */
    @Cacheable(value = "tenant_user_authorities", key = "#email + ':' + #tenantId")
    public Collection<? extends GrantedAuthority> loadEmployeeAuthorities(String email, Long tenantId) {
        log.debug("Loading dynamic authorities for employee: {} in tenant: {}", email, tenantId);

        long startTime = System.nanoTime();

        try {
            // Load employee with roles and permissions for specific tenant
            Employee employee = employeeRepository.findByEmailAndTenantIdWithRoles(email, tenantId)
                    .orElse(null);

            if (employee == null) {
                log.warn("Employee not found: {} in tenant: {}", email, tenantId);
                return Collections.emptyList();
            }

            Set<String> authorities = new HashSet<>();

            // Process each role assigned to the employee
            for (TenantRole role : employee.getRoles()) {
                if (!role.isActive()) {
                    continue;
                }

                // Add role as authority (with ROLE_ prefix for Spring Security)
                authorities.add("ROLE_" + role.getName());

                // Add all permissions from this role
                for (TenantPermission permission : role.getPermissions()) {
                    if (permission != null && permission.getPermission() != null) {
                        // FIXED: getPermission() returns String directly, no .name() needed
                        // Example: "EMPLOYEE_VIEW_SELF", "LEAVE_REQUEST", etc.
                        authorities.add(permission.getPermission());
                    }
                }
            }

            // Add tenant-specific authority
            authorities.add("TENANT_" + tenantId);

            // Convert to GrantedAuthority
            List<GrantedAuthority> grantedAuthorities = authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.debug("Loaded {} authorities for employee {} in tenant {} in {}ms",
                    grantedAuthorities.size(), email, tenantId, duration);

            return grantedAuthorities;

        } catch (Exception e) {
            log.error("Failed to load authorities for employee: {} in tenant: {}", email, tenantId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Load role permissions with caching - FIXED to return Set<String>
     */
    @Cacheable(value = "tenant_role_permissions", key = "#roleId")
    public Set<String> getRolePermissions(Long roleId) {
        log.debug("Loading permissions for role: {}", roleId);

        Optional<TenantRole> roleOpt = tenantRoleRepository.findByIdWithPermissions(roleId);

        if (roleOpt.isEmpty()) {
            return Collections.emptySet();
        }

        TenantRole role = roleOpt.get();

        // FIXED: getPermission() returns String directly, no .name() needed
        return role.getPermissions().stream()
                .filter(p -> p.getPermission() != null)
                .map(TenantPermission::getPermission)  // Returns String directly
                .collect(Collectors.toSet());
    }

    /**
     * Check if employee has specific permission
     */
    public boolean hasPermission(String email, Long tenantId, String permission) {
        Collection<? extends GrantedAuthority> authorities = loadEmployeeAuthorities(email, tenantId);
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(permission) ||
                        auth.getAuthority().equals("ROLE_" + permission));
    }

    /**
     * Check if employee has specific role
     */
    public boolean hasRole(String email, Long tenantId, String roleName) {
        Collection<? extends GrantedAuthority> authorities = loadEmployeeAuthorities(email, tenantId);
        String roleWithPrefix = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(roleWithPrefix));
    }

    /**
     * Get all role names for employee
     */
    public List<String> getEmployeeRoleNames(String email, Long tenantId) {
        Employee employee = employeeRepository.findByEmailAndTenantIdWithRoles(email, tenantId)
                .orElse(null);

        if (employee == null) {
            return Collections.emptyList();
        }

        return employee.getRoles().stream()
                .filter(TenantRole::isActive)
                .map(TenantRole::getName)
                .collect(Collectors.toList());
    }

    /**
     * Invalidate employee authority cache when roles change
     */
    @CacheEvict(value = "tenant_user_authorities", key = "#email + ':' + #tenantId")
    public void invalidateEmployeeAuthorityCache(String email, Long tenantId) {
        log.info("Invalidated authority cache for employee: {} in tenant: {}", email, tenantId);

        // Also invalidate Redis cache
        if (cacheEnabled && redisTemplate != null) {
            try {
                String springKey = "sonixhr:tenant_user_authorities::" + email + ":" + tenantId;
                String customKey = REDIS_KEY_TENANT_USER_AUTHORITIES + email + ":" + tenantId;
                redisTemplate.delete(springKey);
                redisTemplate.delete(customKey);
            } catch (Exception e) {
                log.warn("Failed to delete employee authority cache from Redis: {}", e.getMessage());
            }
        }
    }

    /**
     * Invalidate all caches for a tenant (when tenant-wide role changes)
     */
    public void invalidateTenantCache(Long tenantId) {
        if (cacheEnabled && redisTemplate != null) {
            try {
                // Invalidate Spring cache namespace keys
                String springPattern = "sonixhr:tenant_user_authorities::*:" + tenantId;
                // Invalidate manual keys
                String customPattern = REDIS_KEY_TENANT_USER_AUTHORITIES + "*:" + tenantId;

                scanAndDelete(springPattern);
                scanAndDelete(customPattern);
            } catch (Exception e) {
                log.warn("Failed to delete tenant authority caches from Redis: {}", e.getMessage());
            }
        }
    }

    private void scanAndDelete(String pattern) {
        try {
            Set<String> keys = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                Set<String> keySet = new java.util.HashSet<>();
                org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.keyCommands().scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(1000).build()
                );
                while (cursor.hasNext()) {
                    keySet.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
                return keySet;
            });
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated {} caches matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.warn("Failed to scan and delete pattern {}: {}", pattern, e.getMessage());
        }
    }

    /**
     * Refresh employee roles and increment version
     */
    public void refreshEmployeeRoles(Long employeeId, Long tenantId) {
        Employee employee = employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .orElse(null);

        if (employee != null) {
            employee.incrementRolesVersion();
            employee.clearAuthoritiesCache();
            employeeRepository.save(employee);
            invalidateEmployeeAuthorityCache(employee.getEmail(), tenantId);
            log.info("Refreshed roles for employee: {} in tenant: {}", employee.getEmail(), tenantId);
        }
    }

    /**
     * Get employee authority summary
     */
    public Map<String, Object> getEmployeeAuthoritySummary(String email, Long tenantId) {
        Map<String, Object> summary = new HashMap<>();

        Collection<? extends GrantedAuthority> authorities = loadEmployeeAuthorities(email, tenantId);
        List<String> roleNames = getEmployeeRoleNames(email, tenantId);

        summary.put("email", email);
        summary.put("tenantId", tenantId);
        summary.put("roles", roleNames);
        summary.put("permissions", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(p -> !p.startsWith("ROLE_") && !p.startsWith("TENANT_"))
                .collect(Collectors.toList()));
        summary.put("totalAuthorities", authorities.size());

        return summary;
    }

    /**
     * Preload authorities for frequently accessed employees
     */
    public void preloadAuthorities(List<String> emails, Long tenantId) {
        for (String email : emails) {
            try {
                loadEmployeeAuthorities(email, tenantId);
                log.debug("Preloaded authorities for employee: {} in tenant: {}", email, tenantId);
            } catch (Exception e) {
                log.warn("Failed to preload authorities for employee: {}", email, e.getMessage());
            }
        }
    }
}