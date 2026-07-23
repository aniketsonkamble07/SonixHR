package com.sonixhr.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TenantRLSService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.tenant.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.tenant.cache.ttl-minutes:10}")
    private long cacheTtlMinutes;

    // Store tenant ID in ThreadLocal for application-level isolation
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentRequestId = new ThreadLocal<>();

    // Local cache for tenant config (L1 cache)
    private final Map<Long, TenantConfig> localTenantCache = new ConcurrentHashMap<>();

    // Cache for frequently accessed tenant IDs
    private final Map<String, Boolean> tenantAccessCache = new ConcurrentHashMap<>();

    // Redis cache keys
    private static final String REDIS_KEY_TENANT_CONFIG = "tenant:config:";
    private static final String REDIS_KEY_TENANT_METRICS = "tenant:metrics:";

    // Batch update buffer for metrics
    private final Map<String, Long> metricsBuffer = new ConcurrentHashMap<>();

    /**
     * Set current tenant in database session using parameterized query (SECURE)
     */
    @Transactional
    public void setCurrentTenantInDB(Long tenantId) {
        if (tenantId == null) {
            log.warn("Attempting to set null tenant ID");
            return;
        }

        long startTime = System.nanoTime();

        try {
            // Use parameterized query instead of string concatenation
            jdbcTemplate.execute(
                    "SELECT set_config('app.current_tenant_id', ?, false)",
                    (org.springframework.jdbc.core.PreparedStatementCallback<Object>) ps -> {
                        ps.setString(1, tenantId.toString());
                        ps.execute();
                        return null;
                    }
            );
            currentTenant.set(tenantId);

            // Cache tenant configuration in Redis (async)
            if (cacheEnabled && redisTemplate != null) {
                cacheTenantConfigAsync(tenantId);
            }

            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            if (duration > 100) {
                log.warn("Setting tenant context took {}ms for tenant: {}", duration, tenantId);
            } else if (log.isDebugEnabled()) {
                log.debug("Set current tenant in database context: {} (took {}ms)", tenantId, duration);
            }
        } catch (Exception e) {
            log.error("Failed to set current tenant in database: {}", e.getMessage());
        }
    }

    /**
     * Cache tenant configuration asynchronously
     */
    private void cacheTenantConfigAsync(Long tenantId) {
        try {
            // Check local cache first
            if (localTenantCache.containsKey(tenantId)) {
                return;
            }

            String cacheKey = REDIS_KEY_TENANT_CONFIG + tenantId;

            // Check Redis without blocking
            Boolean exists = redisTemplate.hasKey(cacheKey);
            if (Boolean.TRUE.equals(exists)) {
                return;
            }

            // Load and cache in background (can be made async with @Async)
            cacheTenantConfig(tenantId);
        } catch (Exception e) {
            log.warn("Failed to cache tenant config async: {}", e.getMessage());
        }
    }

    /**
     * Cache tenant configuration in Redis - Optimized
     */
    private void cacheTenantConfig(Long tenantId) {
        try {
            String cacheKey = REDIS_KEY_TENANT_CONFIG + tenantId;

            String query = """
                SELECT company_name AS tenant_name, tenant_code AS schema_name, is_active, NULL AS config 
                FROM tenants 
                WHERE id = ? AND is_active = true
                LIMIT 1
            """;

            jdbcTemplate.query(query, rs -> {
                if (rs.next()) {
                    TenantConfig config = TenantConfig.builder()
                            .tenantName(rs.getString("tenant_name"))
                            .schemaName(rs.getString("schema_name"))
                            .isActive(rs.getBoolean("is_active"))
                            .config(rs.getString("config"))
                            .build();

                    // Cache in local L1 cache
                    localTenantCache.put(tenantId, config);

                    // Cache in Redis L2 cache
                    redisTemplate.opsForHash().putAll(cacheKey, Map.<String, Object>of(
                            "tenantName", config.getTenantName(),
                            "schemaName", config.getSchemaName(),
                            "isActive", config.isActive(),
                            "config", config.getConfig()
                    ));
                    redisTemplate.expire(cacheKey, cacheTtlMinutes, TimeUnit.MINUTES);
                }
                return null;
            }, tenantId);
        } catch (Exception e) {
            log.debug("Tenant config not found for ID: {}", tenantId);
        }
    }

    /**
     * Set current user ID in database session
     */
    @Transactional
    public void setCurrentUserIdInDB(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            jdbcTemplate.execute(
                    "SELECT set_config('app.current_user_id', ?, false)",
                    (org.springframework.jdbc.core.PreparedStatementCallback<Object>) ps -> {
                        ps.setString(1, userId.toString());
                        ps.execute();
                        return null;
                    }
            );
            currentUserId.set(userId);

            if (log.isDebugEnabled()) {
                log.debug("Set current user ID in database context: {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to set current user ID: {}", e.getMessage());
        }
    }

    /**
     * Set both tenant and user context in a single transaction - Optimized
     */
    @Transactional
    public void setContext(Long tenantId, Long userId) {
        // Batch the updates if possible
        if (tenantId != null && userId != null) {
            jdbcTemplate.execute(
                    "SELECT set_config('app.current_tenant_id', ?, false), set_config('app.current_user_id', ?, false)",
                    (org.springframework.jdbc.core.PreparedStatementCallback<Object>) ps -> {
                        ps.setString(1, tenantId.toString());
                        ps.setString(2, userId.toString());
                        ps.execute();
                        return null;
                    }
            );
            currentTenant.set(tenantId);
            currentUserId.set(userId);
        } else {
            if (tenantId != null) {
                setCurrentTenantInDB(tenantId);
            }
            if (userId != null) {
                setCurrentUserIdInDB(userId);
            }
        }
    }

    /**
     * Set context with request tracking - Optimized with buffered metrics
     */
    @Transactional
    public void setContextWithRequestId(Long tenantId, Long userId, String requestId) {
        setContext(tenantId, userId);
        currentRequestId.set(requestId);

        // Buffer metrics instead of writing to Redis on every request
        if (cacheEnabled && tenantId != null) {
            String metricKey = REDIS_KEY_TENANT_METRICS + tenantId + ":requests";
            metricsBuffer.merge(metricKey, 1L, Long::sum);

            // Flush every 100 requests or periodically
            if (metricsBuffer.size() >= 100) {
                flushMetrics();
            }
        }
    }

    /**
     * Flush metrics buffer to Redis
     */
    private void flushMetrics() {
        if (redisTemplate == null) return;

        try {
            metricsBuffer.forEach((key, value) -> {
                redisTemplate.opsForHash().increment(key, "total", value);
                redisTemplate.expire(key, 1, TimeUnit.HOURS);
            });
            metricsBuffer.clear();
        } catch (Exception e) {
            log.warn("Failed to flush metrics to Redis: {}", e.getMessage());
        }
    }

    /**
     * Clear current tenant from database session - Optimized
     */
    @Transactional
    public void clearCurrentTenantInDB() {
        try {
            jdbcTemplate.execute("SELECT set_config('app.current_tenant_id', NULL, false)");
            currentTenant.remove();

            if (log.isDebugEnabled()) {
                log.debug("Cleared current tenant from database context");
            }
        } catch (Exception e) {
            log.warn("Could not clear tenant context: {}", e.getMessage());
        }
    }

    /**
     * Clear current user ID from database session
     */
    @Transactional
    public void clearCurrentUserIdInDB() {
        try {
            jdbcTemplate.execute("SELECT set_config('app.current_user_id', NULL, false)");
            currentUserId.remove();

            if (log.isDebugEnabled()) {
                log.debug("Cleared current user ID from database context");
            }
        } catch (Exception e) {
            log.warn("Could not clear user ID context: {}", e.getMessage());
        }
    }

    /**
     * Clear all contexts
     */
    @Transactional
    public void clearAllContexts() {
        // Batch clear both in one call
        try {
            jdbcTemplate.execute("""
                SELECT set_config('app.current_tenant_id', NULL, false),
                       set_config('app.current_user_id', NULL, false)
            """);
        } catch (Exception e) {
            log.warn("Could not clear contexts: {}", e.getMessage());
        }

        currentTenant.remove();
        currentUserId.remove();
        currentRequestId.remove();
    }

    /**
     * Get current tenant ID from database session - Optimized with local cache
     */
    public Long getCurrentTenantFromDB() {
        long startTime = System.nanoTime();

        try {
            // Try database session first (fastest)
            Long tenantId = jdbcTemplate.queryForObject(
                    "SELECT current_setting('app.current_tenant_id', true)::bigint",
                    Long.class
            );

            if (tenantId != null) {
                logDuration(startTime, "getCurrentTenantFromDB");
                return tenantId;
            }
        } catch (Exception e) {
            // Fallback to ThreadLocal
            Long threadLocalTenant = currentTenant.get();
            if (threadLocalTenant != null) {
                log.debug("Falling back to ThreadLocal tenant: {}", threadLocalTenant);
                return threadLocalTenant;
            }
        }

        return null;
    }

    /**
     * Log duration for slow operations
     */
    private void logDuration(long startTime, String operation) {
        long duration = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startTime);
        if (duration > 100 && log.isDebugEnabled()) {
            log.debug("{} took {}μs", operation, duration);
        }
    }

    /**
     * Get current user ID from database session
     */
    public Long getCurrentUserIdFromDB() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT current_setting('app.current_user_id', true)::bigint",
                    Long.class
            );
        } catch (Exception e) {
            return currentUserId.get();
        }
    }

    /**
     * Get tenant configuration from cache or DB - Optimized with L1/L2 cache
     */
    public TenantConfig getTenantConfig(Long tenantId) {
        if (tenantId == null) {
            return null;
        }

        // L1: Check local cache first (fastest)
        TenantConfig cached = localTenantCache.get(tenantId);
        if (cached != null) {
            return cached;
        }

        // L2: Try Redis cache
        if (cacheEnabled && redisTemplate != null) {
            try {
                String cacheKey = REDIS_KEY_TENANT_CONFIG + tenantId;
                Map<Object, Object> redisCached = redisTemplate.opsForHash().entries(cacheKey);

                if (redisCached != null && !redisCached.isEmpty()) {
                    TenantConfig config = TenantConfig.builder()
                            .tenantName((String) redisCached.get("tenantName"))
                            .schemaName((String) redisCached.get("schemaName"))
                            .isActive(Boolean.TRUE.equals(redisCached.get("isActive")))
                            .config((String) redisCached.get("config"))
                            .build();

                    // Populate L1 cache
                    localTenantCache.put(tenantId, config);
                    return config;
                }
            } catch (Exception e) {
                log.warn("Failed to get tenant config from Redis: {}", e.getMessage());
            }
        }

        // L3: Load from database
        TenantConfig config = loadTenantConfigFromDB(tenantId);

        // Cache if found
        if (config != null && cacheEnabled) {
            localTenantCache.put(tenantId, config);
            if (redisTemplate != null) {
                try {
                    cacheTenantConfig(tenantId);
                } catch (Exception e) {
                    log.warn("Failed to cache tenant config in Redis: {}", e.getMessage());
                }
            }
        }

        return config;
    }

    /**
     * Load tenant config from database - Optimized query
     */
    private TenantConfig loadTenantConfigFromDB(Long tenantId) {
        try {
            String query = """
                SELECT company_name AS tenant_name, tenant_code AS schema_name, is_active, NULL AS config 
                FROM tenants 
                WHERE id = ? AND is_active = true
                LIMIT 1
            """;

            return jdbcTemplate.query(query, rs -> {
                if (rs.next()) {
                    return TenantConfig.builder()
                            .tenantName(rs.getString("tenant_name"))
                            .schemaName(rs.getString("schema_name"))
                            .isActive(rs.getBoolean("is_active"))
                            .config(rs.getString("config"))
                            .build();
                }
                return null;
            }, tenantId);
        } catch (Exception e) {
            log.error("Failed to load tenant config for ID {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate tenant cache (call when tenant configuration changes)
     */
    public void invalidateTenantCache(Long tenantId) {
        if (tenantId == null) return;

        // Clear L1 cache
        localTenantCache.remove(tenantId);

        // Clear L2 cache
        if (redisTemplate != null) {
            try {
                String cacheKey = REDIS_KEY_TENANT_CONFIG + tenantId;
                redisTemplate.delete(cacheKey);
            } catch (Exception e) {
                log.warn("Failed to delete tenant cache from Redis: {}", e.getMessage());
            }
        }

        log.info("Invalidated tenant cache for ID: {}", tenantId);
    }

    /**
     * Invalidate all tenant caches
     */
    public void invalidateAllCaches() {
        localTenantCache.clear();

        if (redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys(REDIS_KEY_TENANT_CONFIG + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Failed to clear tenant caches from Redis: {}", e.getMessage());
            }
        }

        log.info("Invalidated all tenant caches");
    }

    /**
     * Initialize RLS functions in database (Run once during setup)
     */
    public void initializeRLSFunctions() {
        String createGetTenantFunction = """
            CREATE OR REPLACE FUNCTION get_current_tenant_id()
            RETURNS BIGINT AS $$
            DECLARE
                tenant_id BIGINT;
            BEGIN
                BEGIN
                    tenant_id := NULLIF(current_setting('app.current_tenant_id', true), '')::BIGINT;
                EXCEPTION
                    WHEN OTHERS THEN
                        tenant_id := NULL;
                END;
                RETURN tenant_id;
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
            """;

        String createGetUserFunction = """
            CREATE OR REPLACE FUNCTION get_current_user_id()
            RETURNS BIGINT AS $$
            DECLARE
                user_id BIGINT;
            BEGIN
                BEGIN
                    user_id := NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;
                EXCEPTION
                    WHEN OTHERS THEN
                        user_id := NULL;
                END;
                RETURN user_id;
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
            """;

        String createRowLevelSecurityFunction = """
            CREATE OR REPLACE FUNCTION check_tenant_access(row_tenant_id BIGINT)
            RETURNS BOOLEAN AS $$
            BEGIN
                RETURN (row_tenant_id = get_current_tenant_id() OR get_current_tenant_id() IS NULL);
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
            """;

        try {
            jdbcTemplate.execute(createGetTenantFunction);
            jdbcTemplate.execute(createGetUserFunction);
            jdbcTemplate.execute(createRowLevelSecurityFunction);
            log.info("RLS functions initialized successfully");
        } catch (Exception e) {
            log.warn("Failed to initialize RLS functions: {}", e.getMessage());
        }
    }

    /**
     * Apply RLS policies to a table with optimized policy creation
     */
    /**
     * Validates that an identifier contains only safe characters for use in DDL.
     * Prevents SQL injection when identifiers are interpolated into DDL strings.
     */
    private void validateSqlIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
        }
    }

    @Transactional
    @SuppressWarnings("squid:S2077")
    public void applyRLSPolicy(String tableName, String tenantColumn) {
        if (tableName == null || tenantColumn == null) {
            log.warn("Cannot apply RLS policy: tableName or tenantColumn is null");
            return;
        }
        validateSqlIdentifier(tableName);
        validateSqlIdentifier(tenantColumn);

        String enableRLS = String.format("ALTER TABLE %s ENABLE ROW LEVEL SECURITY", tableName);
        String policyName = String.format("%s_tenant_isolation", tableName);
        String dropPolicy = String.format("DROP POLICY IF EXISTS %s ON %s", policyName, tableName);
        String createPolicy = String.format("""
            CREATE POLICY %s ON %s
            USING (%s = get_current_tenant_id())
            WITH CHECK (%s = get_current_tenant_id())
            """, policyName, tableName, tenantColumn, tenantColumn);

        try {
            jdbcTemplate.execute(enableRLS);
            jdbcTemplate.execute(dropPolicy);
            jdbcTemplate.execute(createPolicy);

            log.info("Applied RLS policy on table: {} with tenant column: {}", tableName, tenantColumn);
        } catch (Exception e) {
            log.error("Failed to apply RLS policy on table {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * Apply RLS policies to multiple tables in batch
     */
    @Transactional
    @SuppressWarnings("squid:S2077")
    public void applyRLSPolicies(Map<String, String> tableTenantColumnMap) {
        // Use batch update for better performance
        String[] sqls = tableTenantColumnMap.entrySet().stream()
                .map(entry -> String.format("ALTER TABLE %s ENABLE ROW LEVEL SECURITY", entry.getKey()))
                .toArray(String[]::new);

        jdbcTemplate.batchUpdate(sqls);

        for (Map.Entry<String, String> entry : tableTenantColumnMap.entrySet()) {
            applyRLSPolicy(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Remove RLS policy from a table
     */
    @Transactional
    public void removeRLSPolicy(String tableName) {
        String policyName = String.format("%s_tenant_isolation", tableName);
        String dropPolicy = String.format("DROP POLICY IF EXISTS %s ON %s", policyName, tableName);
        String disableRLS = String.format("ALTER TABLE %s DISABLE ROW LEVEL SECURITY", tableName);

        try {
            jdbcTemplate.execute(dropPolicy);
            jdbcTemplate.execute(disableRLS);
            log.info("Removed RLS policy from table: {}", tableName);
        } catch (Exception e) {
            log.error("Failed to remove RLS policy from table {}: {}", tableName, e.getMessage());
        }
    }

    /**
     * Thread-local getters for application-level tenant context
     */
    public static Long getCurrentTenant() {
        return currentTenant.get();
    }

    public static Long getCurrentUserId() {
        return currentUserId.get();
    }

    public static String getCurrentRequestId() {
        return currentRequestId.get();
    }

    /**
     * Clear all thread-local contexts
     */
    public static void clearContext() {
        currentTenant.remove();
        currentUserId.remove();
        currentRequestId.remove();
    }

    /**
     * Get tenant metrics from Redis - Optimized
     */
    public Map<Object, Object> getTenantMetrics(Long tenantId) {
        if (redisTemplate == null || tenantId == null) {
            return Map.of();
        }

        try {
            // Flush pending metrics before reading
            flushMetrics();

            String metricKey = REDIS_KEY_TENANT_METRICS + tenantId + ":requests";
            Map<Object, Object> metrics = redisTemplate.opsForHash().entries(metricKey);

            if (metrics == null || metrics.isEmpty()) {
                return Map.of("total", 0L);
            }

            return metrics;
        } catch (Exception e) {
            log.warn("Failed to get tenant metrics from Redis: {}", e.getMessage());
            return Map.of("total", 0L);
        }
    }

    /**
     * Check if current user has access to a tenant - Optimized with cache
     */
    public boolean hasTenantAccess(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return false;
        }

        String cacheKey = "tenant_access:" + userId + ":" + tenantId;

        // Check local cache
        Boolean cached = tenantAccessCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            String query = """
                SELECT COUNT(*) FROM user_tenant_access 
                WHERE user_id = ? AND tenant_id = ? AND is_active = true
                LIMIT 1
            """;

            Integer count = jdbcTemplate.queryForObject(query, Integer.class, userId, tenantId);
            boolean hasAccess = count != null && count > 0;

            // Cache the result (positive cache only, TTL via scheduled cleanup)
            if (hasAccess) {
                tenantAccessCache.put(cacheKey, true);
            }

            return hasAccess;
        } catch (Exception e) {
            log.error("Failed to check tenant access: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Clean up access cache periodically
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
    public void cleanupAccessCache() {
        int beforeSize = tenantAccessCache.size();
        tenantAccessCache.clear();
        log.debug("Cleaned tenant access cache: removed {} entries", beforeSize);
    }

    /**
     * Clean up metrics buffer periodically
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void flushMetricsPeriodically() {
        if (!metricsBuffer.isEmpty()) {
            flushMetrics();
        }
    }

    /**
     * Validate tenant context consistency
     */
    public boolean validateTenantContext(Long expectedTenantId) {
        Long actualTenantId = getCurrentTenantFromDB();

        if (actualTenantId == null && expectedTenantId == null) {
            return true;
        }

        if (actualTenantId == null || expectedTenantId == null) {
            if (log.isWarnEnabled()) {
                log.warn("Tenant context mismatch: expected={}, actual={}", expectedTenantId, actualTenantId);
            }
            return false;
        }

        boolean isValid = actualTenantId.equals(expectedTenantId);
        if (!isValid && log.isWarnEnabled()) {
            log.warn("Tenant context mismatch: expected={}, actual={}", expectedTenantId, actualTenantId);
        }

        return isValid;
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        return Map.of(
                "localTenantCacheSize", localTenantCache.size(),
                "tenantAccessCacheSize", tenantAccessCache.size(),
                "metricsBufferSize", metricsBuffer.size()
        );
    }
}

/**
 * Tenant Configuration DTO
 */
@lombok.Builder
@lombok.Data
class TenantConfig {
    private String tenantName;
    private String schemaName;
    private boolean isActive;
    private String config;
}