package com.sonixhr.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@SuppressWarnings({"unchecked", "null"})
public class SecurityUtils {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.security.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.security.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

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

    // Local cache with Caffeine for better performance
    private Map<String, CachedSecurityContext> localCache;

    // Cache for frequently accessed boolean results
    private Map<String, Boolean> roleCheckCache;
    private Map<String, Boolean> permissionCheckCache;

    @PostConstruct
    public void init() {
        localCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(5000)
                .<String, CachedSecurityContext>build().asMap();

        roleCheckCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(10000)
                .<String, Boolean>build().asMap();

        permissionCheckCache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(10000)
                .<String, Boolean>build().asMap();
    }

    // Redis cache keys
    private static final String REDIS_KEY_USER_CONTEXT = "security:user:context:";
    private static final String REDIS_KEY_USER_ROLES = "security:user:roles:";
    private static final String REDIS_KEY_PERMISSION = "security:permission:";

    // ThreadLocal for request-scoped caching (avoid repeated calls in same request)
    private static final ThreadLocal<Map<String, Object>> requestCache = ThreadLocal.withInitial(HashMap::new);

    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    public static class CachedSecurityContext {
        public String email;
        public List<String> roles;
        public String userType;
        public Long tenantId;  // Changed to Long for consistency
        public Long employeeId;
        public String employeeCode;
        public long cachedAt;
        public long expiresAt;

        // Public no-arg constructor for Jackson deserialization
        public CachedSecurityContext() {
        }

        public CachedSecurityContext(Authentication auth, Map<String, Object> claims, long ttlMinutes) {
            this.email = auth != null ? auth.getName() : null;
            this.roles = extractRolesFromClaims(claims);
            this.userType = (String) claims.get("userType");
            this.tenantId = extractTenantIdAsLong(claims);
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

        private Long extractTenantIdAsLong(Map<String, Object> claims) {
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
            if (employeeIdObj instanceof Integer) {
                return ((Integer) employeeIdObj).longValue();
            }
            if (employeeIdObj instanceof Long) {
                return (Long) employeeIdObj;
            }
            return null;
        }
    }

    /**
     * Get current authentication (fast path - no caching needed)
     */
    public Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Get cached security context for current user - Optimized
     */
    private CachedSecurityContext getCachedContext() {
        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        String email = auth.getName();
        if (email == null) {
            return null;
        }

        String key = getCacheKey(email);

        // Check request cache first (fastest - same request)
        Map<String, Object> reqCache = requestCache.get();
        String reqCacheKey = "context_" + key;
        if (reqCache.containsKey(reqCacheKey)) {
            return (CachedSecurityContext) reqCache.get(reqCacheKey);
        }

        // Check local cache second
        CachedSecurityContext cached = localCache.get(key);
        if (cached != null && cached.isValid()) {
            reqCache.put(reqCacheKey, cached);
            return cached;
        }

        // Try Redis cache
        if (cacheEnabled && redisTemplate != null) {
            String cacheKey = REDIS_KEY_USER_CONTEXT + key;
            CachedSecurityContext redisCached = (CachedSecurityContext) redisTemplate.opsForValue().get(cacheKey);
            if (redisCached != null) {
                localCache.put(key, redisCached);
                reqCache.put(reqCacheKey, redisCached);
                return redisCached;
            }
        }

        // Build context from authentication
        Map<String, Object> claims = extractClaimsFromAuth(auth);
        if (claims.isEmpty()) {
            return null;
        }

        CachedSecurityContext context = new CachedSecurityContext(auth, claims, cacheTtlMinutes);

        // Cache the context
        if (cacheEnabled) {
            localCache.put(key, context);
            reqCache.put(reqCacheKey, context);
            if (redisTemplate != null) {
                String cacheKey = REDIS_KEY_USER_CONTEXT + key;
                redisTemplate.opsForValue().set(cacheKey, context, cacheTtlMinutes, TimeUnit.MINUTES);
            }
        }

        return context;
    }

    /**
     * Extract claims from authentication object - Optimized
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

        // Only add authorities if not already present
        if (!claims.containsKey("roles")) {
            List<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            claims.put("roles", roles);
        }

        return claims;
    }

    /**
     * Get current user's email - Optimized with request cache
     */
    public String getCurrentUserEmail() {
        // Check request cache first
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
        log.debug("Current user email: {}", email);
        return email;
    }

    /**
     * Get current user's email with cache support
     */
    public String getCurrentUserEmailCached() {
        CachedSecurityContext context = getCachedContext();
        return context != null && context.email != null ? context.email : "anonymousUser";
    }

    /**
     * Get current user's roles - Optimized with multiple cache layers
     */
    public List<String> getCurrentUserRoles() {
        // Check request cache
        Map<String, Object> reqCache = requestCache.get();
        if (reqCache.containsKey("roles")) {
            return (List<String>) reqCache.get("roles");
        }

        CachedSecurityContext context = getCachedContext();
        if (context != null && context.roles != null) {
            reqCache.put("roles", context.roles);
            return context.roles;
        }

        Authentication auth = getCurrentAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return List.of("ROLE_ANONYMOUS");
        }

        // Try to get from authentication authorities (fastest)
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authStr -> authStr.startsWith("ROLE_"))
                .collect(Collectors.toList());

        if (!roles.isEmpty()) {
            reqCache.put("roles", roles);
            return roles;
        }

        // Try from details
        Object details = auth.getDetails();
        if (details instanceof Map) {
            Map<String, Object> detailsMap = (Map<String, Object>) details;
            Object rolesObj = detailsMap.get("roles");
            if (rolesObj instanceof List) {
                roles = (List<String>) rolesObj;
                reqCache.put("roles", roles);
                return roles;
            }
        }

        return List.of("ROLE_ANONYMOUS");
    }

    /**
     * Get current user's roles with Redis caching
     */
    public List<String> getCurrentUserRolesCached() {
        String email = getCurrentUserEmail();
        if (email == null) {
            return Collections.emptyList();
        }

        String key = getCacheKey(email);

        // Check request cache
        Map<String, Object> reqCache = requestCache.get();
        String cacheKey = "roles_cached_" + key;
        if (reqCache.containsKey(cacheKey)) {
            return (List<String>) reqCache.get(cacheKey);
        }

        if (cacheEnabled && redisTemplate != null) {
            String redisKey = REDIS_KEY_USER_ROLES + key;
            List<String> cachedRoles = (List<String>) redisTemplate.opsForValue().get(redisKey);
            if (cachedRoles != null) {
                reqCache.put(cacheKey, cachedRoles);
                return cachedRoles;
            }
        }

        List<String> roles = getCurrentUserRoles();

        // Cache the roles
        if (cacheEnabled && redisTemplate != null && !roles.isEmpty()) {
            String redisKey = REDIS_KEY_USER_ROLES + key;
            redisTemplate.opsForValue().set(redisKey, roles, cacheTtlMinutes, TimeUnit.MINUTES);
            reqCache.put(cacheKey, roles);
        }

        return roles;
    }

    /**
     * Check if current user has a specific role - Optimized with caching
     */
    public boolean hasRole(String role) {
        if (role == null || role.isEmpty()) {
            return false;
        }

        // Check request cache first
        Map<String, Object> reqCache = requestCache.get();
        String cacheKey = "hasRole_" + role;
        if (reqCache.containsKey(cacheKey)) {
            return (Boolean) reqCache.get(cacheKey);
        }

        // Check local cache
        String email = getCurrentUserEmail();
        String key = email != null ? getCacheKey(email) : "anonymous";
        String localCacheKey = "role_" + key + "_" + role;
        if (cacheEnabled) {
            Boolean cached = roleCheckCache.get(localCacheKey);
            if (cached != null) {
                reqCache.put(cacheKey, cached);
                return cached;
            }
        }

        List<String> roles = getCurrentUserRoles();

        // Optimized role check
        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        String roleWithoutPrefix = role.startsWith("ROLE_") ? role.substring(5) : role;

        boolean hasRole = roles.contains(role) ||
                roles.contains(roleWithPrefix) ||
                roles.contains(roleWithoutPrefix);

        // Cache the result
        if (cacheEnabled) {
            roleCheckCache.put(localCacheKey, hasRole);
        }
        reqCache.put(cacheKey, hasRole);

        return hasRole;
    }

    /**
     * Check if current user has any of the specified roles - Optimized
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }

        // Check request cache for combined result
        Map<String, Object> reqCache = requestCache.get();
        String cacheKey = "hasAnyRole_" + String.join("_", roles);
        if (reqCache.containsKey(cacheKey)) {
            return (Boolean) reqCache.get(cacheKey);
        }

        for (String role : roles) {
            if (hasRole(role)) {
                reqCache.put(cacheKey, true);
                return true;
            }
        }
        reqCache.put(cacheKey, false);
        return false;
    }

    /**
     * Check if current user has all of the specified roles
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

    /**
     * Role check methods using configured constants - Now cached
     */
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

    /**
     * Get current user type - Optimized
     */
    public String getCurrentUserType() {
        // Check request cache
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

        // Determine from UserDetails if available
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            String className = principal.getClass().getSimpleName();
            String userType = className.contains("Employee") ? USER_TYPE_EMPLOYEE :
                    className.contains("PlatformUser") ? USER_TYPE_PLATFORM : null;
            if (userType != null) {
                reqCache.put("userType", userType);
                return userType;
            }
        }

        return null;
    }

    public boolean isPlatformUser() {
        return USER_TYPE_PLATFORM.equals(getCurrentUserType());
    }

    public boolean isEmployeeUser() {
        return USER_TYPE_EMPLOYEE.equals(getCurrentUserType());
    }

    public boolean isTenantUser() {
        return USER_TYPE_TENANT.equals(getCurrentUserType());
    }

    /**
     * Get current tenant ID as Long - Optimized
     */
    public Long getCurrentTenantId() {
        // Check request cache
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

            if (tenantId instanceof Long) {
                reqCache.put("tenantId", (Long) tenantId);
                return (Long) tenantId;
            }
            if (tenantId instanceof Integer) {
                Long tid = ((Integer) tenantId).longValue();
                reqCache.put("tenantId", tid);
                return tid;
            }
            if (tenantId instanceof String) {
                try {
                    Long tid = Long.parseLong((String) tenantId);
                    reqCache.put("tenantId", tid);
                    return tid;
                } catch (NumberFormatException e) {
                    log.debug("Invalid tenant ID format: {}", tenantId);
                }
            }
        }
        return null;
    }

    /**
     * Get current tenant ID as Long (alias)
     */
    public Long getCurrentTenantIdAsLong() {
        return getCurrentTenantId();
    }

    /**
     * Get current employee ID - Optimized
     */
    public Long getCurrentEmployeeId() {
        // Check request cache
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
            if (employeeId instanceof Integer) {
                Long eid = ((Integer) employeeId).longValue();
                reqCache.put("employeeId", eid);
                return eid;
            }
            if (employeeId instanceof Long) {
                reqCache.put("employeeId", (Long) employeeId);
                return (Long) employeeId;
            }
            if (employeeId instanceof String) {
                try {
                    Long eid = Long.parseLong((String) employeeId);
                    reqCache.put("employeeId", eid);
                    return eid;
                } catch (NumberFormatException e) {
                    log.debug("Invalid employee ID format: {}", employeeId);
                }
            }
        }
        return null;
    }

    /**
     * Get current employee code - Optimized
     */
    public String getCurrentEmployeeCode() {
        // Check request cache
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
        // Check request cache
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
     * Check if user is authenticated - Fast path
     */
    public boolean isAuthenticated() {
        Authentication auth = getCurrentAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
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

    /**
     * Check permission with caching - Optimized
     */
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        // Check request cache
        Map<String, Object> reqCache = requestCache.get();
        String cacheKey = "hasPermission_" + permission;
        if (reqCache.containsKey(cacheKey)) {
            return (Boolean) reqCache.get(cacheKey);
        }

        // Check local cache
        String email = getCurrentUserEmail();
        String key = email != null ? getCacheKey(email) : "anonymous";
        if (cacheEnabled && email != null) {
            String localCacheKey = "perm_" + key + "_" + permission;
            Boolean cached = permissionCheckCache.get(localCacheKey);
            if (cached != null) {
                reqCache.put(cacheKey, cached);
                return cached;
            }
        }

        // Check Redis cache
        if (cacheEnabled && redisTemplate != null && email != null) {
            String redisKey = REDIS_KEY_PERMISSION + key + ":" + permission;
            Boolean cached = (Boolean) redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                if (cacheEnabled) {
                    permissionCheckCache.put("perm_" + key + "_" + permission, cached);
                }
                reqCache.put(cacheKey, cached);
                return cached;
            }
        }

        // Check roles for permission
        boolean hasPermission = hasRole(permission);

        // Cache the result
        if (cacheEnabled && email != null) {
            permissionCheckCache.put("perm_" + key + "_" + permission, hasPermission);
            if (redisTemplate != null) {
                String redisKey = REDIS_KEY_PERMISSION + key + ":" + permission;
                redisTemplate.opsForValue().set(redisKey, hasPermission, 1, TimeUnit.MINUTES);
            }
        }

        reqCache.put(cacheKey, hasPermission);
        return hasPermission;
    }

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

    /**
     * Invalidate cache for current user
     */
    public void invalidateCurrentUserCache() {
        String email = getCurrentUserEmail();
        if (email != null) {
            invalidateUserCache(email);
        }
    }

    /**
     * Invalidate cache for specific user
     */
    public void invalidateUserCache(String email) {
        if (email == null) {
            return;
        }

        String lowerEmail = email.toLowerCase();

        // Clear local caches matching the email suffix or exact email
        localCache.keySet().removeIf(k -> k.toLowerCase().endsWith(":" + lowerEmail) || k.equalsIgnoreCase(email));
        roleCheckCache.keySet().removeIf(k -> k.toLowerCase().contains(":" + lowerEmail + "_") || k.toLowerCase().contains("_" + lowerEmail + "_") || k.toLowerCase().contains(lowerEmail));
        permissionCheckCache.keySet().removeIf(k -> k.toLowerCase().contains(":" + lowerEmail + "_") || k.toLowerCase().contains("_" + lowerEmail + "_") || k.toLowerCase().contains(lowerEmail));

        // Clear Redis cache
        if (cacheEnabled && redisTemplate != null) {
            try {
                // Delete unqualified keys
                String contextKey = REDIS_KEY_USER_CONTEXT + email;
                String rolesKey = REDIS_KEY_USER_ROLES + email;
                redisTemplate.delete(contextKey);
                redisTemplate.delete(rolesKey);

                // Use case-insensitive non-blocking SCAN to find and delete qualified keys
                scanAndInvalidateRedisKeys(REDIS_KEY_USER_CONTEXT + "*", lowerEmail, false);
                scanAndInvalidateRedisKeys(REDIS_KEY_USER_ROLES + "*", lowerEmail, false);
                scanAndInvalidateRedisKeys(REDIS_KEY_PERMISSION + "*", lowerEmail, true);

                log.debug("Invalidated security cache for user: {}", email);
            } catch (Exception e) {
                log.warn("Failed to invalidate Redis cache for user {}: {}", email, e.getMessage());
            }
        }
    }

    private void scanAndInvalidateRedisKeys(String pattern, String suffix, boolean containsMode) {
        try {
            Set<String> keys = redisTemplate.execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                Set<String> keySet = new java.util.HashSet<>();
                org.springframework.data.redis.core.Cursor<byte[]> cursor = connection.keyCommands().scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(1000).build()
                );
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8);
                    String lowerKey = key.toLowerCase();
                    if (containsMode) {
                        if (lowerKey.contains(":" + suffix + ":")) {
                            keySet.add(key);
                        }
                    } else {
                        if (lowerKey.endsWith(":" + suffix)) {
                            keySet.add(key);
                        }
                    }
                }
                return keySet;
            });
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Failed to scan and invalidate Redis keys for pattern {} and suffix {}: {}", pattern, suffix, e.getMessage());
        }
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        localCache.clear();
        roleCheckCache.clear();
        permissionCheckCache.clear();

        if (cacheEnabled && redisTemplate != null) {
            scanAndDelete(REDIS_KEY_USER_CONTEXT + "*");
            scanAndDelete(REDIS_KEY_USER_ROLES + "*");
            scanAndDelete(REDIS_KEY_PERMISSION + "*");
            log.info("Cleared all security caches");
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
            }
        } catch (Exception e) {
            log.warn("Failed to scan and delete pattern {}: {}", pattern, e.getMessage());
        }
    }

    /**
     * Clear request cache (call at end of each request)
     */
    public static void clearRequestCache() {
        requestCache.remove();
    }

    private String getCacheKey(String email) {
        if (email == null || "anonymousUser".equalsIgnoreCase(email)) {
            return "anonymousUser";
        }

        // Check request cache first
        Map<String, Object> reqCache = requestCache.get();
        String reqKey = "cacheKey_" + email;
        if (reqCache.containsKey(reqKey)) {
            return (String) reqCache.get(reqKey);
        }

        String resultKey = email;
        Authentication auth = getCurrentAuthentication();
        if (auth != null && email.equalsIgnoreCase(auth.getName())) {
            Object details = auth.getDetails();
            if (details instanceof Map) {
                Map<String, Object> detailsMap = (Map<String, Object>) details;
                String userType = (String) detailsMap.get("userType");
                if ("EMPLOYEE".equals(userType)) {
                    Object tenantIdObj = detailsMap.get("tenantId");
                    String tenantIdStr = tenantIdObj != null ? tenantIdObj.toString() : "no-tenant";
                    resultKey = "EMPLOYEE:" + tenantIdStr + ":" + email;
                } else {
                    resultKey = "PLATFORM:" + email;
                }
            }
        }

        reqCache.put(reqKey, resultKey);
        return resultKey;
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
        summary.put("isSuperAdmin", isSuperAdmin());
        summary.put("isAdmin", isAdmin());
        summary.put("isHR", isHR());
        summary.put("isManager", isManager());
        summary.put("isEmployee", isEmployee());
        return summary;
    }
}