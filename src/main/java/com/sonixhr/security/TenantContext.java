package com.sonixhr.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NamedThreadLocal;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TenantContext {

    // ThreadLocal with better debugging
    private static final ThreadLocal<Long> currentTenant = new NamedThreadLocal<>("Current Tenant ID");
    private static final ThreadLocal<String> currentTenantName = new NamedThreadLocal<>("Current Tenant Name");
    private static final ThreadLocal<Long> currentUserId = new NamedThreadLocal<>("Current User ID");
    private static final ThreadLocal<String> requestId = new NamedThreadLocal<>("Request ID");
    private static final ThreadLocal<AtomicInteger> depth = ThreadLocal.withInitial(() -> new AtomicInteger(0));

    // =====================================================
    // TENANT MANAGEMENT
    // =====================================================

    public static void setCurrentTenant(Long tenantId) {
        if (tenantId == null) {
            log.warn("Attempting to set null tenant ID");
            return;
        }
        currentTenant.set(tenantId);
        log.debug("Tenant context set: {}", tenantId);
    }

    public static Long getCurrentTenant() {
        return currentTenant.get();
    }

    public static Long getCurrentTenantOrThrow() {
        Long tenantId = currentTenant.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context available");
        }
        return tenantId;
    }

    public static boolean hasTenantContext() {
        return currentTenant.get() != null;
    }

    // =====================================================
    // TENANT NAME MANAGEMENT
    // =====================================================

    public static void setCurrentTenantName(String tenantName) {
        if (tenantName != null) {
            currentTenantName.set(tenantName);
        }
    }

    public static String getCurrentTenantName() {
        return currentTenantName.get();
    }

    // =====================================================
    // USER MANAGEMENT
    // =====================================================

    public static void setCurrentUserId(Long userId) {
        if (userId != null) {
            currentUserId.set(userId);
            log.debug("User context set: {}", userId);
        }
    }

    public static Long getCurrentUserId() {
        return currentUserId.get();
    }

    public static Long getCurrentUserIdOrThrow() {
        Long userId = currentUserId.get();
        if (userId == null) {
            throw new IllegalStateException("No user context available");
        }
        return userId;
    }

    // =====================================================
    // REQUEST MANAGEMENT
    // =====================================================

    public static void setRequestId(String id) {
        requestId.set(id);
    }

    public static String getRequestId() {
        String id = requestId.get();
        if (id == null) {
            id = generateRequestId();
            requestId.set(id);
        }
        return id;
    }

    private static String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // =====================================================
    // NESTED CONTEXT SUPPORT (for async operations)
    // =====================================================

    public static void incrementDepth() {
        depth.get().incrementAndGet();
    }

    public static void decrementDepth() {
        AtomicInteger counter = depth.get();
        if (counter.decrementAndGet() == 0) {
            clear();
            depth.remove();
        }
    }

    public static int getDepth() {
        return depth.get().get();
    }

    // =====================================================
    // CONTEXT VALIDATION
    // =====================================================

    public static void validateContext() {
        if (currentTenant.get() == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        log.debug("Context validated - Tenant: {}, User: {}, Request: {}",
                currentTenant.get(), currentUserId.get(), getRequestId());
    }

    // =====================================================
    // CLEAR METHODS
    // =====================================================

    public static void clear() {
        currentTenant.remove();
        currentTenantName.remove();
        currentUserId.remove();
        // Don't remove requestId automatically - keep for logging
    }

    public static void clearAll() {
        currentTenant.remove();
        currentTenantName.remove();
        currentUserId.remove();
        requestId.remove();
        depth.remove();
        log.debug("All tenant context cleared");
    }

    // =====================================================
    // CONTEXT SNAPSHOT (for async operations)
    // =====================================================

    public static TenantContextSnapshot createSnapshot() {
        return new TenantContextSnapshot(
                currentTenant.get(),
                currentTenantName.get(),
                currentUserId.get(),
                getRequestId()
        );
    }

    public static void restoreSnapshot(TenantContextSnapshot snapshot) {
        if (snapshot != null) {
            currentTenant.set(snapshot.tenantId());
            currentTenantName.set(snapshot.tenantName());
            currentUserId.set(snapshot.userId());
            requestId.set(snapshot.requestId());
        }
    }

    // =====================================================
    // HTTP REQUEST CONTEXT (for API logging)
    // =====================================================

    public static String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            return ip;
        }
        return "unknown";
    }

    // =====================================================
    // CONTEXT INFO (for logging)
    // =====================================================

    public static String getContextInfo() {
        return String.format("[Tenant: %s, User: %s, Request: %s, Depth: %d]",
                currentTenant.get(),
                currentUserId.get(),
                getRequestId(),
                getDepth()
        );
    }

    // =====================================================
    // CONTEXT SNAPSHOT RECORD
    // =====================================================

    public record TenantContextSnapshot(
            Long tenantId,
            String tenantName,
            Long userId,
            String requestId
    ) {}
}