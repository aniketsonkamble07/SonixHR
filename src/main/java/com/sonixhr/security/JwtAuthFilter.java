package com.sonixhr.security;

import com.sonixhr.service.employee.EmployeeDetailsService;
import com.sonixhr.service.platform.PlatformUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRLSService tenantRLSService;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final EmployeeDetailsService employeeDetailsService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${app.jwt.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.jwt.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    // Cache for public path matching (performance optimization)
    private final Map<String, Boolean> publicPathCache = new ConcurrentHashMap<>();

    // Cache for user details (L1 cache)
    private final Map<String, UserDetails> userDetailsCache = new ConcurrentHashMap<>();

    // Cache for authentication tokens
    private final Map<String, UsernamePasswordAuthenticationToken> authCache = new ConcurrentHashMap<>();

    // Pre-compiled public path patterns for faster matching
    private static final Set<String> EXACT_PUBLIC_PATHS = Set.of(
            "/api/health",
            "/actuator/health",
            "/api/tenants/register",
            "/error"
    );

    private static final List<PathPattern> WILDCARD_PATTERNS = List.of(
            new PathPattern("/api/auth/", true),
            new PathPattern("/api/platform/auth/", true),
            new PathPattern("/api/tenant/auth/", true),
            new PathPattern("/api/public/", true),
            new PathPattern("/swagger-ui/", true),
            new PathPattern("/v3/api-docs/", true),
            new PathPattern("/api-docs/", true),
            new PathPattern("/api/debug/", true),
            new PathPattern("/api/tenants/verify-subdomain/", true),
            new PathPattern("/api/forgot-password/", true),
            new PathPattern("/api/reset-password/", true)
    );

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

    private record PathPattern(String prefix, boolean isWildcard) {}

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

        long startTime = System.nanoTime();
        String path = request.getRequestURI();
        String method = request.getMethod();

        try {
            // Fast path - check public paths using optimized method
            if (isPublicPathOptimized(path)) {
                if (log.isDebugEnabled()) {
                    log.debug("Public path accessed: {} {}", method, path);
                }
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            // Check cache for existing authentication
            if (cacheEnabled) {
                UsernamePasswordAuthenticationToken cachedAuth = authCache.get(token);
                if (cachedAuth != null && cachedAuth.isAuthenticated()) {
                    SecurityContextHolder.getContext().setAuthentication(cachedAuth);
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            // Validate token
            if (!jwtService.validateToken(token)) {
                if (log.isDebugEnabled()) {
                    log.debug("Invalid token for request: {} {}", method, path);
                }
                filterChain.doFilter(request, response);
                return;
            }

            // Check if already authenticated
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            // Extract claims once
            Claims claims = jwtService.extractAllClaims(token);
            String username = claims.getSubject();
            String userType = claims.get("userType", String.class);
            Integer tokenRolesVersion = jwtService.extractRolesVersion(token);

            if (username == null || userType == null) {
                log.warn("Token missing required claims for request: {} {}", method, path);
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails userDetails = null;
            boolean tenantContextSet = false;
            String cacheKey = userType + ":" + username;

            try {
                // Check user cache
                if (cacheEnabled) {
                    userDetails = userDetailsCache.get(cacheKey);
                }

                if ("PLATFORM".equals(userType)) {
                    if (userDetails == null) {
                        userDetails = platformUserDetailsService.loadUserByUsername(username);
                        if (cacheEnabled && userDetails != null) {
                            userDetailsCache.put(cacheKey, userDetails);
                        }
                    }
                    TenantContext.clear();

                } else if ("EMPLOYEE".equals(userType)) {
                    String tenantIdStr = claims.get("tenantId", String.class);
                    if (tenantIdStr == null) {
                        log.warn("Employee token missing tenantId for user: {}", username);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    Long tenantId = Long.parseLong(tenantIdStr);
                    TenantContext.setCurrentTenant(tenantId);
                    tenantRLSService.setCurrentTenantInDB(tenantId);
                    tenantContextSet = true;

                    if (userDetails == null) {
                        userDetails = employeeDetailsService.loadUserByUsername(username);
                        if (cacheEnabled && userDetails != null) {
                            userDetailsCache.put(cacheKey, userDetails);
                        }
                    }

                    // Check roles version - if changed, reload user
                    if (userDetails != null && needRolesReload(userDetails, tokenRolesVersion)) {
                        userDetails = reloadUserDetails(username, userType, tenantId);
                        if (cacheEnabled) {
                            userDetailsCache.put(cacheKey, userDetails);
                        }
                    }
                }

                if (userDetails != null) {
                    // Get authorities from token or user details (prioritize token for performance)
                    Collection<? extends GrantedAuthority> authorities = extractAuthorities(claims, userDetails);

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

                    // Store additional info in details
                    Map<String, Object> detailsMap = new HashMap<>();
                    detailsMap.put("userType", userType);
                    detailsMap.put("tenantId", claims.get("tenantId"));
                    detailsMap.put("employeeId", claims.get("employeeId"));
                    detailsMap.put("rolesVersion", tokenRolesVersion);
                    authToken.setDetails(detailsMap);

                    // Set authentication in context
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // Cache the authentication
                    if (cacheEnabled) {
                        authCache.put(token, authToken);
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("Authenticated {} user: {} with {} authorities",
                                userType, username, authorities.size());
                    }
                }
            } catch (Exception e) {
                log.error("Authentication error for user: {} - {}", username, e.getMessage());
                SecurityContextHolder.clearContext();
            }

            filterChain.doFilter(request, response);

            // Log performance for slow requests
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            if (duration > 100) {
                log.warn("Slow request: {} {} took {}ms, user: {}", method, path, duration, username);
            }

        } catch (Exception e) {
            log.error("Unexpected error in JWT filter for {} {}: {}", method, path, e.getMessage());
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
        } finally {
            // Clean up tenant context after request
            cleanupTenantContext();
        }
    }

    /**
     * Optimized public path checking with caching
     */
    private boolean isPublicPathOptimized(String path) {
        // Check exact match first (fastest)
        if (EXACT_PUBLIC_PATHS.contains(path)) {
            return true;
        }

        // Check cache
        Boolean cached = publicPathCache.get(path);
        if (cached != null) {
            return cached;
        }

        // Check wildcard patterns (optimized)
        for (PathPattern pattern : WILDCARD_PATTERNS) {
            if (path.startsWith(pattern.prefix)) {
                publicPathCache.put(path, true);
                return true;
            }
        }

        // Fallback to AntPathMatcher for complex patterns
        boolean isPublic = PUBLIC_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        // Cache the result
        if (publicPathCache.size() < 500) { // Limit cache size
            publicPathCache.put(path, isPublic);
        }

        return isPublic;
    }

    /**
     * Check if roles need to be reloaded based on version
     */
    private boolean needRolesReload(UserDetails userDetails, Integer tokenRolesVersion) {
        if (tokenRolesVersion == null) return false;

        if (userDetails instanceof com.sonixhr.entity.platform.PlatformUser) {
            com.sonixhr.entity.platform.PlatformUser user = (com.sonixhr.entity.platform.PlatformUser) userDetails;
            return !tokenRolesVersion.equals(user.getRolesVersion());
        } else if (userDetails instanceof com.sonixhr.entity.employee.Employee) {
            com.sonixhr.entity.employee.Employee employee = (com.sonixhr.entity.employee.Employee) userDetails;
            return !tokenRolesVersion.equals(employee.getRolesVersion());
        }
        return false;
    }

    /**
     * Reload user details with fresh roles
     */
    private UserDetails reloadUserDetails(String username, String userType, Long tenantId) {
        if ("PLATFORM".equals(userType)) {
            return platformUserDetailsService.loadUserByUsername(username);
        } else if ("EMPLOYEE".equals(userType)) {
            return employeeDetailsService.loadUserByUsernameWithFreshRoles(username);
        }
        return null;
    }

    /**
     * Extract authorities - prioritize token claims for performance
     */
    private Collection<? extends GrantedAuthority> extractAuthorities(Claims claims, UserDetails userDetails) {
        // Always load authorities from UserDetails (which loads from database)
        // This ensures role changes take effect immediately
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();

        if (log.isDebugEnabled()) {
            log.debug("Loaded {} authorities from database for user: {}",
                    authorities.size(), userDetails.getUsername());
        }

        return authorities;
    }


    /**
     * Clean up tenant context
     */
    private void cleanupTenantContext() {
        if (TenantContext.getCurrentTenant() != null) {
            TenantContext.clear();
            try {
                tenantRLSService.clearCurrentTenantInDB();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Error clearing tenant context: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Invalidate user cache (call when roles change)
     */
    public void invalidateUserCache(String username, String userType) {
        String cacheKey = userType + ":" + username;
        userDetailsCache.remove(cacheKey);

        // Also clear auth cache entries for this user
        authCache.entrySet().removeIf(entry ->
                entry.getValue() != null &&
                        entry.getValue().getPrincipal() instanceof UserDetails &&
                        ((UserDetails) entry.getValue().getPrincipal()).getUsername().equals(username)
        );

        log.debug("Invalidated cache for user: {} ({})", username, userType);
    }

    /**
     * Clear all caches (for testing or admin operations)
     */
    public void clearAllCaches() {
        userDetailsCache.clear();
        authCache.clear();
        publicPathCache.clear();
        log.info("Cleared all JWT filter caches");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Integer> getCacheStats() {
        return Map.of(
                "userDetailsCacheSize", userDetailsCache.size(),
                "authCacheSize", authCache.size(),
                "publicPathCacheSize", publicPathCache.size()
        );
    }

    /**
     * Original method kept for compatibility
     */
    private boolean isPublicPath(String path) {
        return isPublicPathOptimized(path);
    }
}