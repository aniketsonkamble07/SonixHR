package com.sonixhr.controller.platform;

import com.sonixhr.entity.common.ApiHitLog;
import com.sonixhr.service.common.ApiHitLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/platform/api-logs")
@RequiredArgsConstructor
public class PlatformApiHitLogController {

    private final ApiHitLogService apiHitLogService;

    /**
     * Get API hit logs with optional tenantId filtering.
     * Accessible only to platform-level administrators.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('API_LOG_VIEW', 'VIEW_SYSTEM_METRICS', 'VIEW_TENANTS')")
    public ResponseEntity<Page<ApiHitLog>> getAllApiLogs(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long tenantId,
            org.springframework.data.domain.Pageable pageable) {

        log.info("REST request by platform team to fetch API hit logs (tenantId: {})", tenantId);
        Page<ApiHitLog> logs;
        if (tenantId != null && tenantId > 0) {
            logs = apiHitLogService.getTenantLogs(tenantId, pageable);
        } else {
            logs = apiHitLogService.getAllLogs(pageable);
        }
        return ResponseEntity.ok(logs);
    }
}
