package com.sonixhr.security;

import com.sonixhr.service.platform.PlatformUserDetailsService;
import com.sonixhr.service.tenant.TenantContext;
import com.sonixhr.service.tenant.TenantRLSService;
import com.sonixhr.service.tenant.TenantUserDetailsService;
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
    private final TenantUserDetailsService tenantUserDetailsService;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(
            JwtService jwtService,
            @Lazy TenantRLSService tenantRLSService,
            PlatformUserDetailsService platformUserDetailsService,
            TenantUserDetailsService tenantUserDetailsService
    ) {
        this.jwtService = jwtService;
        this.tenantRLSService = tenantRLSService;
        this.platformUserDetailsService = platformUserDetailsService;
        this.tenantUserDetailsService = tenantUserDetailsService;
    }

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/**",
            "/api/platform/auth/**",
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
            "/error"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println("\n========== JWT FILTER START ==========");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Request Method: " + request.getMethod());

        String path = request.getRequestURI();

        if (isPublicPath(path)) {
            System.out.println("PUBLIC PATH - SKIPPING AUTH: " + path);
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("PROTECTED PATH - NEEDS AUTH: " + path);

        String authHeader = request.getHeader("Authorization");
        System.out.println("Authorization Header: '" + authHeader + "'");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("ERROR: Missing or invalid Authorization header");
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid token");
            return;
        }

        String token = authHeader.substring(7);
        System.out.println("Token (first 20 chars): " + token.substring(0, Math.min(20, token.length())) + "...");

        boolean valid = jwtService.validateToken(token);
        System.out.println("Token Valid: " + valid);

        if (!valid) {
            System.out.println("ERROR: Invalid or expired token");
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        Claims claims;
        try {
            claims = jwtService.extractAllClaims(token);
            System.out.println("Claims extracted successfully");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to parse JWT claims: " + e.getMessage());
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        String username = claims.getSubject();
        String userType = claims.get("userType", String.class);
        System.out.println("Username: " + username);
        System.out.println("UserType: " + userType);

        // Extract roles
        Object rolesObj = claims.get("roles");
        List<String> permissionNames = rolesObj instanceof List<?> roles
                ? roles.stream()
                .map(Object::toString)
                .toList()
                : List.of();

        System.out.println("Roles: " + permissionNames);

        Collection<? extends GrantedAuthority> authorities =
                permissionNames.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        UserDetails userDetails;
        boolean tenantDbContextSet = false;

        // Extract additional claims
        String tenantIdStr = claims.get("tenantId", String.class);
        Long employeeId = null;
        Object employeeIdObj = claims.get("employeeId");
        if (employeeIdObj instanceof Integer) {
            employeeId = ((Integer) employeeIdObj).longValue();
        } else if (employeeIdObj instanceof Long) {
            employeeId = (Long) employeeIdObj;
        }

        String employeeCode = claims.get("employeeCode", String.class);

        try {
            if ("TENANT".equals(userType)) {
                System.out.println("Processing TENANT user");

                if (tenantIdStr == null) {
                    System.out.println("ERROR: Tenant ID missing");
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Tenant ID missing");
                    return;
                }

                UUID tenantId;
                try {
                    tenantId = UUID.fromString(tenantIdStr);
                } catch (Exception e) {
                    System.out.println("ERROR: Invalid tenant ID format: " + tenantIdStr);
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant ID");
                    return;
                }

                System.out.println("Setting tenant context: " + tenantId);
                TenantContext.setCurrentTenant(tenantId);
                tenantRLSService.setCurrentTenantInDB(tenantId);
                tenantDbContextSet = true;

                userDetails = tenantUserDetailsService.loadUserByUsername(username);
                System.out.println("Tenant user loaded: " + userDetails.getUsername());

            } else if ("PLATFORM".equals(userType)) {
                System.out.println("Processing PLATFORM user");
                TenantContext.clear();

                userDetails = platformUserDetailsService.loadUserByUsername(username);
                System.out.println("Platform user loaded: " + userDetails.getUsername());

            } else if ("EMPLOYEE".equals(userType)) {
                System.out.println("Processing EMPLOYEE user");
                TenantContext.clear();

                // Create UserDetails for employee
                userDetails = org.springframework.security.core.userdetails.User
                        .withUsername(username)
                        .password("")
                        .authorities(new ArrayList<>())
                        .build();
                System.out.println("Employee user loaded: " + userDetails.getUsername());

            } else {
                System.out.println("ERROR: Unknown user type: " + userType);
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unknown user type");
                return;
            }

            // =====================================================
            // STORE ADDITIONAL DETAILS IN AUTHENTICATION
            // =====================================================
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

            // Create a map to store additional details
            Map<String, Object> details = new HashMap<>();
            details.put("userType", userType);
            details.put("roles", permissionNames);
            details.put("tenantId", tenantIdStr);
            details.put("employeeId", employeeId);
            details.put("employeeCode", employeeCode);

            authToken.setDetails(details);
            SecurityContextHolder.getContext().setAuthentication(authToken);

            System.out.println("Authentication set with details - UserType: " + userType +
                    ", Roles: " + permissionNames +
                    ", EmployeeId: " + employeeId);
            System.out.println("Proceeding to controller...");
            filterChain.doFilter(request, response);

        } finally {
            System.out.println("\n========== CLEANUP ==========");
            TenantContext.clear();
            System.out.println("TenantContext cleared");

            if (tenantDbContextSet) {
                try {
                    tenantRLSService.clearCurrentTenantInDB();
                    System.out.println("Database tenant context cleared");
                } catch (Exception e) {
                    System.out.println("Failed to clear DB tenant context: " + e.getMessage());
                }
            }
            System.out.println("========== JWT FILTER END ==========\n");
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
        System.out.println("SENDING ERROR: " + status + " - " + message);
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