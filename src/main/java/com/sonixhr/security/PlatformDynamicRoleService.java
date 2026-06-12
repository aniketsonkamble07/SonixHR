package com.sonixhr.security;

import com.sonixhr.common.base.BasePermission;
import com.sonixhr.entity.platform.PlatformPermission;
import com.sonixhr.entity.platform.PlatformRole;
import com.sonixhr.entity.platform.PlatformUser;
import com.sonixhr.repository.platform.PlatformUserRepository;
import com.sonixhr.repository.platform.PlatformRoleRepository;
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
public class PlatformDynamicRoleService {

    private final PlatformUserRepository platformUserRepository;
    private final PlatformRoleRepository platformRoleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.platform.role.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.platform.role.cache.ttl-minutes:30}")
    private long cacheTtlMinutes;

    private static final String REDIS_KEY_PLATFORM_USER_AUTHORITIES = "platform:user:authorities:";
    private static final String REDIS_KEY_PLATFORM_ROLE_PERMISSIONS = "platform:role:permissions:";

    /**
     * Load platform user authorities dynamically from database
     */
    @Cacheable(value = "platform_user_authorities", key = "#email")
    public Collection<? extends GrantedAuthority> loadUserAuthorities(String email) {
        log.debug("Loading dynamic authorities for platform user: {}", email);

        long startTime = System.nanoTime();

        try {
            PlatformUser user = platformUserRepository.findByEmailWithRoles(email)
                    .orElse(null);

            if (user == null) {
                log.warn("Platform user not found: {}", email);
                return Collections.emptyList();
            }

            Set<String> authorities = new HashSet<>();

            for (PlatformRole role : user.getRoles()) {
                if (!role.isActive()) {
                    continue;
                }

                // Add role as authority
                authorities.add("ROLE_" + role.getName());

                for (PlatformPermission permission : role.getPermissions()) {
                    if (permission != null && permission.getPermission() != null) {
                        authorities.add(permission.getPermission());
                    }
                }
            }

            // Convert to GrantedAuthority
            List<GrantedAuthority> grantedAuthorities = authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.debug("Loaded {} authorities for platform user {} in {}ms",
                    grantedAuthorities.size(), email, duration);

            return grantedAuthorities;

        } catch (Exception e) {
            log.error("Failed to load authorities for platform user: {}", email, e);
            return Collections.emptyList();
        }
    }
    /**
     * Load role permissions with caching
     */
    @Cacheable(value = "platform_role_permissions", key = "#roleId")
    public Set<String> getRolePermissions(Long roleId) {
        log.debug("Loading permissions for platform role: {}", roleId);

        Optional<PlatformRole> roleOpt = platformRoleRepository.findByIdWithPermissions(roleId);

        if (roleOpt.isEmpty()) {
            return Collections.emptySet();
        }

        PlatformRole role = roleOpt.get();

        Set<PlatformPermission> permissions = role.getPermissions();
        Set<String> result = new HashSet<>();

        for (PlatformPermission permission : permissions) {
            if (permission != null && permission.getPermission() != null) {
                result.add(permission.getPermission());
            }
        }

        return result;
    }

    /**
     * Check if platform user has specific permission
     */
    public boolean hasPermission(String email, String permission) {
        Collection<? extends GrantedAuthority> authorities = loadUserAuthorities(email);
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(permission) ||
                        auth.getAuthority().equals("ROLE_" + permission));
    }

    /**
     * Check if platform user has specific role
     */
    public boolean hasRole(String email, String roleName) {
        Collection<? extends GrantedAuthority> authorities = loadUserAuthorities(email);
        String roleWithPrefix = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(roleWithPrefix));
    }

    /**
     * Get all role names for platform user
     */
    public List<String> getUserRoleNames(String email) {
        PlatformUser user = platformUserRepository.findByEmailWithRoles(email)
                .orElse(null);

        if (user == null) {
            return Collections.emptyList();
        }

        return user.getRoles().stream()
                .filter(PlatformRole::isActive)
                .map(PlatformRole::getName)
                .collect(Collectors.toList());
    }

    /**
     * Invalidate user authority cache when roles change
     */
    @CacheEvict(value = "platform_user_authorities", key = "#email")
    public void invalidateUserAuthorityCache(String email) {
        log.info("Invalidated authority cache for platform user: {}", email);

        if (cacheEnabled && redisTemplate != null) {
            String cacheKey = REDIS_KEY_PLATFORM_USER_AUTHORITIES + email;
            redisTemplate.delete(cacheKey);
        }
    }

    /**
     * Refresh platform user roles and increment version
     */
    public void refreshUserRoles(Long userId) {
        PlatformUser user = platformUserRepository.findById(userId)
                .orElse(null);

        if (user != null) {
            user.incrementRolesVersion();
            user.clearAuthoritiesCache();
            platformUserRepository.save(user);
            invalidateUserAuthorityCache(user.getEmail());
            log.info("Refreshed roles for platform user: {}", user.getEmail());
        }
    }

    /**
     * Get user authority summary
     */
    public Map<String, Object> getUserAuthoritySummary(String email) {
        Map<String, Object> summary = new HashMap<>();

        Collection<? extends GrantedAuthority> authorities = loadUserAuthorities(email);
        List<String> roleNames = getUserRoleNames(email);

        summary.put("email", email);
        summary.put("roles", roleNames);
        summary.put("permissions", authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(p -> !p.startsWith("ROLE_"))
                .collect(Collectors.toList()));
        summary.put("totalAuthorities", authorities.size());

        return summary;
    }

    /**
     * Preload authorities for frequently accessed platform users
     */
    public void preloadAuthorities(List<String> emails) {
        for (String email : emails) {
            try {
                loadUserAuthorities(email);
                log.debug("Preloaded authorities for platform user: {}", email);
            } catch (Exception e) {
                log.warn("Failed to preload authorities for user: {}", email, e.getMessage());
            }
        }
    }
}