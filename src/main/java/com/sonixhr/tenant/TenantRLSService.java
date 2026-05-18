package com.sonixhr.tenant;

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
    public void setCurrentTenantInDB(UUID tenantId) {
        if (tenantId == null) {
            jdbcTemplate.execute("SELECT set_current_tenant(NULL)");
            log.debug("Cleared tenant context in PostgreSQL");
        } else {
            jdbcTemplate.update("SELECT set_current_tenant(?)", tenantId);
            log.debug("Set tenant context in PostgreSQL to: {}", tenantId);
        }
    }

    @Transactional
    public void clearCurrentTenantInDB() {
        jdbcTemplate.execute("SELECT set_current_tenant(NULL)");
        log.debug("Cleared tenant context in PostgreSQL");
    }
}