package com.sonixhr.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(1)
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip for public endpoints
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Set request ID for tracking
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        TenantContext.setRequestId(requestId);

        // Get tenant ID from authenticated user (set by JwtAuthFilter)
        Long tenantId = extractTenantFromRequest(request);

        if (tenantId != null) {
            TenantContext.setCurrentTenant(tenantId);
            TenantContext.incrementDepth();

            log.info("Tenant context initialized - Tenant: {}, Request: {}, Path: {}, Method: {}",
                    tenantId, requestId, path, request.getMethod());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.decrementDepth();
            if (TenantContext.getDepth() == 0) {
                TenantContext.clear();
                log.debug("Tenant context cleared for request: {}", requestId);
            }
        }
    }

    private Long extractTenantFromRequest(HttpServletRequest request) {
        // Try to get from SecurityContext (already authenticated user)
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof com.sonixhr.entity.employee.Employee) {
            com.sonixhr.entity.employee.Employee employee =
                    (com.sonixhr.entity.employee.Employee) authentication.getPrincipal();
            return employee.getTenantId();
        }

        // Try from request header (for debugging)
        String tenantHeader = request.getHeader("X-Tenant-ID");
        if (tenantHeader != null) {
            try {
                return Long.parseLong(tenantHeader);
            } catch (NumberFormatException e) {
                log.warn("Invalid tenant ID in header: {}", tenantHeader);
            }
        }

        return null;
    }

    private boolean isPublicPath(String path) {
        String[] publicPaths = {
                "/api/auth/",
                "/api/public/",
                "/api/tenant/auth/",
                "/api/platform/auth/",
                "/api/health",
                "/actuator/",
                "/swagger-ui/",
                "/v3/api-docs/",
                "/error"
        };

        for (String publicPath : publicPaths) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        return false;
    }
}