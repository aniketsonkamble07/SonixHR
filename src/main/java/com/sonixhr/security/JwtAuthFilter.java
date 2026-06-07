package com.sonixhr.security;

import com.sonixhr.service.employee.EmployeeDetailsService;
import com.sonixhr.service.platform.PlatformUserDetailsService;
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
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRLSService tenantRLSService;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final EmployeeDetailsService employeeDetailsService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/platform/auth/login",
            "/api/platform/auth/refresh",
            "/api/platform/auth/activate",
            "/api/platform/auth/forgot-password",
            "/api/platform/auth/reset-password",
            "/api/platform/auth/verify-token",
            "/api/tenant/auth/**",
            "/api/public/**",
            "/api/employee/auth/activate",
            "/api/health",
            "/actuator/health",
            "/api/tenants/register",
            "/api/tenants/verify-subdomain/**",
            "/api/forgot-password/**",
            "/api/reset-password/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/error",
            "/api/debug/**"
    );

    public JwtAuthFilter(
            JwtService jwtService,
            @Lazy TenantRLSService tenantRLSService,
            PlatformUserDetailsService platformUserDetailsService,
            EmployeeDetailsService employeeDetailsService
    ) {
        this.jwtService = jwtService;
        this.tenantRLSService = tenantRLSService;
        this.platformUserDetailsService = platformUserDetailsService;
        this.employeeDetailsService = employeeDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip filter for public paths
        if (isPublicPath(path)) {
            log.debug("Public path - skipping auth: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        log.info("========== JWT FILTER PROCESSING ==========");
        log.info("Request URI: {}", path);
        log.info("Request Method: {}", request.getMethod());

        String authHeader = request.getHeader("Authorization");
        log.info("Authorization Header: {}", authHeader != null ? "Present" : "Missing");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("No valid Bearer token found for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        log.info("Token (first 20 chars): {}...", token.substring(0, Math.min(20, token.length())));

        try {
            // Validate token
            if (!jwtService.validateToken(token)) {
                log.error("Invalid token");
                filterChain.doFilter(request, response);
                return;
            }

            // Extract claims
            Claims claims = jwtService.extractAllClaims(token);
            String username = claims.getSubject();
            String userType = claims.get("userType", String.class);

            log.info("Token valid - Username: {}, UserType: {}", username, userType);

            // Check if already authenticated
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                log.info("Already authenticated, skipping");
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails userDetails = null;
            boolean tenantDbContextSet = false;

            try {
                if ("PLATFORM".equals(userType)) {
                    log.info("Loading platform user: {}", username);
                    userDetails = platformUserDetailsService.loadUserByUsername(username);
                    log.info("Platform user loaded: {}", userDetails.getUsername());
                    TenantContext.clear();

                } else if ("EMPLOYEE".equals(userType)) {
                    log.info("Loading employee: {}", username);
                    String tenantIdStr = claims.get("tenantId", String.class);
                    if (tenantIdStr != null) {
                        Long tenantId = Long.parseLong(tenantIdStr);
                        TenantContext.setCurrentTenant(tenantId);
                        tenantRLSService.setCurrentTenantInDB(tenantId);
                        tenantDbContextSet = true;
                    }
                    userDetails = employeeDetailsService.loadUserByUsername(username);
                    log.info("Employee loaded: {}", userDetails.getUsername());
                }

                if (userDetails != null) {
                    // Get authorities from token or from user details
                    Collection<? extends GrantedAuthority> authorities;
                    Object rolesObj = claims.get("roles");
                    if (rolesObj instanceof List) {
                        List<String> roles = (List<String>) rolesObj;
                        authorities = roles.stream()
                                .map(SimpleGrantedAuthority::new)
                                .collect(Collectors.toList());
                    } else {
                        authorities = userDetails.getAuthorities();
                    }

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

                    // Set authentication in context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("✅ Authentication set for user: {}", username);
                    log.info("   Authorities: {}", authorities);
                }
            } finally {
                // Don't clear tenant context here, it will be cleared after request
            }

        } catch (Exception e) {
            log.error("Error processing JWT: {}", e.getMessage(), e);
            // Clear security context on error
            SecurityContextHolder.clearContext();
        }

        log.info("Continuing filter chain");
        filterChain.doFilter(request, response);

        // Clean up tenant context after request
        if (TenantContext.getCurrentTenant() != null) {
            TenantContext.clear();
            try {
                tenantRLSService.clearCurrentTenantInDB();
            } catch (Exception e) {
                log.error("Failed to clear DB tenant context: {}", e.getMessage());
            }
        }

        log.info("========== JWT FILTER COMPLETED ==========");
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}