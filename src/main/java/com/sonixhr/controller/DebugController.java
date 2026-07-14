package com.sonixhr.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> debugDb() {
        try {
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT column_name, data_type, is_nullable " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = 'tenant_subscriptions'"
            );

            List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                    "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'tenant_subscriptions'"
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "columns", columns,
                    "indexes", indexes
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}