package com.sonixhr.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class DiagnosticController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/db-query")
    public List<Map<String, Object>> runQuery(@RequestParam String sql) {
        log.info("Executing diagnostic SQL: {}", sql);
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/db-execute")
    public Map<String, Object> runExecute(@RequestParam String sql) {
        log.info("Executing diagnostic DDL: {}", sql);
        try {
            jdbcTemplate.execute(sql);
            return Map.of("success", true, "message", "Executed successfully");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
