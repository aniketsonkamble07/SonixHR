package com.sonixhr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpirationInterceptor implements HandlerInterceptor {

    private final TenantExpirationService tenantExpirationService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Skip for public/health endpoints
        if (isPublicPath(path)) {
            return true;
        }

        // Skip for platform admin APIs
        if (path.startsWith("/api/platform/")) {
            return true;
        }

        // Skip for tenant registration/activation
        if (isAuthPath(path)) {
            return true;
        }

        // Check if this is a tenant API
        if (!path.startsWith("/api/tenant/") && !path.startsWith("/api/tenants/")) {
            return true;
        }

        // Get current tenant from context
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            // Try to get from request attribute (set by JwtAuthFilter)
            Object tenantIdAttr = request.getAttribute("tenantId");
            if (tenantIdAttr instanceof Long) {
                tenantId = (Long) tenantIdAttr;
            } else {
                return true; // No tenant context, let it proceed (will fail auth later)
            }
        }

        // Check tenant status
        TenantExpirationService.TenantStatus status = tenantExpirationService.getTenantStatus(tenantId);

        // If tenant is active, allow all
        if (status == TenantExpirationService.TenantStatus.ACTIVE) {
            return true;
        }

        // Check if path is allowed for expired tenants
        boolean isAllowedPath = tenantExpirationService.isPathAllowedForExpiredTenant(path);
        boolean isReadOnly = tenantExpirationService.isReadOnlyOperation(method);

        // For expired/archived tenants: ONLY allow READ-ONLY billing/subscription paths
        // EXCEPT if the user has administrative authority (MANAGE_SUBSCRIPTION or VIEW_BILLING) to self-serve activate/renew/reactivate/upgrade
        if (status != TenantExpirationService.TenantStatus.ACTIVE) {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a != null && ("MANAGE_SUBSCRIPTION".equalsIgnoreCase(a.getAuthority())
                            || "VIEW_BILLING".equalsIgnoreCase(a.getAuthority())));

            if (isAllowedPath && (isReadOnly || isAdmin)) {
                log.debug("Expired tenant {} accessing allowed path: {} {} (isAdmin={})", tenantId, method, path, isAdmin);
                return true;
            }

            // Block all other requests
            log.warn("Blocking request from expired tenant {}: {} {} (Status: {})",
                    tenantId, method, path, status);

            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("status", status.name());
            body.put("message", tenantExpirationService.getTenantStatusMessage(status));
            body.put("timestamp", LocalDateTime.now().toString());
            body.put("path", path);

            // Add helpful info for expired tenants
            if (status == TenantExpirationService.TenantStatus.EXPIRED ||
                    status == TenantExpirationService.TenantStatus.RETAINED) {
                body.put("actionRequired", "Please renew your subscription to regain full access.");
                body.put("allowedActions", "You can still access billing and subscription management.");
            } else if (status == TenantExpirationService.TenantStatus.ELIGIBLE_FOR_DELETION) {
                body.put("actionRequired", "Your data is scheduled for permanent deletion. Please contact support immediately.");
            } else if (status == TenantExpirationService.TenantStatus.SUSPENDED) {
                body.put("actionRequired", "Please contact support to reactivate your account.");
            } else if (status == TenantExpirationService.TenantStatus.DELETED) {
                body.put("actionRequired", "Tenant account has been soft-deleted. Please contact support.");
            }

            objectMapper.writeValue(response.getOutputStream(), body);
            return false;
        }

        return true;
    }

    private boolean isPublicPath(String path) {
        return PublicPaths.isPublic(path) ||
                path.startsWith("/actuator/") ||
                path.startsWith("/api/health") ||
                path.startsWith("/swagger-ui/") ||
                path.startsWith("/v3/api-docs/");
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/api/tenant/auth/activate") ||
                path.startsWith("/api/tenant/auth/forgot-password") ||
                path.startsWith("/api/tenant/auth/reset-password") ||
                path.startsWith("/api/tenants/register");
    }
}