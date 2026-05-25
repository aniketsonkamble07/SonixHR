package com.sonixhr.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseFunctionInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initializeFunctions() {

        String createFunctionSql = """
                CREATE OR REPLACE FUNCTION set_current_tenant(
                    p_tenant_id UUID
                )
                RETURNS void AS
                $$
                BEGIN
                    PERFORM set_config(
                        'app.current_tenant',
                        COALESCE(p_tenant_id::text, ''),
                        false
                    );
                END;
                $$ LANGUAGE plpgsql;
                """;

        jdbcTemplate.execute(createFunctionSql);

        log.info("PostgreSQL function set_current_tenant created successfully");
    }
}