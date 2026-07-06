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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
 
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Slf4j
@Component
@SuppressWarnings("null")
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TenantRLSService tenantRLSService;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final EmployeeDetailsService employeeDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.jwt.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${app.jwt.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    // Cache for public path matching (performance optimization)
    private final Map<String, Boolean> publicPathCache = new ConcurrentHashMap<>();

    // Cache for user details (L1 cache)
    private Cache<String, UserDetails> userDetailsCache;

    // Cache for authentication tokens
    private Cache<String, UsernamePasswordAuthenticationToken> authCache;

    public JwtAuthFilter(
            JwtService jwtService,
            @Lazy TenantRLSService tenantRLSService,
            PlatformUserDetailsService platformUserDetailsService,
            EmployeeDetailsService employeeDetailsService,
            TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.tenantRLSService = tenantRLSService;
        this.platformUserDetailsService = platformUserDetailsService;
        this.employeeDetailsService = employeeDetailsService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @jakarta.annotation.PostConstruct
    private void initCaches() {
        authCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();

        userDetailsCache = Caffeine.newBuilder()
                .expireAfterAccess(cacheTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();
    }

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain)
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
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
            // Note: token from URL query params (?token=...) is intentionally NOT supported.
            // URL tokens are logged by servers, proxies, and stored in browser history.

            if (token == null || token.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            // Check if token is blacklisted first
            if (jwtService.isTokenBlacklisted(token)) {
                if (cacheEnabled) {
                    authCache.invalidate(token);
                }
                if (log.isDebugEnabled()) {
                    log.debug("Blacklisted token accessed: {} {}", method, path);
                }
                filterChain.doFilter(request, response);
                return;
            }

            // Check cache for existing authentication
            if (cacheEnabled) {
                UsernamePasswordAuthenticationToken cachedAuth = authCache.getIfPresent(token);
                if (cachedAuth != null && cachedAuth.isAuthenticated()) {
                    if (jwtService.isTokenExpired(token)) {
                        authCache.invalidate(token);
                    } else {
                        SecurityContextHolder.getContext().setAuthentication(cachedAuth);
                        filterChain.doFilter(request, response);
                        return;
                    }
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
            String cacheKey;
            if ("EMPLOYEE".equals(userType)) {
                String tenantIdStr = claims.get("tenantId", String.class);
                cacheKey = userType + ":" + (tenantIdStr != null ? tenantIdStr : "no-tenant") + ":" + username;
            } else {
                cacheKey = userType + ":" + username;
            }

            try {
                // Check user cache
                if (cacheEnabled) {
                    userDetails = userDetailsCache.getIfPresent(cacheKey);
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
                    if (tokenBlacklistService.isTenantBlacklisted(tenantId)) {
                        log.warn("Employee token accessed for suspended tenant: {}", tenantId);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    TenantContext.setCurrentTenant(tenantId);
                    tenantRLSService.setCurrentTenantInDB(tenantId);

                    if (userDetails == null) {
                        userDetails = employeeDetailsService.loadUserByUsername(username);
                        if (cacheEnabled && userDetails != null) {
                            userDetailsCache.put(cacheKey, userDetails);
                        }
                    }
                }

                // Check roles version for ALL users (Platform & Employee) - if changed, reload user
                if (userDetails != null && needRolesReload(userDetails, tokenRolesVersion)) {
                    Long tenantId = "EMPLOYEE".equals(userType) ? Long.parseLong(claims.get("tenantId", String.class)) : null;
                    userDetails = reloadUserDetails(username, userType, tenantId);
                    if (cacheEnabled && userDetails != null) {
                        userDetailsCache.put(cacheKey, userDetails);
                    }
                }

                if (userDetails != null) {
                    // Get authorities from token or user details (prioritize token for performance)
                    Collection<? extends GrantedAuthority> authorities = extractAuthorities(claims, userDetails);

                    // Create authentication token
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails,
                            null, authorities);

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
        // Exclude authenticated tenant and platform auth endpoints
        if ("/api/tenant/auth/me".equals(path) || 
            "/api/tenant/auth/logout".equals(path) || 
            "/api/tenant/auth/test-auth".equals(path) ||
            "/api/platform/auth/logout".equals(path)) {
            return false;
        }

        // Check exact match first (fastest)
        if (PublicPaths.EXACT.contains(path)) {
            return true;
        }

        // Check cache
        Boolean cached = publicPathCache.get(path);
        if (cached != null) {
            return cached;
        }

        // Check prefix patterns via shared PublicPaths constant
        for (String prefix : PublicPaths.PREFIXES) {
            if (path.startsWith(prefix)) {
                publicPathCache.put(path, true);
                return true;
            }
        }

        // Cache negative result (limit cache size)
        if (publicPathCache.size() < 500) {
            publicPathCache.put(path, false);
        }

        return false;
    }

    /**
     * Check if roles need to be reloaded based on version
     */
    private boolean needRolesReload(UserDetails userDetails, Integer tokenRolesVersion) {
        if (tokenRolesVersion == null)
            return false;

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
        String suffix = ":" + username;
        userDetailsCache.asMap().keySet().removeIf(key -> key.endsWith(suffix));

        // Also clear auth cache entries for this user
        authCache.asMap().entrySet().removeIf(entry -> {
            if (entry.getValue() != null && entry.getValue().getPrincipal() instanceof UserDetails) {
                UserDetails ud = (UserDetails) entry.getValue().getPrincipal();
                return username.equalsIgnoreCase(ud.getUsername());
            }
            return false;
        });

        log.debug("Invalidated cache for user: {} ({})", username, userType);
    }

    /**
     * Clear all caches (for testing or admin operations)
     */
    public void clearAllCaches() {
        userDetailsCache.invalidateAll();
        authCache.invalidateAll();
        publicPathCache.clear();
        log.info("Cleared all JWT filter caches");
    }

    /**
     * Get cache statistics
     */
    public Map<String, Integer> getCacheStats() {
        return Map.of(
                "userDetailsCacheSize", (int) userDetailsCache.estimatedSize(),
                "authCacheSize", (int) authCache.estimatedSize(),
                "publicPathCacheSize", publicPathCache.size());
    }
}