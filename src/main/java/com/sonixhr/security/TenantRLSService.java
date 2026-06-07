package com.sonixhr.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRLSService {

    private final JdbcTemplate jdbcTemplate;

    // Store tenant ID in ThreadLocal for application-level isolation
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<Long> currentUserId = new ThreadLocal<>();

    /**
     * Set current tenant in database session using parameterized query (SECURE)
     */
    @Transactional
    public void setCurrentTenantInDB(Long tenantId) {
        if (tenantId == null) {
            log.warn("Attempting to set null tenant ID");
            return;
        }

        try {
            // ✅ SECURE: Use parameterized query instead of string concatenation
            jdbcTemplate.update(
                    "SELECT set_config('app.current_tenant_id', ?, false)",
                    tenantId.toString()
            );
            currentTenant.set(tenantId);
            log.debug("Set current tenant in database context: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to set current tenant in database: {}", e.getMessage());
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
            jdbcTemplate.update(
                    "SELECT set_config('app.current_user_id', ?, false)",
                    userId.toString()
            );
            currentUserId.set(userId);
            log.debug("Set current user ID in database context: {}", userId);
        } catch (Exception e) {
            log.error("Failed to set current user ID: {}", e.getMessage());
        }
    }

    /**
     * Clear current tenant from database session
     */
    @Transactional
    public void clearCurrentTenantInDB() {
        try {
            jdbcTemplate.update(
                    "SELECT set_config('app.current_tenant_id', NULL, false)"
            );
            currentTenant.remove();
            log.debug("Cleared current tenant from database context");
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
            jdbcTemplate.update(
                    "SELECT set_config('app.current_user_id', NULL, false)"
            );
            currentUserId.remove();
        } catch (Exception e) {
            log.warn("Could not clear user ID context: {}", e.getMessage());
        }
    }

    /**
     * Get current tenant ID from database session
     */
    public Long getCurrentTenantFromDB() {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT current_setting('app.current_tenant_id', true)::bigint",
                    Long.class
            );
        } catch (Exception e) {
            return currentTenant.get();
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
     * Initialize RLS functions in database (Run once during setup)
     */
    public void initializeRLSFunctions() {
        String createGetTenantFunction = """
            CREATE OR REPLACE FUNCTION get_current_tenant_id()
            RETURNS BIGINT AS $$
            BEGIN
                RETURN NULLIF(current_setting('app.current_tenant_id', true), '')::BIGINT;
            EXCEPTION
                WHEN OTHERS THEN
                    RETURN NULL;
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
            """;

        String createGetUserFunction = """
            CREATE OR REPLACE FUNCTION get_current_user_id()
            RETURNS BIGINT AS $$
            BEGIN
                RETURN NULLIF(current_setting('app.current_user_id', true), '')::BIGINT;
            EXCEPTION
                WHEN OTHERS THEN
                    RETURN NULL;
            END;
            $$ LANGUAGE plpgsql SECURITY DEFINER;
            """;

        try {
            jdbcTemplate.execute(createGetTenantFunction);
            jdbcTemplate.execute(createGetUserFunction);
            log.info("RLS functions initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize RLS functions: {}", e.getMessage());
        }
    }

    /**
     * Apply RLS policies to a table
     */
    public void applyRLSPolicy(String tableName, String tenantColumn) {
        String enableRLS = String.format("ALTER TABLE %s ENABLE ROW LEVEL SECURITY", tableName);

        String createPolicy = String.format("""
            CREATE POLICY %s_tenant_isolation ON %s
            USING (%s = get_current_tenant_id())
            """, tableName, tableName, tenantColumn);

        try {
            jdbcTemplate.execute(enableRLS);
            jdbcTemplate.execute(createPolicy);
            log.info("RLS policy applied to table: {}", tableName);
        } catch (Exception e) {
            log.error("Failed to apply RLS policy to {}: {}", tableName, e.getMessage());
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

    /**
     * Clear all thread-local contexts
     */
    public static void clearContext() {
        currentTenant.remove();
        currentUserId.remove();
    }
}