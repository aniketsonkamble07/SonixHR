package com.sonixhr.service.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRLSService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void setCurrentTenantInDB(Long tenantId) {
        if (tenantId == null) {
            log.warn("Attempting to set null tenant ID");
            return;
        }

        try {
            // Use execute() instead of update() for VOID functions
            jdbcTemplate.execute("SELECT set_current_tenant('" + tenantId + "')");
            log.debug("Set current tenant in database context: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to set current tenant in database: {}", e.getMessage());
            // Try alternative approach
            tryAlternativeSetTenant(tenantId);
        }
    }

    @Transactional
    public void clearCurrentTenantInDB() {
        try {
            // Use execute() instead of update() and handle potential errors
            jdbcTemplate.execute("SELECT clear_current_tenant()");
            log.debug("Cleared current tenant from database context");
        } catch (Exception e) {
            log.debug("clear_current_tenant function not available, trying alternative");
            try {
                jdbcTemplate.execute("SET app.current_tenant = NULL");
                log.debug("Cleared tenant using session variable");
            } catch (Exception ex) {
                // Log as debug since this is not critical
                log.debug("Could not clear tenant context: {}", ex.getMessage());
            }
        }
    }

    private void tryAlternativeSetTenant(Long tenantId) {
        try {
            jdbcTemplate.execute("SET app.current_tenant = '" + tenantId.toString() + "'");
            log.debug("Set tenant using session variable: {}", tenantId);
        } catch (Exception ex) {
            log.error("Alternative set tenant failed: {}", ex.getMessage());
        }
    }

    public Long getCurrentTenantFromDB() {
        try {
            return jdbcTemplate.queryForObject("SELECT get_current_tenant()", Long.class);
        } catch (Exception e) {
            log.warn("Could not get current tenant from database: {}", e.getMessage());
            return null;
        }
    }
}