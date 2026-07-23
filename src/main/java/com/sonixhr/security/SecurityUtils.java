package com.sonixhr.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@SuppressWarnings({ "unchecked", "null" })
public class SecurityUtils {

    private final RedisTemplate<String, Object> redisTemplate;
    private boolean redisAvailable = false;

    @Value("${app.security.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.security.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    @Value("${app.security.cache.max-size:5000}")
    private int maxCacheSize;

    // Role constants - configurable via application properties
    @Value("${app.roles.super-admin:SUPER_ADMIN}")
    private String ROLE_SUPER_ADMIN;

    @Value("${app.roles.admin:ADMIN}")
    private String ROLE_ADMIN;

    @Value("${app.roles.hr:HR}")
    private String ROLE_HR;

    @Value("${app.roles.manager:MANAGER}")
    private String ROLE_MANAGER;

    @Value("${app.roles.employee:EMPLOYEE}")
    private String ROLE_EMPLOYEE;

    @Value("${app.roles.user:USER}")
    private String ROLE_USER;

    // User type constants
    @Value("${app.user-types.platform:PLATFORM}")
    private String USER_TYPE_PLATFORM;

    @Value("${app.user-types.employee:EMPLOYEE}")
    private String USER_TYPE_EMPLOYEE;

    @Value("${app.user-types.tenant:TENANT}")
    private String USER_TYPE_TENANT;

    // Caches using proper Caffeine Cache interface
    private Cache<String, CachedSecurityContext> localCache;
    private Cache<String, Boolean> roleCheckCache;
    private Cache<String, Boolean> permissionCheckCache;

    // Redis cache keys
    private static final String REDIS_KEY_USER_CONTEXT = "security:user:context:";
    private static final String REDIS_KEY_USER_ROLES = "security:user:roles:";
    private static final String REDIS_KEY_PERMISSION = "security:permission:";

    // ThreadLocal for request-scoped caching
    private static final ThreadLocal<Map<String, Object>> requestCache = ThreadLocal.withInitial(HashMap::new);

    @Autowired
    public SecurityUtils(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        // Initialize caches with Caffeine
        localCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxCacheSize)
                .recordStats()
                .build();

        roleCheckCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxCacheSize * 2)
                .recordStats()
                .build();

        permissionCheckCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(maxCacheSize * 2)
                .recordStats()
                .build();

        // Check Redis availability
        if (cacheEnabled && redisTemplate != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                redisAvailable = true;
                log.info("Redis connection established for SecurityUtils");
            } catch (Exception e) {
                log.warn("Redis is not available for SecurityUtils cache: {}", e.getMessage());
                redisAvailable = false;
            }
        }

        log.info("SecurityUtils initialized with cache enabled: {}, TTL: {} minutes, Max Cache Size: {}",
                cacheEnabled, cacheTtlMinutes, maxCacheSize);
    }

    @PreDestroy
    public void cleanup() {
        log.info("SecurityUtils cleanup - clearing caches");
        clearAllCaches();
    }

    // =====================================================
    // SECURITY CONTEXT CACHE CLASS
    // =====================================================

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class CachedSecurityContext {
        public String email;
        public List<String> roles;
        public String userType;
        public Long tenantId;
        public Long employeeId;
        public String employeeCode;
        public long cachedAt;
        public long expiresAt;

        public CachedSecurityContext() {
        }

        public CachedSecurityContext(Authentication auth, Map<String, Object> claims, long ttlMinutes) {
            this.email = auth != null ? auth.getName() : null;
            this.roles = extractRolesFromClaims(claims);
            this.userType = (String) claims.get("userType");
            this.tenantId = extractTenantId(claims);
            this.employeeId = extractEmployeeId(claims);
            this.employeeCode = (String) claims.get("employeeCode");
            this.cachedAt = System.currentTimeMillis();
            this.expiresAt = this.cachedAt + TimeUnit.MINUTES.toMillis(ttlMinutes);
        }

        public boolean isValid() {
            return System.currentTimeMillis() < expiresAt;
        }

        private List<String> extractRolesFromClaims(Map<String, Object> claims) {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List) {
                return (List<String>) rolesObj;
            }
            return Collections.emptyList();
        }

        private Long extractTenantId(Map<String, Object> claims) {
            Object tenantIdObj = claims.get("tenantId");
            if (tenantIdObj instanceof Long) {
                return (Long) tenantIdObj;
            }
            if (tenantIdObj instanceof Integer) {
                return ((Integer) tenantIdObj).longValue();
            }
            if (tenantIdObj instanceof String) {
                try {
                    return Long.parseLong((String) tenantIdObj);
                } catch (NumberFormatException e) {
                    log.debug("Invalid tenant ID format: {}", tenantIdObj);
                }
            }
            return null;
        }

        private Long extractEmployeeId(Map<String, Object> claims) {
            Object employeeIdObj = claims.get("employeeId");
            if (employeeIdObj instanceof Long) {
                return (Long) employeeIdObj;
            }
            if (employeeIdObj instanceof Integer) {
                return ((Integer) employeeIdObj).longValue();
            }
            if (employeeIdObj instanceof String) {
                try {
                    return Long.parseLong((String) employeeIdObj);
                } catch (NumberFormatException e) {
                    log.debug("Invalid employee ID format: {}", employeeIdObj);
                }
            }
            return null;
        }
    }

    // =====================================================
    // PUBLIC METHODS
    // =====================================================

    /**
     * Get current authentication
     */
    public Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication auth = getCurrentAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    /**
     * Get current user email
     */
    public String getCurrentUserEmail() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("email")) {
            return (String) reqCache.get("email");
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            reqCache.put("email", "anonymousUser");
            return "anonymousUser";
        }

        String email = auth.getName();
        if (email == null || "anonymousUser".equalsIgnoreCase(email)) {
            email = "anonymousUser";
        }
        reqCache.put("email", email);
        return email;
    }

    /**
     * Get current user roles
     */
    public List<String> getCurrentUserRoles() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("roles")) {
            return (List<String>) reqCache.get("roles");
        }

        // Check cache first
        CachedSecurityContext context = getCachedContext();
        if (context != null && context.roles != null && !context.roles.isEmpty()) {
            reqCache.put("roles", context.roles);
            return context.roles;
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptyList();
        }

        // Extract from authorities
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        reqCache.put("roles", roles);
        return roles;
    }

    /**
     * Get current user type
     */
    public String getCurrentUserType() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("userType")) {
            return (String) reqCache.get("userType");
        }

        CachedSecurityContext context = getCachedContext();
        if (context != null && context.userType != null) {
            reqCache.put("userType", context.userType);
            return context.userType;
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object userType = detailsMap.get("userType");
            if (userType instanceof String) {
                reqCache.put("userType", (String) userType);
                return (String) userType;
            }
        }

        return null;
    }

    /**
     * Get current tenant ID
     */
    public Long getCurrentTenantId() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("tenantId")) {
            return (Long) reqCache.get("tenantId");
        }

        CachedSecurityContext context = getCachedContext();
        if (context != null && context.tenantId != null) {
            reqCache.put("tenantId", context.tenantId);
            return context.tenantId;
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object tenantId = detailsMap.get("tenantId");
            if (tenantId != null) {
                Long tid = convertToLong(tenantId);
                if (tid != null) {
                    reqCache.put("tenantId", tid);
                    return tid;
                }
            }
        }
        return null;
    }

    /**
     * Get current employee ID
     */
    public Long getCurrentEmployeeId() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("employeeId")) {
            return (Long) reqCache.get("employeeId");
        }

        CachedSecurityContext context = getCachedContext();
        if (context != null && context.employeeId != null) {
            reqCache.put("employeeId", context.employeeId);
            return context.employeeId;
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object employeeId = detailsMap.get("employeeId");
            if (employeeId != null) {
                Long eid = convertToLong(employeeId);
                if (eid != null) {
                    reqCache.put("employeeId", eid);
                    return eid;
                }
            }
        }
        return null;
    }

    /**
     * Get current employee code
     */
    public String getCurrentEmployeeCode() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("employeeCode")) {
            return (String) reqCache.get("employeeCode");
        }

        CachedSecurityContext context = getCachedContext();
        if (context != null && context.employeeCode != null) {
            reqCache.put("employeeCode", context.employeeCode);
            return context.employeeCode;
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object employeeCode = detailsMap.get("employeeCode");
            if (employeeCode instanceof String) {
                reqCache.put("employeeCode", (String) employeeCode);
                return (String) employeeCode;
            }
        }
        return null;
    }

    /**
     * Get current user's full name
     */
    public String getCurrentUserFullName() {
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("fullName")) {
            return (String) reqCache.get("fullName");
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object fullName = detailsMap.get("fullName");
            if (fullName instanceof String) {
                reqCache.put("fullName", (String) fullName);
                return (String) fullName;
            }
        }
        return null;
    }

    /**
     * Get current user's principal object
     */
    public Object getCurrentPrincipal() {
        Authentication auth = getCurrentAuthentication();
        return auth != null ? auth.getPrincipal() : null;
    }

    /**
     * Get current user's authorities as string list
     */
    public List<String> getCurrentAuthorities() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Collections.emptyList();
        }

        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    // =====================================================
    // ROLE CHECK METHODS
    // =====================================================

    /**
     * Check if user has a specific role
     */
    public boolean hasRole(String role) {
        if (role == null || role.isEmpty()) {
            return false;
        }

        Map<String, Object> reqCache = requestCache.get();
        String cacheKey = "hasRole_" + role;
        if (reqCache.containsKey(cacheKey)) {
            return (Boolean) reqCache.get(cacheKey);
        }

        String email = getCurrentUserEmail();
        String localCacheKey = "role_" + email + "_" + role;

        if (cacheEnabled) {
            Boolean cached = roleCheckCache.getIfPresent(localCacheKey);
            if (cached != null) {
                reqCache.put(cacheKey, cached);
                return cached;
            }
        }

        List<String> roles = getCurrentUserRoles();

        // Check both with and without ROLE_ prefix
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        String roleWithoutPrefix = role.startsWith("ROLE_") ? role.substring(5) : role;

        boolean hasRole = roles.contains(role) ||
                roles.contains(roleWithPrefix) ||
                roles.contains(roleWithoutPrefix);

        if (cacheEnabled) {
            roleCheckCache.put(localCacheKey, hasRole);
        }
        reqCache.put(cacheKey, hasRole);

        return hasRole;
    }

    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }

        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user has all of the specified roles
     */
    public boolean hasAllRoles(String... roles) {
        if (roles == null || roles.length == 0) {
            return true;
        }

        for (String role : roles) {
            if (!hasRole(role)) {
                return false;
            }
        }
        return true;
    }

    // =====================================================
    // ROLE CHECK SHORTCUTS
    // =====================================================

    public boolean isSuperAdmin() {
        return hasRole(ROLE_SUPER_ADMIN);
    }

    public boolean isAdmin() {
        return hasRole(ROLE_ADMIN);
    }

    public boolean isHR() {
        return hasRole(ROLE_HR);
    }

    public boolean isManager() {
        return hasRole(ROLE_MANAGER);
    }

    public boolean isEmployee() {
        return hasRole(ROLE_EMPLOYEE);
    }

    public boolean isUser() {
        return hasRole(ROLE_USER);
    }

    public boolean hasAnyAdminRole() {
        return hasAnyRole(ROLE_SUPER_ADMIN, ROLE_ADMIN);
    }

    public boolean hasAnyHRRole() {
        return hasAnyRole(ROLE_HR, ROLE_ADMIN, ROLE_SUPER_ADMIN);
    }

    public boolean hasAnyManagementRole() {
        return hasAnyRole(ROLE_MANAGER, ROLE_HR, ROLE_ADMIN, ROLE_SUPER_ADMIN);
    }

    // =====================================================
    // USER TYPE CHECK METHODS
    // =====================================================

    public boolean isPlatformUser() {
        return USER_TYPE_PLATFORM.equals(getCurrentUserType());
    }

    public boolean isEmployeeUser() {
        return USER_TYPE_EMPLOYEE.equals(getCurrentUserType());
    }

    public boolean isTenantUser() {
        return USER_TYPE_TENANT.equals(getCurrentUserType());
    }

    // =====================================================
    // PERMISSION CHECK
    // =====================================================

    /**
     * Check if user has a specific permission
     */
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        Map<String, Object> reqCache = requestCache.get();
        String cacheKey = "hasPermission_" + permission;
        if (reqCache.containsKey(cacheKey)) {
            return (Boolean) reqCache.get(cacheKey);
        }

        String email = getCurrentUserEmail();
        String localCacheKey = "perm_" + email + "_" + permission;

        if (cacheEnabled) {
            Boolean cached = permissionCheckCache.getIfPresent(localCacheKey);
            if (cached != null) {
                reqCache.put(cacheKey, cached);
                return cached;
            }
        }

        // Check Redis cache
        if (redisAvailable && email != null && !"anonymousUser".equals(email)) {
            try {
                String redisKey = REDIS_KEY_PERMISSION + email + ":" + permission;
                Boolean cached = (Boolean) redisTemplate.opsForValue().get(redisKey);
                if (cached != null) {
                    permissionCheckCache.put(localCacheKey, cached);
                    reqCache.put(cacheKey, cached);
                    return cached;
                }
            } catch (Exception e) {
                log.debug("Redis unavailable for permission check: {}", e.getMessage());
            }
        }

        // Check roles for permission
        boolean hasPermission = hasRole(permission);

        // Cache the result
        if (cacheEnabled && email != null && !"anonymousUser".equals(email)) {
            permissionCheckCache.put(localCacheKey, hasPermission);
            if (redisAvailable) {
                try {
                    String redisKey = REDIS_KEY_PERMISSION + email + ":" + permission;
                    redisTemplate.opsForValue().set(redisKey, hasPermission, 1, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.debug("Redis unavailable for permission cache write: {}", e.getMessage());
                }
            }
        }

        reqCache.put(cacheKey, hasPermission);
        return hasPermission;
    }

    // =====================================================
    // CACHE MANAGEMENT
    // =====================================================

    /**
     * Invalidate cache for current user
     */
    public void invalidateCurrentUserCache() {
        String email = getCurrentUserEmail();
        if (email != null && !"anonymousUser".equals(email)) {
            invalidateUserCache(email);
        }
    }

    /**
     * Invalidate cache for specific user
     */
    public void invalidateUserCache(String email) {
        if (email == null || "anonymousUser".equals(email)) {
            return;
        }

        String lowerEmail = email.toLowerCase();
        log.debug("Invalidating cache for user: {}", email);

        // Clear local caches
        localCache.invalidateAll();
        roleCheckCache.invalidateAll();
        permissionCheckCache.invalidateAll();

        // Clear Redis cache
        if (redisAvailable) {
            try {
                String contextKey = REDIS_KEY_USER_CONTEXT + lowerEmail;
                String rolesKey = REDIS_KEY_USER_ROLES + lowerEmail;

                List<String> keysToDelete = new ArrayList<>();
                keysToDelete.add(contextKey);
                keysToDelete.add(rolesKey);

                // Delete permission keys with prefix
                Set<String> permissionKeys = redisTemplate.keys(REDIS_KEY_PERMISSION + lowerEmail + ":*");
                if (permissionKeys != null && !permissionKeys.isEmpty()) {
                    keysToDelete.addAll(permissionKeys);
                }

                if (!keysToDelete.isEmpty()) {
                    redisTemplate.delete(keysToDelete);
                }
                log.debug("Invalidated Redis cache for user: {}", email);
            } catch (Exception e) {
                log.warn("Failed to invalidate Redis cache for user {}: {}", email, e.getMessage());
            }
        }
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        localCache.invalidateAll();
        roleCheckCache.invalidateAll();
        permissionCheckCache.invalidateAll();
        requestCache.remove();

        if (redisAvailable) {
            try {
                Set<String> keys = redisTemplate.keys(REDIS_KEY_USER_CONTEXT + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
                keys = redisTemplate.keys(REDIS_KEY_USER_ROLES + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
                keys = redisTemplate.keys(REDIS_KEY_PERMISSION + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
                log.info("Cleared all Redis security caches");
            } catch (Exception e) {
                log.warn("Failed to clear Redis caches: {}", e.getMessage());
            }
        }
        log.info("Cleared all security caches");
    }

    /**
     * Clear request cache (call at end of each request)
     */
    public static void clearRequestCache() {
        requestCache.remove();
    }

    // =====================================================
    // CONFIGURATION GETTERS
    // =====================================================

    /**
     * Get role configuration
     */
    public Map<String, String> getRoleConfiguration() {
        Map<String, String> config = new HashMap<>();
        config.put("super_admin", ROLE_SUPER_ADMIN);
        config.put("admin", ROLE_ADMIN);
        config.put("hr", ROLE_HR);
        config.put("manager", ROLE_MANAGER);
        config.put("employee", ROLE_EMPLOYEE);
        config.put("user", ROLE_USER);
        return config;
    }

    /**
     * Get user type configuration
     */
    public Map<String, String> getUserTypeConfiguration() {
        Map<String, String> config = new HashMap<>();
        config.put("platform", USER_TYPE_PLATFORM);
        config.put("employee", USER_TYPE_EMPLOYEE);
        config.put("tenant", USER_TYPE_TENANT);
        return config;
    }

    // =====================================================
    // STATISTICS & UTILITY METHODS
    // =====================================================

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("localCache", localCache.stats());
        stats.put("roleCheckCache", roleCheckCache.stats());
        stats.put("permissionCheckCache", permissionCheckCache.stats());
        stats.put("redisAvailable", redisAvailable);
        stats.put("cacheEnabled", cacheEnabled);
        stats.put("cacheTtlMinutes", cacheTtlMinutes);
        stats.put("maxCacheSize", maxCacheSize);
        return stats;
    }

    /**
     * Get security context summary for logging
     */
    public Map<String, Object> getSecurityContextSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("authenticated", isAuthenticated());
        summary.put("email", getCurrentUserEmail());
        summary.put("userType", getCurrentUserType());
        summary.put("roles", getCurrentUserRoles());
        summary.put("tenantId", getCurrentTenantId());
        summary.put("employeeId", getCurrentEmployeeId());
        summary.put("employeeCode", getCurrentEmployeeCode());
        summary.put("isSuperAdmin", isSuperAdmin());
        summary.put("isAdmin", isAdmin());
        summary.put("isHR", isHR());
        summary.put("isManager", isManager());
        summary.put("isEmployee", isEmployee());
        summary.put("isPlatformUser", isPlatformUser());
        summary.put("isEmployeeUser", isEmployeeUser());
        summary.put("isTenantUser", isTenantUser());
        return summary;
    }

    // =====================================================
    // PRIVATE HELPER METHODS
    // =====================================================

    /**
     * Get cached security context for current user
     */
    private CachedSecurityContext getCachedContext() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        String email = auth.getName();
        if (email == null || "anonymousUser".equals(email)) {
            return null;
        }

        String lowerEmail = email.toLowerCase();

        // Check request cache first
        Map<String, Object> reqCache = requestCache.get();
        String reqKey = "cachedContext";
        if (reqCache.containsKey(reqKey)) {
            return (CachedSecurityContext) reqCache.get(reqKey);
        }

        // Check local cache
        String cacheKey = "context_" + lowerEmail;
        CachedSecurityContext cached = localCache.getIfPresent(cacheKey);
        if (cached != null && cached.isValid()) {
            reqCache.put(reqKey, cached);
            return cached;
        }

        // Check Redis cache
        if (redisAvailable) {
            try {
                String redisKey = REDIS_KEY_USER_CONTEXT + lowerEmail;
                cached = (CachedSecurityContext) redisTemplate.opsForValue().get(redisKey);
                if (cached != null) {
                    localCache.put(cacheKey, cached);
                    reqCache.put(reqKey, cached);
                    return cached;
                }
            } catch (Exception e) {
                log.debug("Redis unavailable for context cache read: {}", e.getMessage());
            }
        }

        // Build context from authentication
        Map<String, Object> claims = extractClaimsFromAuth(auth);
        if (claims.isEmpty()) {
            return null;
        }

        cached = new CachedSecurityContext(auth, claims, cacheTtlMinutes);

        // Cache the context
        if (cacheEnabled) {
            localCache.put(cacheKey, cached);
            reqCache.put(reqKey, cached);

            if (redisAvailable) {
                try {
                    String redisKey = REDIS_KEY_USER_CONTEXT + lowerEmail;
                    redisTemplate.opsForValue().set(redisKey, cached, cacheTtlMinutes, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.debug("Redis unavailable for context cache write: {}", e.getMessage());
                }
            }
        }

        return cached;
    }

    /**
     * Extract claims from authentication object
     */
    private Map<String, Object> extractClaimsFromAuth(Authentication auth) {
        Map<String, Object> claims = new HashMap<>();

        if (auth == null) {
            return claims;
        }

        Object details = auth.getDetails();
        if (details instanceof Map) {
            claims.putAll((Map<String, Object>) details);
        }

        // Add roles if not present
        if (!claims.containsKey("roles")) {
            List<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            claims.put("roles", roles);
        }

        return claims;
    }

    /**
     * Convert object to Long safely
     */
    private Long convertToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.debug("Failed to convert to Long: {}", value);
            }
        }
        return null;
    }
}