package com.sonixhr.security;

import com.sonixhr.service.platform.PlatformUserDetailsService;
import com.sonixhr.tenant.TenantUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantContext.TenantService tenantService;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final TenantUserDetailsService tenantUserDetailsService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(
            JwtService jwtService,
            @Lazy TenantContext.TenantService tenantService,
            PlatformUserDetailsService platformUserDetailsService,
            TenantUserDetailsService tenantUserDetailsService
    ) {
        this.jwtService = jwtService;
        this.tenantService = tenantService;
        this.platformUserDetailsService = platformUserDetailsService;
        this.tenantUserDetailsService = tenantUserDetailsService;
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/platform/auth/**",
            "/api/public/**",
            "/api/health",
            "/actuator/health",
            "/api/tenants/register",
            "/api/tenants/verify-subdomain/**",
            "/api/forgot-password/**",
            "/api/reset-password/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/error"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        log.debug("JwtAuthFilter invoked for request: {}", request.getRequestURI());

        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            log.debug("Public path - skipping authentication: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        log.debug("Authorization header present: {}", authHeader != null);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid token");
            return;
        }

        String token = authHeader.substring(7);
        log.debug("JWT token received (first 20 chars): {}...",
                token.substring(0, Math.min(20, token.length())));

        boolean valid = jwtService.validateToken(token);
        log.debug("Token validation result: {}", valid);

        if (!valid) {
            log.warn("Invalid or expired token for path: {}", path);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        Claims claims;
        try {
            claims = jwtService.extractAllClaims(token);
            log.debug("JWT claims extracted successfully");
        } catch (Exception e) {
            log.error("Failed to parse JWT claims: {}", e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        String username = claims.getSubject();
        String userType = claims.get("userType", String.class);
        log.info("Authenticating user: {} with type: {}", username, userType);

        Object rolesObj = claims.get("roles");
        List<String> permissionNames = rolesObj instanceof List<?> roles
                ? roles.stream()
                .map(Object::toString)
                .toList()
                : List.of();

        log.debug("Permissions extracted: {} permissions", permissionNames.size());

        Collection<? extends GrantedAuthority> authorities =
                permissionNames.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        UserDetails userDetails;
        boolean tenantDbContextSet = false;

        try {
            if ("TENANT".equals(userType)) {
                log.debug("Tenant user detected");

                String tenantIdStr = claims.get("tenantId", String.class);
                if (tenantIdStr == null) {
                    log.warn("Tenant ID missing for tenant user: {}", username);
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Tenant ID missing");
                    return;
                }

                UUID tenantId;
                try {
                    tenantId = UUID.fromString(tenantIdStr);
                } catch (Exception e) {
                    log.warn("Invalid tenant ID format: {}", tenantIdStr);
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant ID");
                    return;
                }

                log.debug("Setting tenant context for tenant: {}", tenantId);
                TenantContext.setCurrentTenant(tenantId);
                tenantService.setCurrentTenantInDB(tenantId);
                tenantDbContextSet = true;

                userDetails = tenantUserDetailsService.loadUserByUsername(username);
                log.debug("Tenant user loaded: {}", userDetails.getUsername());

            } else if ("PLATFORM".equals(userType)) {
                log.debug("Platform user detected - no tenant context needed");
                TenantContext.clear();

                userDetails = platformUserDetailsService.loadUserByUsername(username);
                log.debug("Platform user loaded: {}", userDetails.getUsername());

            } else {
                log.warn("Unknown user type: {} for user: {}", userType, username);
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unknown user type");
                return;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("Authentication successfully set for user: {}", username);

            filterChain.doFilter(request, response);

        } finally {
            log.debug("Cleaning up tenant context");
            TenantContext.clear();

            if (tenantDbContextSet) {
                try {
                    tenantService.clearCurrentTenantInDB();
                    log.debug("Database tenant context cleared");
                } catch (Exception e) {
                    log.error("Failed to clear database tenant context: {}", e.getMessage());
                }
            }
        }
    }

    private boolean isPublicPath(String path) {
        boolean isPublic = PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
        if (isPublic) {
            log.trace("Path matched as public: {}", path);
        }
        return isPublic;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        log.warn("Sending error response: {} - {}", status, message);
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format("""
                {
                    "success": false,
                    "status": %d,
                    "message": "%s"
                }
                """, status, message));
    }
}