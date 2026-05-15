package com.sonixhr.security;

import com.sonixhr.service.platform.PlatformUserDetailsService;
import com.sonixhr.tenant.TenantContext;
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

        System.out.println("\n========== JWT FILTER START ==========");

        String path = request.getRequestURI();

        System.out.println("Request URI: " + path);

        if (isPublicPath(path)) {

            System.out.println("PUBLIC API - SKIPPING JWT FILTER");

            filterChain.doFilter(request, response);

            return;
        }

        String authHeader = request.getHeader("Authorization");

        System.out.println("Authorization Header: " + authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            System.out.println("MISSING OR INVALID TOKEN");

            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid token"
            );

            return;
        }

        String token = authHeader.substring(7);

        System.out.println("JWT Token Received");

        boolean valid = jwtService.validateToken(token);

        System.out.println("Token Valid: " + valid);

        if (!valid) {

            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired token"
            );

            return;
        }

        Claims claims;

        try {

            claims = jwtService.extractAllClaims(token);

            System.out.println("Claims Extracted Successfully");

        } catch (Exception e) {

            System.out.println("FAILED TO PARSE JWT CLAIMS");

            e.printStackTrace();

            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid token"
            );

            return;
        }

        String username = claims.getSubject();
        String userType = claims.get("userType", String.class);

        System.out.println("Username: " + username);
        System.out.println("UserType: " + userType);

        Object rolesObj = claims.get("roles");

        List<String> permissionNames =
                rolesObj instanceof List<?> roles
                        ? roles.stream()
                        .map(Object::toString)
                        .toList()
                        : List.of();

        System.out.println("Permissions: " + permissionNames);

        Collection<? extends GrantedAuthority> authorities =
                permissionNames.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        UserDetails userDetails;

        boolean tenantDbContextSet = false;

        try {

            if ("TENANT".equals(userType)) {

                System.out.println("TENANT USER DETECTED");

                String tenantIdStr =
                        claims.get("tenantId", String.class);

                if (tenantIdStr == null) {

                    sendError(
                            response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Tenant ID missing"
                    );

                    return;
                }

                UUID tenantId;

                try {

                    tenantId = UUID.fromString(tenantIdStr);

                } catch (Exception e) {

                    sendError(
                            response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "Invalid tenant ID"
                    );

                    return;
                }

                System.out.println("Setting Tenant Context");

                TenantContext.setCurrentTenant(tenantId);

                System.out.println("Setting DB Tenant Context");

                tenantService.setCurrentTenantInDB(tenantId);

                tenantDbContextSet = true;

                userDetails =
                        tenantUserDetailsService
                                .loadUserByUsername(username);

                System.out.println(
                        "Tenant User Loaded: "
                                + userDetails.getUsername()
                );

            } else if ("PLATFORM".equals(userType)) {

                System.out.println("PLATFORM USER DETECTED");

                TenantContext.clear();

                userDetails =
                        platformUserDetailsService
                                .loadUserByUsername(username);

                System.out.println(
                        "Platform User Loaded: "
                                + userDetails.getUsername()
                );

            } else {

                sendError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Unknown user type"
                );

                return;
            }

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authToken);

            System.out.println(
                    "Authentication Stored Successfully"
            );

            System.out.println(
                    "SecurityContext Authentication: "
                            + SecurityContextHolder
                            .getContext()
                            .getAuthentication()
            );

            System.out.println(
                    "Proceeding To Controller"
            );

            filterChain.doFilter(request, response);

        } finally {

            System.out.println(
                    "\n========== CLEANUP START =========="
            );

            TenantContext.clear();

            System.out.println("TenantContext Cleared");

            if (tenantDbContextSet) {

                try {

                    tenantService.clearCurrentTenantInDB();

                    System.out.println(
                            "DB Tenant Context Cleared"
                    );

                } catch (Exception e) {

                    System.out.println(
                            "FAILED TO CLEAR DB TENANT CONTEXT"
                    );

                    e.printStackTrace();
                }
            }

            System.out.println(
                    "========== JWT FILTER END ==========\n"
            );
        }
    }

    private boolean isPublicPath(String path) {

        return PUBLIC_PATHS.stream()
                .anyMatch(pattern ->
                        pathMatcher.match(pattern, path));
    }

    private void sendError(HttpServletResponse response,
                           int status,
                           String message)
            throws IOException {

        System.out.println(
                "SEND ERROR -> " + status + " : " + message
        );

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