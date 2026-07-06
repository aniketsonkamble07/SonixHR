package com.sonixhr.service.common;

import com.sonixhr.entity.tenant.Tenant;
import com.sonixhr.entity.tenant.TenantAuditLog;
import com.sonixhr.repository.tenant.TenantAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final TenantAuditLogRepository auditLogRepository;

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public void log(Tenant tenant, String action, String fieldName, String oldValue, String newValue, UUID performedBy, String metadata) {
        try {
            HttpServletRequest request = getRequest();
            String ipAddress = null;
            String userAgent = null;
            if (request != null) {
                ipAddress = request.getRemoteAddr();
                userAgent = request.getHeader("User-Agent");
            }

            TenantAuditLog auditLog = TenantAuditLog.builder()
                    .tenant(tenant)
                    .action(action)
                    .fieldName(fieldName)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .performedBy(performedBy)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .metadata(metadata)
                    .build();
            
            auditLogRepository.save(auditLog);
            log.info("Audit log persisted: action={}, performedBy={}", action, performedBy);
        } catch (Exception e) {
            log.error("Failed to persist audit log", e);
        }
    }

    @Transactional
    public void log(Tenant tenant, String action, String fieldName, String oldValue, String newValue, Long performedBy, String metadata) {
        UUID performedByUuid = performedBy != null
                ? UUID.nameUUIDFromBytes(String.valueOf(performedBy).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : null;
        log(tenant, action, fieldName, oldValue, newValue, performedByUuid, metadata);
    }

    @Transactional
    public void log(Tenant tenant, String action, String fieldName, String oldValue, String newValue, String metadata) {
        Long performedBy = getCurrentUserId();
        log(tenant, action, fieldName, oldValue, newValue, performedBy, metadata);
    }

    public Long getCurrentUserId() {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof com.sonixhr.common.base.BaseEntity) {
                    return ((com.sonixhr.common.base.BaseEntity) principal).getId();
                }
            }
        } catch (Exception e) {
            log.error("Error retrieving current user ID for audit log", e);
        }
        return null;
    }
}
