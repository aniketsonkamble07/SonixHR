package com.sonixhr.controller.common;

import com.sonixhr.entity.common.ApiHitLog;
import com.sonixhr.entity.employee.Employee;
import com.sonixhr.exceptions.BusinessException;
import com.sonixhr.service.common.ApiHitLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/api-logs")
@RequiredArgsConstructor
public class ApiHitLogController {

    private final ApiHitLogService apiHitLogService;

    /**
     * Get paginated API hit logs for the current tenant.
     * Accessible to tenant super admins or users with role view authority.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN', 'API_LOG_VIEW')")
    public ResponseEntity<Page<ApiHitLog>> getTenantApiLogs(
            @AuthenticationPrincipal Employee currentUser,
            Pageable pageable) {

        Long tenantId = currentUser.getTenantId();
        log.info("REST request to fetch API hit logs for tenant: {}", tenantId);

        // Check if API logging visibility is enabled for the tenant
        if (!apiHitLogService.isApiLoggingEnabled(tenantId)) {
            throw new BusinessException("API hit logging features are currently disabled for your organization.");
        }

        Page<ApiHitLog> logs = apiHitLogService.getTenantLogs(tenantId, pageable);
        return ResponseEntity.ok(logs);
    }

    /**
     * Toggle the visibility/enabled status of API hit logs for the current tenant.
     */
    @PutMapping("/toggle")
    @PreAuthorize("hasAnyAuthority('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleApiLogging(
            @AuthenticationPrincipal Employee currentUser,
            @RequestParam boolean enabled) {

        Long tenantId = currentUser.getTenantId();
        log.info("REST request to toggle API logging visibility for tenant: {} to {}", tenantId, enabled);

        apiHitLogService.toggleApiLogging(tenantId, enabled);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("tenantId", tenantId);
        response.put("apiLoggingEnabled", enabled);
        response.put("message", "API logging visibility updated successfully.");
        return ResponseEntity.ok(response);
    }
}
