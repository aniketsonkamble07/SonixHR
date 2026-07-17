package com.sonixhr.service.common;

import com.sonixhr.entity.common.ApiHitLog;
import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.exceptions.ResourceNotFoundException;
import com.sonixhr.repository.common.ApiHitLogRepository;
import com.sonixhr.repository.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiHitLogService {

    private final ApiHitLogRepository apiHitLogRepository;
    private final TenantRepository tenantRepository;

    /**
     * Save the API hit log asynchronously in a background thread to prevent performance impact on the main request.
     */
    @Async
    @Transactional
    public void saveLog(ApiHitLog apiHitLog) {
        try {
            apiHitLogRepository.save(apiHitLog);
            log.trace("Asynchronously saved API hit log: {}", apiHitLog.getRequestUri());
        } catch (Exception e) {
            log.error("Failed to save API hit log asynchronously", e);
        }
    }

    /**
     * Get paginated logs for a specific tenant.
     */
    public Page<ApiHitLog> getTenantLogs(Long tenantId, Pageable pageable) {
        log.debug("Fetching API hit logs for tenant ID: {}", tenantId);
        return apiHitLogRepository.findByTenantId(tenantId, pageable);
    }

    /**
     * Get all logs (for platform team analysis).
     */
    public Page<ApiHitLog> getAllLogs(Pageable pageable) {
        log.debug("Fetching all API hit logs (platform view)");
        return apiHitLogRepository.findAll(pageable);
    }

    /**
     * Toggle the visibility/status of API logging for a tenant.
     */
    @Transactional
    public void toggleApiLogging(Long tenantId, boolean enabled) {
        log.info("Toggling API logging visibility for tenant ID: {} to {}", tenantId, enabled);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        tenant.setApiLoggingEnabled(enabled);
        tenantRepository.save(tenant);
    }

    /**
     * Check if a tenant has API logging enabled for their own view.
     */
    public boolean isApiLoggingEnabled(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::isApiLoggingEnabled)
                .orElse(false);
    }
}
