package com.sonixhr.security;

import com.sonixhr.service.TenantService;
import com.sonixhr.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantService tenantService;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/public/",
            "/api/health",
            "/actuator/health",
            "/api/tenants/register",
            "/api/tenants/verify-subdomain",
            "/api/forgot-password",
            "/api/reset-password",
            "/swagger-ui",
            "/v3/api-docs",
            "/api-docs"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip authentication for public endpoints
        if (isPublicPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (SecurityContextHolder.getContext().getAuthentication() == null) {

                    // Validate token
                    if (!jwtService.validateToken(token)) {
                        log.warn("Invalid token for request: {}", requestPath);
                        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                        return;
                    }

                    // Extract claims
                    Claims claims = jwtService.extractAllClaims(token);
                    String username = claims.getSubject();
                    String tenantId = claims.get("tenantId", String.class);

                    // Extract roles safely
                    Object rolesObj = claims.get("roles");
                    List<String> roles = new ArrayList<>();
                    if (rolesObj instanceof List) {
                        for (Object role : (List<?>) rolesObj) {
                            if (role instanceof String) {
                                roles.add((String) role);
                            }
                        }
                    }

                    // Validate token type
                    String tokenType = claims.get("tokenType", String.class);
                    if ("REFRESH".equals(tokenType) && !requestPath.contains("/auth/refresh")) {
                        log.warn("Attempted to use refresh token for non-refresh endpoint: {}", requestPath);
                        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token type");
                        return;
                    }

                    //  CRITICAL: Set tenant context for RLS
                    if (tenantId != null && !tenantId.isEmpty()) {
                        try {
                            UUID tenantUUID = UUID.fromString(tenantId);

                            // Set in ThreadLocal for Java layer
                            TenantContext.setCurrentTenant(tenantUUID);

                            // Set in PostgreSQL session for RLS
                            tenantService.setCurrentTenantInDB(tenantUUID);

                            log.debug("Tenant context set - Java: {}, PostgreSQL RLS: {}", tenantUUID, tenantId);
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid tenant ID format: {}", tenantId);
                            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant ID in token");
                            return;
                        }
                    } else {
                        log.warn("No tenant ID in token for user: {}", username);
                        sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: missing tenant");
                        return;
                    }

                    // Create authorities collection properly
                    List<GrantedAuthority> authorities;
                    if (roles != null && !roles.isEmpty()) {
                        authorities = roles.stream()
                                .map(role -> {
                                    String roleName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                                    return new SimpleGrantedAuthority(roleName);
                                })
                                .collect(Collectors.toList());
                    } else {
                        authorities = Collections.emptyList();
                    }

                    // Create authentication token with proper typing
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    log.debug("Authenticated user: {} with {} authorities for tenant: {}",
                            username, authorities.size(), tenantId);
                }
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
                return;
            }

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.error("JWT token expired: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token signature");
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Malformed token");
        } catch (Exception e) {
            log.error("JWT authentication error: {}", e.getMessage(), e);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
        } finally {
            // IMPORTANT: Clear tenant context after request
            TenantContext.clear();
            tenantService.clearCurrentTenantInDB();
            log.debug("Cleared tenant context after request");
        }
    }

    private boolean isPublicPath(String requestPath) {
        return PUBLIC_PATHS.stream().anyMatch(requestPath::startsWith);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"success\":false,\"status\":%d,\"message\":\"%s\",\"timestamp\":%d}",
                status, message, System.currentTimeMillis()
        ));
    }
}