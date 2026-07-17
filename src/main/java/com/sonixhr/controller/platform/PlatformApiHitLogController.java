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
     * Get all API hit logs (ignores individual tenant visibility settings).
     * Accessible only to platform-level administrators.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public ResponseEntity<Page<ApiHitLog>> getAllApiLogs(Pageable pageable) {
        log.info("REST request by platform team to fetch all API hit logs");
        Page<ApiHitLog> logs = apiHitLogService.getAllLogs(pageable);
        return ResponseEntity.ok(logs);
    }
}
